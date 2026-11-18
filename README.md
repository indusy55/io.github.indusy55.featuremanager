# Feature Manager

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![libxposed API 102](https://img.shields.io/badge/libxposed-API%20102-blue.svg)](https://github.com/JingMatrix/libxposed-api)

基于 [libxposed API 102](https://github.com/JingMatrix/libxposed-api) 的 Android Xposed 模块：**按应用覆写系统特性（system features）视图**，用于强制启用/屏蔽 `PackageManager` 的 feature，从而切换应用变体（PC / TV / 平板 / 手机 / 车机等）。

```bash
git clone git@github.com:indusy55/io.github.indusy55.featuremanager.git
```

## 它能做什么

- **首页**：网格展示已配置规则的应用，显示规则数量与「已生效」状态；点击图标进入该应用的规则编辑器。
- **应用**：已安装应用列表，支持搜索与过滤；显示 Xposed 作用域 / 进程状态；可一键申请作用域。
- **模板**：一个模板就是一个规则集。内置 PC / TV / 平板 / 手机 / 车机变体模板，可新建/复制/编辑自定义模板，**应用模板 = patch**（只覆盖模板声明的特性）。
- **设置**：外观配置（主题、动态颜色、强调色、纯黑深色、网格密度、紧凑卡片）与框架状态诊断。
- 支持配置导入/导出，便于备份和分享。

## Feature 规则语义

这里的 feature 指 `context.packageManager.getSystemAvailableFeatures()` / `hasSystemFeature()` 看到的系统特性。

| 规则 | `hasSystemFeature(name)` / `(name, version)` | `getSystemAvailableFeatures()` |
| --- | --- | --- |
| 默认 | 调用原方法 | 保留原数组项 |
| 强制开启 | 直接返回 `true` | 若原数组不存在该项，补入 `FeatureInfo(name, version=0, flags=0)` |
| 强制关闭 | 直接返回 `false` | 从原数组中移除该项 |

模板应用采用 **patch 语义**：只覆写模板中显式声明的 feature，目标应用原有的其他规则保持不变；勾选「替换现有规则」才会全量替换。

## 快速开始

1. 构建并安装 APK（见下方「构建」）。
2. 在 Xposed 管理器（如 LSPosed）中启用本模块，并将目标应用加入作用域。`module.prop` 中 `staticScope=false`，也可以在应用的「应用」页点击「申请作用域」。`scope.list` 的基线只包含模块自身。
3. 打开本模块：
   - 逐项配置：「应用」→ 选择目标应用 → 设置每个 feature 的三态规则；
   - 或批量配置：「模板」→ 选择 PC/TV 等模板 → 「应用到应用」→ 多选目标应用。
4. **强制停止并重新打开目标应用**。目标进程重新加载规则后，模块会把配置生效状态回报给设置页，首页出现「已生效 vN」。
5. 常见示例：让 Google Play 下发 Chrome Canary 的 **Desktop** 变体，需要应用完整 PC 模板，其中包含 `com.google.desktop.gms`（Desktop APK 的必需 feature），仅 spoof `android.hardware.type.pc` 不够。Play 还会结合服务端设备认证与缓存画像，必要时清除 Play 数据或卸载后重新下载目标应用。

## 内置模板

| 模板 | 主要行为 |
| --- | --- |
| PC 桌面端 (Desktop / ARC) | 开启 `android.hardware.type.pc`、`org.chromium.arc`、`org.chromium.arc.device_management`、`com.google.desktop.gms`、自由窗口与横屏；关闭 TV / 手表 / 车机 / 电话标识 |
| TV 电视端 (Leanback) | 开启 `android.software.leanback`、`android.hardware.type.television`、横屏与以太网；关闭触摸屏与电话标识 |
| 平板 / 折叠屏 | 开启多点触控、自由窗口与横竖屏；关闭 PC / TV / 电话标识 |
| 标准手机 | 开启触摸屏与电话；关闭 PC / TV / 车机标识 |
| 车载 | 开启 `android.hardware.type.automotive`、横屏与 GPS；关闭 PC / TV 标识 |

也可以在规则编辑器中手动添加任意 feature 名称（包括应用私有的 feature 字符串）。

## 工作原理

Hook 点位于目标进程的 `android.app.ApplicationPackageManager`：

- `hasSystemFeature(String)`
- `hasSystemFeature(String, int)`
- `getSystemAvailableFeatures()`

配置**不通过** `getRemotePreferences` 读取。模块通过：

```kotlin
context.contentResolver.call(
    "io.github.indusy55.featuremanager.config", // authority
    "resolve",
    targetPackageName,
    null
)
```

向设置页的 `ConfigProvider` 请求 `add` / `remove` 两个字符串数组（另带 `version` 与 `configured`）。拿到配置后，模块发出 `io.github.indusy55.featuremanager.CONFIG_LOADED` 显式广播，设置页据此显示「已生效」。模块侧有短 TTL 缓存，避免每次 feature 查询都跨进程调用。

作用域状态通过 `io.github.libxposed:service:102.0.0` 获取：`getScope()` 判断是否在作用域，`getRunningTargets()` 判断目标进程是否已被 hook，并支持 `requestScope()` / `removeScope()`。

关键源码：

- `xposed/FeatureManagerModule.kt` — Xposed 入口与 hook
- `xposed/ConfigClient.kt` — 跨进程配置读取、缓存与 LOADED 广播
- `service/ConfigProvider.kt`、`service/LoadedReceiver.kt` — 配置提供者与状态接收
- `data/` — 规则、模板、应用列表与持久化
- `ui/` — Compose 页面与组件

## 构建

需要 JDK 17+、Android SDK Platform 37。Gradle Wrapper 会自动下载 Gradle 9.4.1，AGP 版本为 9.2.1。

```bash
./gradlew :app:assembleDebug
# Debug APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:testDebugUnitTest
```

模块元数据位于 `app/src/main/resources/META-INF/xposed/`：

- `java_init.list` → `io.github.indusy55.featuremanager.xposed.FeatureManagerModule`
- `module.prop` → `minApiVersion=102`、`targetApiVersion=102`、`staticScope=false`、`autoHotReload=true`
- `scope.list` → 模块自身包名（其他目标应用通过作用域管理动态加入）

## 隐私

模块不请求网络权限，不在应用内上传任何配置。规则、模板与外观设置保存在应用私有 `SharedPreferences` 中。导出的配置为本地 JSON，导入/导出由用户主动触发。

## 免责声明

本项目依赖 Xposed 框架（通常需要 root 或相应定制环境）。修改应用对系统特性的感知可能导致应用异常、账号风险或违反第三方服务条款。请在自己的设备与测试环境中自行评估风险，作者不承担任何责任。

## 开源协议

[MIT](LICENSE) © 2026 indusy55
