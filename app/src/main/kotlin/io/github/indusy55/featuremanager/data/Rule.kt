package io.github.indusy55.featuremanager.data

import android.content.pm.PackageManager
import io.github.indusy55.featuremanager.core.FeatureCatalog

/**
 * Tri-state feature override rule for an app or template:
 * - [DEFAULT]: Delegate to original ApplicationPackageManager
 * - [FORCE_TRUE]: Force hasSystemFeature -> true, and synthesize FeatureInfo(name, 0, 0) in getSystemAvailableFeatures
 * - [FORCE_FALSE]: Force hasSystemFeature -> false, and strip from getSystemAvailableFeatures
 */
enum class Decision {
    DEFAULT,
    FORCE_TRUE,
    FORCE_FALSE;

    val isOverride: Boolean
        get() = this != DEFAULT
}

data class AppFeatureConfig(
    val packageName: String,
    val rules: Map<String, Decision> = emptyMap(),
    val appliedTemplates: List<String> = emptyList(),
    val updatedAt: Long = 0L
) {
    val activeRules: Map<String, Decision>
        get() = rules.filterValues { it != Decision.DEFAULT }

    val addedFeatures: List<String>
        get() = rules.asSequence()
            .filter { it.value == Decision.FORCE_TRUE }
            .map { it.key }
            .sorted()
            .toList()

    val removedFeatures: List<String>
        get() = rules.asSequence()
            .filter { it.value == Decision.FORCE_FALSE }
            .map { it.key }
            .sorted()
            .toList()

    val hasActiveRules: Boolean
        get() = rules.values.any { it != Decision.DEFAULT }

    val enabledCount: Int
        get() = rules.values.count { it == Decision.FORCE_TRUE }

    val disabledCount: Int
        get() = rules.values.count { it == Decision.FORCE_FALSE }
}

