package io.github.indusy55.featuremanager.core

import android.content.pm.PackageManager

/**
 * Shared IPC contract between the module UI process ([io.github.indusy55.featuremanager.service.ConfigProvider]),
 * the broadcast receiver ([io.github.indusy55.featuremanager.service.LoadedReceiver]),
 * and the Xposed hook running inside target processes ([io.github.indusy55.featuremanager.xposed.FeatureManagerModule]).
 */
object ConfigContract {
    const val APP_ID = "io.github.indusy55.featuremanager"
    const val AUTHORITY = "$APP_ID.config"
    const val URI_STRING = "content://$AUTHORITY"

    const val METHOD_RESOLVE = "resolve"
    const val METHOD_PING = "ping"

    const val KEY_ADD = "add"
    const val KEY_REMOVE = "remove"
    const val KEY_VERSION = "version"
    const val KEY_Configured = "configured"
    const val KEY_ERROR = "error"

    /** Broadcast sent by hooked target process to notify settings UI that rules took effect. */
    const val ACTION_CONFIG_LOADED = "$APP_ID.CONFIG_LOADED"

    /** Broadcast sent by settings UI when rules change. */
    const val ACTION_CONFIG_CHANGED = "$APP_ID.CONFIG_CHANGED"

    const val EXTRA_PACKAGE = "pkg"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_VERSION = "version"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_ADDED_COUNT = "added_count"
    const val EXTRA_REMOVED_COUNT = "removed_count"
    const val EXTRA_FINGERPRINT = "fingerprint"

    private val PACKAGE_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")

    fun isValidPackageName(pkg: String?): Boolean {
        if (pkg.isNullOrBlank() || pkg.length > 255) return false
        return PACKAGE_REGEX.matches(pkg)
    }
    /** Stable identity of the actual rule content, independent of revision bumps. */
    fun fingerprint(add: Collection<String>, remove: Collection<String>): String {
        return add.sorted().joinToString(",") + "|" + remove.sorted().joinToString(",")
    }
}


enum class FeatureCategory(val title: String) {
    FORM_FACTOR("形态与变体 (Variant)"),
    INPUT_DISPLAY("输入与窗口"),
    CONNECTIVITY("通信与连接"),
    MEDIA_CAMERA("多媒体与相机"),
    SENSORS_BIOMETRICS("传感器与生物识别"),
    SYSTEM_SOFTWARE("系统与软件特性"),
    CUSTOM("设备与自定义特性")
}

data class FeatureSpec(
    val name: String,
    val label: String,
    val category: FeatureCategory,
    val description: String = ""
)

object FeatureCatalog {
    // CommonChromeOS / ARC feature strings checked by apps for PC mode
    const val FEATURE_CHROME_ARC = "org.chromium.arc"
    const val FEATURE_CHROME_ARC_DEVICE_MGMT = "org.chromium.arc.device_management"
    const val FEATURE_GOOGLE_AT_HOME = "com.google.android.tv"
    const val FEATURE_DESKTOP_GMS = "com.google.desktop.gms"

