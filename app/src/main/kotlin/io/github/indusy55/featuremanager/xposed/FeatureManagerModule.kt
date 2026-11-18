package io.github.indusy55.featuremanager.xposed

import android.content.pm.FeatureInfo
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * libxposed API 102 entry point. Hooks the target app's [android.app.ApplicationPackageManager]
 * so feature queries are answered from the rule set configured in the settings UI.
 *
 * Runtime contract:
 * - hasSystemFeature(String) / hasSystemFeature(String, int):
 *   FORCE_TRUE -> true; FORCE_FALSE -> false; DEFAULT -> original method.
 * - getSystemAvailableFeatures():
 *   strip FORCE_FALSE entries; append synthetic FeatureInfo(name, version=0, flags=0) for
 *   FORCE_TRUE entries that are missing from the original result.
 * - Rules are pulled via ContentResolver.call(authority, "resolve", pkg) from ConfigProvider,
 *   never from Xposed remote preferences. After loading, a CONFIG_LOADED broadcast is sent back
 *   to the settings app to drive the "已生效" indicator.
 */
class FeatureManagerModule : XposedModule() {

    @Volatile
    private var processName: String = "unknown"

    @Volatile
    private var hostPackageName: String? = null

    @Volatile
    private var hooksInstalled = false

    private val installLock = Any()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        logDebug("module loaded in process=$processName, framework=${frameworkName} ${frameworkVersion}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        hostPackageName = pkg
        val loader = try {
            param.defaultClassLoader
        } catch (_: Throwable) {
            javaClass.classLoader
        }
        installHooks(pkg, loader)
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val previousPkg = hostPackageName
        ConfigClient.invalidate()
        synchronized(installLock) { hooksInstalled = false }
        super.onHotReloaded(param)
        if (!previousPkg.isNullOrBlank()) {
            installHooks(previousPkg, javaClass.classLoader)
        }
        logDebug("hot reload completed for $previousPkg")
    }

    private fun installHooks(packageName: String, loader: ClassLoader?) {
        synchronized(installLock) {
            if (hooksInstalled) return
            val apmClass = runCatching {
                Class.forName(APM_CLASS_NAME, false, loader ?: javaClass.classLoader)
            }.getOrElse { error ->
                logWarn("Failed to locate $APM_CLASS_NAME: ${error.message}")
                return
            }

            val hasFeature1 = findMethod(apmClass, "hasSystemFeature", String::class.java)
            val hasFeature2 = findMethod(
                apmClass,
                "hasSystemFeature",
                String::class.java,
                Integer.TYPE
            )
            val getFeatures = findMethod(apmClass, "getSystemAvailableFeatures")

            if (hasFeature1 == null && hasFeature2 == null && getFeatures == null) {
                logWarn("No target methods found on $APM_CLASS_NAME")
                return
            }

            hasFeature1?.let { hookHasFeature(it, packageName) }
            hasFeature2?.let { hookHasFeature(it, packageName) }
            getFeatures?.let { hookGetSystemAvailableFeatures(it, packageName) }

            hooksInstalled = true
            logDebug(
                "Installed hooks pkg=$packageName " +
                    "hasSystemFeature(String)=${hasFeature1 != null} " +
                    "hasSystemFeature(String,int)=${hasFeature2 != null} " +
                    "getSystemAvailableFeatures=${getFeatures != null}"
            )
        }
    }

    private fun hookHasFeature(method: Method, packageName: String) {
        val module = this
        hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val featureName = chain.getArg(0) as? String
                if (featureName.isNullOrBlank()) {
                    chain.proceed()
                } else {
                    val rules = ConfigClient.resolve(
                        packageName = packageName,
                        hookedObject = chain.thisObject,
                        processName = processName,
                        log = { module.logDebug(it) }
                    )
                    when {
                        rules.remove.contains(featureName) -> false
                        rules.add.contains(featureName) -> true
                        else -> chain.proceed()
                    }
                }
            }
    }

    private fun hookGetSystemAvailableFeatures(method: Method, packageName: String) {
        val module = this
        hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val original = chain.proceed()

                @Suppress("UNCHECKED_CAST")
                val source = original as? Array<FeatureInfo>
                    ?: return@intercept original

                val rules = ConfigClient.resolve(
                    packageName = packageName,
                    hookedObject = chain.thisObject,
                    processName = processName,
                    log = { module.logDebug(it) }
                )

                val result = ArrayList<FeatureInfo>(source.size + rules.add.size)
                val existingNames = HashSet<String>(source.size + rules.add.size)
                for (info in source) {
                    val name = info?.name
                    if (name.isNullOrEmpty()) continue
                    if (rules.remove.contains(name)) continue
                    if (existingNames.add(name)) {
                        result.add(info)
                    }
                }

                for (name in rules.add.sorted()) {
                    if (!existingNames.add(name)) continue
                    val synthetic = FeatureInfo().apply {
                        this.name = name
                        this.version = 0
                        this.flags = 0
                        this.reqGlEsVersion = 0
                    }
                    result.add(synthetic)
                }

                result.toTypedArray()
            }
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        return runCatching { clazz.getDeclaredMethod(name, *parameterTypes) }.getOrNull()
            ?: runCatching { clazz.getMethod(name, *parameterTypes) }.getOrNull()
    }

    private fun logDebug(message: String) {
        runCatching { log(Log.DEBUG, TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { log(Log.WARN, TAG, message) }
    }

    private companion object {
        const val TAG = "FeatureManager.Xposed"
        const val APM_CLASS_NAME = "android.app.ApplicationPackageManager"
    }
}
