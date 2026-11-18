package io.github.indusy55.featuremanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.core.FeatureCatalog
import io.github.indusy55.featuremanager.core.FeatureCategory
import io.github.indusy55.featuremanager.core.FeatureSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val uid: Int
)

data class DeviceFeatureItem(
    val spec: FeatureSpec,
    val deviceAvailableByDefault: Boolean,
    val deviceVersion: Int = 0
)

class AppListRepository private constructor(private val appContext: Context) {

    private val pm: PackageManager = appContext.packageManager

    private val iconCache = object : LruCache<String, ImageBitmap>(96) {}

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _deviceFeatures = MutableStateFlow<Map<String, Int>>(emptyMap())
    val deviceFeatures: StateFlow<Map<String, Int>> = _deviceFeatures.asStateFlow()

    suspend fun ensureLoaded(forceRefresh: Boolean = false) {
        if (_deviceFeatures.value.isEmpty() || forceRefresh) {
            loadDeviceSystemFeatures()
        }
        if (_installedApps.value.isEmpty() || forceRefresh) {
            refreshInstalledApps()
        }
    }

    suspend fun loadDeviceSystemFeatures() = withContext(Dispatchers.IO) {
        val available = runCatching {
            pm.systemAvailableFeatures
                ?.mapNotNull { info ->
                    val name = info?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    name to info.version
                }
                ?.toMap()
                ?: emptyMap()
        }.getOrDefault(emptyMap())
        _deviceFeatures.value = available
    }

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        _isLoadingApps.value = true
        try {
            val apps = runCatching {
                val rawList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
                rawList.mapNotNull { pkgInfo ->
                    val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                    if (pkgInfo.packageName == ConfigContract.APP_ID) return@mapNotNull null
                    val label = runCatching {
                        appInfo.loadLabel(pm).toString().ifBlank { pkgInfo.packageName }
                    }.getOrDefault(pkgInfo.packageName)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                    InstalledAppInfo(
                        packageName = pkgInfo.packageName,
                        label = label,
                        versionName = pkgInfo.versionName ?: "",
                        versionCode = vCode,
                        isSystemApp = isSystem,
                        uid = appInfo.uid
                    )
                }.sortedWith(
                    compareBy<InstalledAppInfo> { it.isSystemApp }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                )
            }.getOrDefault(emptyList())
            _installedApps.value = apps
        } finally {
            _isLoadingApps.value = false
        }
    }

    fun getCachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun loadAppIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = 128, height = 128).asImageBitmap()
                iconCache.put(packageName, bitmap)
                bitmap
            }.getOrNull()
        }
    }

    fun resolveAppInfo(packageName: String): InstalledAppInfo {
        _installedApps.value.firstOrNull { it.packageName == packageName }?.let { return it }
        return runCatching {
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val appInfo = pkgInfo.applicationInfo
            val label = appInfo?.loadLabel(pm)?.toString()?.ifBlank { packageName } ?: packageName
            val isSystem = appInfo != null &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            InstalledAppInfo(
                packageName = packageName,
                label = label,
                versionName = pkgInfo.versionName ?: "",
                versionCode = vCode,
                isSystemApp = isSystem,
                uid = appInfo?.uid ?: 0
            )
        }.getOrElse {
            InstalledAppInfo(
                packageName = packageName,
                label = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                versionName = "未安装",
                versionCode = 0L,
                isSystemApp = false,
                uid = 0
            )
        }
    }

    /**
     * Merges curated catalog features, current device's native [PackageManager.getSystemAvailableFeatures],
     * and any extra custom feature keys present in [extraKeys].
     */
    fun buildCatalogWithDeviceState(extraKeys: Set<String> = emptySet()): List<DeviceFeatureItem> {
        val nativeMap = _deviceFeatures.value
        val result = ArrayList<DeviceFeatureItem>(FeatureCatalog.KNOWN_FEATURES.size + nativeMap.size)
        val seen = HashSet<String>()

        for (spec in FeatureCatalog.KNOWN_FEATURES) {
            seen.add(spec.name)
            result.add(
                DeviceFeatureItem(
                    spec = spec,
                    deviceAvailableByDefault = nativeMap.containsKey(spec.name),
                    deviceVersion = nativeMap[spec.name] ?: 0
                )
            )
        }

        for (extra in extraKeys.sorted()) {
            if (extra.isNotBlank() && seen.add(extra)) {
                result.add(
                    DeviceFeatureItem(
                        spec = FeatureSpec(
                            name = extra,
                            label = FeatureCatalog.displayTitle(extra),
                            category = FeatureCategory.CUSTOM,
                            description = "自定义或手动添加的 Feature 规则项"
                        ),
                        deviceAvailableByDefault = nativeMap.containsKey(extra),
                        deviceVersion = nativeMap[extra] ?: 0
                    )
                )
            }
        }

        for ((name, ver) in nativeMap.entries.sortedBy { it.key }) {
            if (seen.add(name)) {
                result.add(
                    DeviceFeatureItem(
                        spec = FeatureSpec(
                            name = name,
                            label = FeatureCatalog.displayTitle(name),
                            category = FeatureCategory.CUSTOM,
                            description = "当前设备系统原生支持的 Feature (version=$ver)"
                        ),
                        deviceAvailableByDefault = true,
                        deviceVersion = ver
                    )
                )
            }
        }

        return result
    }

    companion object {
        @Volatile
        private var instance: AppListRepository? = null

        fun get(context: Context): AppListRepository {
            return instance ?: synchronized(this) {
                instance ?: AppListRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
