# Feature Manager

一个基于 [libxposed API 102](https://github.com/JingMatrix/libxposed-api) 的 Android Xposed 模块，用于按应用覆写
`PackageManager.getSystemAvailableFeatures()` / `hasSystemFeature()` 的系统特性（features）。

最常见的用途是**应用变体切换（Variant Patch）**：把应用识别成 PC / TV / 平板 / 手机 / 车机，从而进入相应的
桌面大屏、Leanback 遥控器或移动端界面。内置模板以 **PC 桌面端** 和 **TV 电视端** 为核心。

## 功能

| 页面 | 说明 |
| --- | --- |
| 首页 | 网格展示已配置规则的应用；显示规则数量与「已生效」状态；点击卡片进入规则编辑器 |
| 应用 | 已安装应用列表，支持搜索与过滤；显示 Xposed 作用域/进程状态；可一键申请作用域 |
| 模板 | 规则集管理。内置 PC / TV / 平板 / 手机 / 车机变体模板；自定义模板 CRUD；**应用模板 = 以 patch 方式合并规则**；支持配置导入导出 |
| 设置 | 外观（深浅色、动态颜色、强调色、纯黑深色、网格列数、紧凑卡片）；Xposed 框架状态与作用域诊断 |

### Feature 规则语义（三态）

- **默认**：调用原始 `ApplicationPackageManager` 实现，不修改结果。
- **开启**：
  - `hasSystemFeature(name)` 与 `hasSystemFeature(name, version)` 直接返回 `true`；
  - `getSystemAvailableFeatures()` 中若该特性不存在，则补入 `FeatureInfo(name, version=0, flags=0)`。
- **关闭**：
  - 两个 `hasSystemFeature` 重载直接返回 `false`；
  - 从 `getSystemAvailableFeatures()` 返回的数组中移除该特性。

模板应用采用 **patch** 语义：只覆写模板中显式声明的特性，应用原有的其他特性规则保持不变（可勾选「替换现有规则」改为全量替换）。

## Xposed 模块配置

模块元数据位于 `app/src/main/resources/META-INF/xposed/`：

- `java_init.list` → `io.github.indusy55.featuremanager.xposed.FeatureManagerModule`
- `module.prop` → `minApiVersion=102`、`targetApiVersion=102`、`staticScope=false`、`autoHotReload=true`

Hook 点为 `android.app.ApplicationPackageManager` 的：

- `hasSystemFeature(String)`
- `hasSystemFeature(String, int)`
- `getSystemAvailableFeatures()`

模块只会在被加载进目标应用进程后安装 hook；找不到方法或拿不到 Context 时降级为原方法，不会导致目标应用崩溃。

## 配置读取与「已生效」状态

规则**不是**通过 `getRemotePreferences` 读取的。模块在目标进程内通过：

```kotlin
contentResolver.call(
    "io.github.indusy55.featuremanager.config",
    "resolve",
    targetPackageName,
    null
)
```

向设置页的 `ConfigProvider` 请求两个字符串数组：

- `add`：强制开启的 feature 名称
- `remove`：强制关闭的 feature 名称
- 附加 `version`（配置修订号）与 `configured` 标志，便于缓存与诊断

拿到配置后，模块会发出 `io.github.indusy55.featuremanager.CONFIG_LOADED` 显式广播（携带包名、进程名、
版本号、时间戳与增删数量），设置页的 `LoadedReceiver` 接收后显示「已生效」。配置变更时设置页发出
`CONFIG_CHANGED` 广播；模块侧还有 1.5 秒短 TTL 缓存，避免每次 `hasSystemFeature()` 都跨进程调用。

## 作用域（Scope）状态

应用通过 `io.github.libxposed:service:102.0.0` 连接 Xposed 框架服务：

- `XposedService.getScope()` 判断应用是否在模块作用域；
- `XposedService.getRunningTargets()`（服务 API ≥ 102）判断目标进程是否已被 hook；
- 支持 `requestScope()` / `removeScope()`；框架不可用或请求失败时显示中文诊断信息。

`scope.list` 的基线只包含模块自身包名；由于 `staticScope=false`，可在应用内通过 Xposed 服务
`requestScope()` 动态为其他应用加入作用域，或在 Xposed 管理器中勾选。部分框架要求重启目标应用
（或设备）后才能注入模块。

## 构建

需要 JDK 17+、Android SDK Platform 37 与 Android Gradle Plugin 9.2.1 / Gradle 9.4.1（Wrapper 自动下载）。

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 常见 Feature 名称

- PC / ChromeOS：`android.hardware.type.pc`、`org.chromium.arc`、`org.chromium.arc.device_management`、
  `android.software.freeform_window_management`
- TV / Leanback：`android.software.leanback`、`android.software.leanback_only`、
  `android.hardware.type.television`、`android.hardware.touchscreen`（通常关闭）
- 手机：`android.hardware.touchscreen`、`android.hardware.telephony`

也可以在规则编辑器里手动添加任意 feature 名称（例如应用私有 feature 字符串）。
