package io.github.indusy55.featuremanager.xposed

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import io.github.indusy55.featuremanager.core.ConfigContract
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side resolver used inside each hooked target process. It talks to the Settings app's
 * [io.github.indusy55.featuremanager.service.ConfigProvider] through
 * [ContentResolver.call] (authority form for API 26 compatibility), caches results briefly to
 * avoid a binder round trip on every hasSystemFeature() call, and sends a CONFIG_LOADED
 * broadcast back to the settings UI once new rules have been loaded.
 */
object ConfigClient {

    data class RuleConfig(
        val add: Set<String>,
        val remove: Set<String>,
        val version: Int
    ) {
        companion object {
            val EMPTY = RuleConfig(emptySet(), emptySet(), 0)
        }
    }

    private const val CACHE_TTL_MS = 1_500L

    private data class CacheEntry(val config: RuleConfig, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val querying = ThreadLocal.withInitial { false }

    /** Version of the last configuration that triggered a CONFIG_LOADED broadcast. */
    @Volatile
    private var lastReportedVersion: Int = -1

    fun resolve(
        packageName: String,
        hookedObject: Any?,
        processName: String,
        log: (msg: String) -> Unit = {}
    ): RuleConfig {
        val now = SystemClock.elapsedRealtime()
        cache[packageName]?.let { cached ->
            if (now - cached.fetchedAt < CACHE_TTL_MS) return cached.config
        }
        if (querying.get() == true) {
            return cache[packageName]?.config ?: RuleConfig.EMPTY
        }

        querying.set(true)
        try {
            val context = findHostContext(hookedObject)
            if (context == null) {
                log("Cannot resolve config for $packageName: host Context unavailable")
                return cache[packageName]?.config ?: RuleConfig.EMPTY
            }

            val result = runCatching {
                context.contentResolver.call(
                    ConfigContract.AUTHORITY,
                    ConfigContract.METHOD_RESOLVE,
                    packageName,
                    null
                )
            }.onFailure { error ->
                log("ContentResolver.call failed: ${error.javaClass.simpleName}: ${error.message}")
            }.getOrNull()

            if (result == null) {
                return cache[packageName]?.config ?: RuleConfig.EMPTY
            }

            if (!result.containsKey(ConfigContract.KEY_ADD) ||
                !result.containsKey(ConfigContract.KEY_REMOVE)
            ) {
                val err = result.getString(ConfigContract.KEY_ERROR)
                log("ConfigProvider error: ${err ?: "missing add/remove arrays"}")
                return cache[packageName]?.config ?: RuleConfig.EMPTY
            }

            val add = result.getStringArray(ConfigContract.KEY_ADD)
                ?.asSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            val remove = result.getStringArray(ConfigContract.KEY_REMOVE)
                ?.asSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            val version = result.getInt(ConfigContract.KEY_VERSION, 0)

            val config = RuleConfig(add = add, remove = remove, version = version)
            cache[packageName] = CacheEntry(config, SystemClock.elapsedRealtime())

            if (version != lastReportedVersion) {
                lastReportedVersion = version
                sendLoadedBroadcast(context, packageName, processName, config)
                log("Loaded config v$version for $packageName: +${add.size}/-${remove.size}")
            }
            return config
        } finally {
            querying.set(false)
        }
    }

    fun invalidate() {
        cache.clear()
        lastReportedVersion = -1
    }

    private fun sendLoadedBroadcast(
        context: Context,
        packageName: String,
        processName: String,
        config: RuleConfig
    ) {
        val intent = Intent(ConfigContract.ACTION_CONFIG_LOADED).apply {
            `package` = ConfigContract.APP_ID
            putExtra(ConfigContract.EXTRA_PACKAGE, packageName)
            putExtra(ConfigContract.EXTRA_PROCESS, processName)
            putExtra(ConfigContract.EXTRA_VERSION, config.version)
            putExtra(ConfigContract.EXTRA_TIMESTAMP, System.currentTimeMillis())
            putExtra(ConfigContract.EXTRA_ADDED_COUNT, config.add.size)
            putExtra(ConfigContract.EXTRA_REMOVED_COUNT, config.remove.size)
        }
        runCatching { context.sendBroadcast(intent) }
    }

    /**
     * Attempts several boot-level entry points to obtain a usable Context in the target process.
     * This is intentionally best-effort: when no Context is available the hooks fall back to
     * the original PackageManager behavior rather than breaking the target application.
     */
    private fun findHostContext(hookedObject: Any?): Context? {
        val viaActivityThread = runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        }.getOrNull()
        if (viaActivityThread != null) return viaActivityThread

        val viaAppGlobals = runCatching {
            val clazz = Class.forName("android.app.AppGlobals")
            val method = clazz.getDeclaredMethod("getInitialApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        }.getOrNull()
        if (viaAppGlobals != null) return viaAppGlobals

        if (hookedObject != null) {
            val viaApmField = runCatching {
                val clazz = hookedObject.javaClass
                val field = findField(clazz, "mContext") ?: return@runCatching null
                field.isAccessible = true
                field.get(hookedObject) as? Context
            }.getOrNull()
            if (viaApmField != null) return viaApmField
        }

        return null
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) return field
            current = current.superclass
        }
        return null
    }
}