    val KNOWN_FEATURES: List<FeatureSpec> = listOf(
        // Form factor / Variant (PC, TV, Watch, Automotive, Embedded)
        FeatureSpec(
            name = PackageManager.FEATURE_PC,
            label = "PC 桌面设备 (type.pc)",
            category = FeatureCategory.FORM_FACTOR,
            description = "标识设备为 PC / Chromebook 桌面形态，触发应用的桌面端平板/多栏布局"
        ),
        FeatureSpec(
            name = FEATURE_CHROME_ARC,
            label = "ChromeOS ARC 容器",
            category = FeatureCategory.FORM_FACTOR,
            description = "许多主流应用检测此特性以启用 PC 键鼠与大屏桌面变体"
        ),
        FeatureSpec(
            name = FEATURE_CHROME_ARC_DEVICE_MGMT,
            label = "ChromeOS ARC 设备管理",
            category = FeatureCategory.FORM_FACTOR,
            description = "ChromeOS 桌面环境辅助标识"
        ),
        FeatureSpec(
            name = FEATURE_DESKTOP_GMS,
            label = "Google Desktop GMS (ChromeOS PC)",
            category = FeatureCategory.FORM_FACTOR,
            description = "Chrome/Play 桌面版 (Desktop) 变体声明的必需特性；普通手机/平板不具备此项"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_LEANBACK,
            label = "Android TV (Leanback UI)",
            category = FeatureCategory.FORM_FACTOR,
            description = "标识设备支持电视端大屏遥控器交互 (Leanback)"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_LEANBACK_ONLY,
            label = "仅限 Leanback 电视模式",
            category = FeatureCategory.FORM_FACTOR,
            description = "强制设备仅运行电视端界面"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_TELEVISION,
            label = "电视设备形态 (type.television)",
            category = FeatureCategory.FORM_FACTOR,
            description = "传统电视硬件形态标识，部分视频/流媒体应用依赖此项切换 TV 变体"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_LIVE_TV,
            label = "Live TV 电视直播框架",
            category = FeatureCategory.FORM_FACTOR,
            description = "Android TV 频道与直播支持"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_AUTOMOTIVE,
            label = "车载形态 (type.automotive)",
            category = FeatureCategory.FORM_FACTOR,
            description = "Android Automotive 车机系统形态"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_WATCH,
            label = "智能手表 (type.watch)",
            category = FeatureCategory.FORM_FACTOR,
            description = "Wear OS 手表形态标识"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_EMBEDDED,
            label = "嵌入式设备 (type.embedded)",
            category = FeatureCategory.FORM_FACTOR,
            description = "IoT / 嵌入式硬件标识"
        ),

        // Input & Display / Window Management
        FeatureSpec(
            name = PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT,
            label = "自由窗口管理 (Freeform)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "允许应用以可缩放桌面多窗口模式运行"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_PICTURE_IN_PICTURE,
            label = "画中画 (Picture-in-Picture)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持视频悬浮小窗播放"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
            label = "副屏多任务显示",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "允许在外接显示器上启动 Activity"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_TOUCHSCREEN,
            label = "电容触摸屏 (touchscreen)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "TV 变体通常需要关闭此项以强制启用遥控器焦点导航"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH,
            label = "多点触控 (multitouch)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持双指缩放等多点触摸操作"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT,
            label = "独立多点触控",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持追踪两个以上独立触控点"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND,
            label = "五指以上多点触控 (jazzhand)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持 5 个以上独立触控点"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_FAKETOUCH,
            label = "模拟触控 (faketouch)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "鼠标/遥控器模拟点击兼容能力，PC 和 TV 通常开启"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_GAMEPAD,
            label = "游戏手柄支持 (gamepad)",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "硬件实体游戏控制器输入"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_SCREEN_LANDSCAPE,
            label = "横屏显示支持",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持横向屏幕方向"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_SCREEN_PORTRAIT,
            label = "竖屏显示支持",
            category = FeatureCategory.INPUT_DISPLAY,
            description = "支持纵向屏幕方向"
        ),

        // Connectivity
        FeatureSpec(
            name = PackageManager.FEATURE_TELEPHONY,
            label = "蜂窝移动通信 (telephony)",
            category = FeatureCategory.CONNECTIVITY,
            description = "语音通话与短信基带，平板/PC/TV 通常无此特性"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_WIFI,
            label = "Wi-Fi 无线网络",
            category = FeatureCategory.CONNECTIVITY,
            description = "802.11 无线局域网支持"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_ETHERNET,
            label = "有线以太网 (ethernet)",
            category = FeatureCategory.CONNECTIVITY,
            description = "PC 与电视盒子常用的有线网络接口"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_BLUETOOTH,
            label = "蓝牙 (Bluetooth)",
            category = FeatureCategory.CONNECTIVITY,
            description = "经典蓝牙连接支持"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_BLUETOOTH_LE,
            label = "低功耗蓝牙 (BLE)",
            category = FeatureCategory.CONNECTIVITY,
            description = "Bluetooth Low Energy 外设支持"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_NFC,
            label = "NFC 近场通信",
            category = FeatureCategory.CONNECTIVITY,
            description = "刷卡与标签读写能力"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_USB_HOST,
            label = "USB Host 主机模式",
            category = FeatureCategory.CONNECTIVITY,
            description = "外接键盘、鼠标、U盘等 USB 设备"
        ),

        // Media & Camera
        FeatureSpec(
            name = PackageManager.FEATURE_CAMERA_ANY,
            label = "任意相机支持 (camera.any)",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "至少拥有一个摄像头（含外接）"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_CAMERA,
            label = "后置主摄像头",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "拥有后置相机硬件"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_CAMERA_FRONT,
            label = "前置摄像头",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "拥有自拍/视频通话前置摄像头"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_MICROPHONE,
            label = "麦克风音频输入",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "支持录音与语音输入"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_AUDIO_OUTPUT,
            label = "音频输出",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "扬声器、耳机或 HDMI 音频输出通道"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_AUDIO_LOW_LATENCY,
            label = "低延迟音频",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "专业音频与音游低延迟输出通路"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_AUDIO_PRO,
            label = "专业级音频 (audio.pro)",
            category = FeatureCategory.MEDIA_CAMERA,
            description = "超低延迟与高采样率专业音频指标"
        ),

        // Sensors & Biometrics
        FeatureSpec(
            name = PackageManager.FEATURE_FINGERPRINT,
            label = "指纹识别",
            category = FeatureCategory.SENSORS_BIOMETRICS,
            description = "生物指纹解锁与支付验证硬件"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_FACE,
            label = "面部识别",
            category = FeatureCategory.SENSORS_BIOMETRICS,
            description = "生物人脸认证硬件"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_LOCATION_GPS,
            label = "GPS 卫星定位",
            category = FeatureCategory.SENSORS_BIOMETRICS,
            description = "高精度卫星导航硬件"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            label = "加速度传感器",
            category = FeatureCategory.SENSORS_BIOMETRICS,
            description = "重力感应与旋转检测"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_SENSOR_GYROSCOPE,
            label = "陀螺仪传感器",
            category = FeatureCategory.SENSORS_BIOMETRICS,
            description = "三轴角速度体感检测"
        ),

        // System & Software
        FeatureSpec(
            name = PackageManager.FEATURE_WEBVIEW,
            label = "系统 WebView",
            category = FeatureCategory.SYSTEM_SOFTWARE,
            description = "提供内嵌网页渲染引擎组件"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_BACKUP,
            label = "应用数据备份",
            category = FeatureCategory.SYSTEM_SOFTWARE,
            description = "支持系统备份与恢复机制"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_PRINTING,
            label = "系统打印服务",
            category = FeatureCategory.SYSTEM_SOFTWARE,
            description = "支持文档打印框架"
        ),
        FeatureSpec(
            name = PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL,
            label = "Vulkan 图形硬件加速",
            category = FeatureCategory.SYSTEM_SOFTWARE,
            description = "次世代 3D 渲染管线支持"
        )
    )

    private val BY_NAME: Map<String, FeatureSpec> = KNOWN_FEATURES.associateBy { it.name }

    fun findSpec(name: String): FeatureSpec? = BY_NAME[name]

    fun displayTitle(name: String): String = BY_NAME[name]?.label ?: simplifyFeatureName(name)

    fun simplifyFeatureName(name: String): String {
        return name
            .removePrefix("android.hardware.")
            .removePrefix("android.software.")
            .removePrefix("com.google.android.")
    }
}
