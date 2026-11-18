package io.github.indusy55.featuremanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.data.LoadedProcessState

/**
 * Receives `io.github.indusy55.featuremanager.CONFIG_LOADED` broadcasts from hooked target
 * processes when they pull and apply feature rules from [ConfigProvider], updating the UI's
 * "已生效" status indicator.
 */
class LoadedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ConfigContract.ACTION_CONFIG_LOADED) return

        val pkg = intent.getStringExtra(ConfigContract.EXTRA_PACKAGE)?.trim()
        if (!ConfigContract.isValidPackageName(pkg)) return

        val processName = intent.getStringExtra(ConfigContract.EXTRA_PROCESS)
            ?.takeIf { it.isNotBlank() }
            ?: pkg!!
        val version = intent.getIntExtra(ConfigContract.EXTRA_VERSION, 0)
        val timestamp = intent.getLongExtra(
            ConfigContract.EXTRA_TIMESTAMP,
            System.currentTimeMillis()
        )
        val addedCount = intent.getIntExtra(ConfigContract.EXTRA_ADDED_COUNT, 0)
        val removedCount = intent.getIntExtra(ConfigContract.EXTRA_REMOVED_COUNT, 0)
        val fingerprint = intent.getStringExtra(ConfigContract.EXTRA_FINGERPRINT).orEmpty()

        FeatureRepository.get(context).markConfigLoaded(
            LoadedProcessState(
                packageName = pkg!!,
                processName = processName,
                configVersion = version,
                timestamp = timestamp,
                addedCount = addedCount,
                removedCount = removedCount,
                rulesFingerprint = fingerprint
            )
        )
    }
}