data class FeatureTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val rules: Map<String, Decision>,
    val builtIn: Boolean = false,
    val badge: String = ""
) {
    val enabledCount: Int
        get() = rules.values.count { it == Decision.FORCE_TRUE }

    val disabledCount: Int
        get() = rules.values.count { it == Decision.FORCE_FALSE }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

enum class AccentStyle(val label: String) {
    INDIGO("靛青 (默认)"),
    TEAL("青碧"),
    AMBER("琥珀"),
    CRIMSON("绯红"),
    VIOLET("紫罗兰")
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlackDark: Boolean = false,
    val accentStyle: AccentStyle = AccentStyle.INDIGO,
    val homeGridColumns: Int = 4,
    val compactCards: Boolean = false,
    val showSystemAppsByDefault: Boolean = false,
    val autoRequestScopeOnSave: Boolean = true
)

data class LoadedProcessState(
    val packageName: String,
    val processName: String = packageName,
    val configVersion: Int = 0,
    val timestamp: Long = 0L,
    val addedCount: Int = 0,
    val removedCount: Int = 0
)

data class PersistedAppState(
    val revision: Int = 1,
    val appConfigs: Map<String, AppFeatureConfig> = emptyMap(),
    val customTemplates: List<FeatureTemplate> = emptyList(),
    val settings: AppearanceSettings = AppearanceSettings(),
    val loadedStates: Map<String, LoadedProcessState> = emptyMap()
) {
    val allTemplates: List<FeatureTemplate>
        get() = BuiltinTemplates.ALL + customTemplates
}

object BuiltinTemplates {
    val VARIANT_PC = FeatureTemplate(
        id = "builtin_variant_pc",
        name = "PC 桌面端变体 (Desktop / ARC)",
        subtitle = "Variant -> PC",
        badge = "常用推荐",
        description = "开启 PC 形态、ChromeOS ARC 容器与自由多窗口特性，同时移除 TV / 手表 / 车机与蜂窝电话标识，让应用切换至桌面大屏多栏交互模式。",
        builtIn = true,
        rules = linkedMapOf(
            PackageManager.FEATURE_PC to Decision.FORCE_TRUE,
            FeatureCatalog.FEATURE_CHROME_ARC to Decision.FORCE_TRUE,
            FeatureCatalog.FEATURE_CHROME_ARC_DEVICE_MGMT to Decision.FORCE_TRUE,
            FeatureCatalog.FEATURE_DESKTOP_GMS to Decision.FORCE_TRUE,
            PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT to Decision.FORCE_TRUE,
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS to Decision.FORCE_TRUE,
            PackageManager.FEATURE_FAKETOUCH to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_LANDSCAPE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_USB_HOST to Decision.FORCE_TRUE,
            PackageManager.FEATURE_ETHERNET to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TELEVISION to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK_ONLY to Decision.FORCE_FALSE,
            PackageManager.FEATURE_WATCH to Decision.FORCE_FALSE,
            PackageManager.FEATURE_AUTOMOTIVE to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TELEPHONY to Decision.FORCE_FALSE
        )
    )

    val VARIANT_TV = FeatureTemplate(
        id = "builtin_variant_tv",
        name = "TV 电视端变体 (Leanback / Television)",
        subtitle = "Variant -> TV",
        badge = "常用推荐",
        description = "注入 Android TV Leanback 与 Television 特性，屏蔽触摸屏与蜂窝电话特性，使音视频及综合应用进入大屏电视端/遥控器界面。",
        builtIn = true,
        rules = linkedMapOf(
            PackageManager.FEATURE_LEANBACK to Decision.FORCE_TRUE,
            PackageManager.FEATURE_LEANBACK_ONLY to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TELEVISION to Decision.FORCE_TRUE,
            PackageManager.FEATURE_LIVE_TV to Decision.FORCE_TRUE,
            PackageManager.FEATURE_FAKETOUCH to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_LANDSCAPE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_ETHERNET to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TOUCHSCREEN to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND to Decision.FORCE_FALSE,
            PackageManager.FEATURE_PC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC_DEVICE_MGMT to Decision.FORCE_FALSE,
            PackageManager.FEATURE_WATCH to Decision.FORCE_FALSE,
            PackageManager.FEATURE_AUTOMOTIVE to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TELEPHONY to Decision.FORCE_FALSE
        )
    )

    val VARIANT_TABLET = FeatureTemplate(
        id = "builtin_variant_tablet",
        name = "平板/折叠屏多窗变体 (Tablet)",
        subtitle = "Variant -> Tablet",
        badge = "大屏优化",
        description = "保留完整多点触控并开启自由窗口与横屏支持，同时屏蔽蜂窝通信、TV 与 PC 独占容器标识，适用于平板横屏分栏适配。",
        builtIn = true,
        rules = linkedMapOf(
            PackageManager.FEATURE_TOUCHSCREEN to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH to Decision.FORCE_TRUE,
            PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT to Decision.FORCE_TRUE,
            PackageManager.FEATURE_PICTURE_IN_PICTURE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_LANDSCAPE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_PORTRAIT to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TELEPHONY to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TELEVISION to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK_ONLY to Decision.FORCE_FALSE,
            PackageManager.FEATURE_PC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC to Decision.FORCE_FALSE,
            PackageManager.FEATURE_WATCH to Decision.FORCE_FALSE,
            PackageManager.FEATURE_AUTOMOTIVE to Decision.FORCE_FALSE
        )
    )

    val VARIANT_PHONE = FeatureTemplate(
        id = "builtin_variant_phone",
        name = "标准手机变体 (Mobile Phone)",
        subtitle = "Variant -> Phone",
        badge = "还原手机",
        description = "在平板、车机或电视盒上伪装为标准触控手机，关闭 PC / TV / 车载特性并启用触摸屏与电话特性。",
        builtIn = true,
        rules = linkedMapOf(
            PackageManager.FEATURE_TOUCHSCREEN to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TELEPHONY to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_PORTRAIT to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_LANDSCAPE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_PC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC_DEVICE_MGMT to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TELEVISION to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK_ONLY to Decision.FORCE_FALSE,
            PackageManager.FEATURE_AUTOMOTIVE to Decision.FORCE_FALSE,
            PackageManager.FEATURE_WATCH to Decision.FORCE_FALSE
        )
    )

    val VARIANT_AUTOMOTIVE = FeatureTemplate(
        id = "builtin_variant_automotive",
        name = "车载信息娱乐变体 (Automotive)",
        subtitle = "Variant -> Car",
        badge = "车机模式",
        description = "开启 Android Automotive 车载硬件形态与横屏GPS特性，用于测试或启用应用的车载大字号驾驶模式界面。",
        builtIn = true,
        rules = linkedMapOf(
            PackageManager.FEATURE_AUTOMOTIVE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_SCREEN_LANDSCAPE to Decision.FORCE_TRUE,
            PackageManager.FEATURE_LOCATION_GPS to Decision.FORCE_TRUE,
            PackageManager.FEATURE_TOUCHSCREEN to Decision.FORCE_TRUE,
            PackageManager.FEATURE_PC to Decision.FORCE_FALSE,
            FeatureCatalog.FEATURE_CHROME_ARC to Decision.FORCE_FALSE,
            PackageManager.FEATURE_TELEVISION to Decision.FORCE_FALSE,
            PackageManager.FEATURE_LEANBACK to Decision.FORCE_FALSE,
            PackageManager.FEATURE_WATCH to Decision.FORCE_FALSE
        )
    )

    val ALL: List<FeatureTemplate> = listOf(
        VARIANT_PC,
        VARIANT_TV,
        VARIANT_TABLET,
        VARIANT_PHONE,
        VARIANT_AUTOMOTIVE
    )
}
