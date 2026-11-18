package io.github.indusy55.featuremanager.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.data.FeatureRepository

/**
 * Exposes per-package feature rules (`add` and `remove` String arrays) to hooked target processes
 * via `ContentResolver.call("content://io.github.indusy55.featuremanager.config", "resolve", pkg, null)`.
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { FeatureRepository.get(it) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle().apply {
            putString(ConfigContract.KEY_ERROR, "Provider context unavailable")
            putStringArray(ConfigContract.KEY_ADD, emptyArray())
            putStringArray(ConfigContract.KEY_REMOVE, emptyArray())
        }

        if (method == ConfigContract.METHOD_PING) {
            return Bundle().apply {
                putInt(ConfigContract.KEY_VERSION, FeatureRepository.get(ctx).current.revision)
            }
        }

        if (method != ConfigContract.METHOD_RESOLVE) {
            return Bundle().apply {
                putString(ConfigContract.KEY_ERROR, "Unsupported method: $method")
                putStringArray(ConfigContract.KEY_ADD, emptyArray())
                putStringArray(ConfigContract.KEY_REMOVE, emptyArray())
            }
        }

        val targetPackage = arg?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras?.getString(ConfigContract.EXTRA_PACKAGE)?.trim()

        if (!ConfigContract.isValidPackageName(targetPackage)) {
            return Bundle().apply {
                putString(ConfigContract.KEY_ERROR, "Invalid package name")
                putStringArray(ConfigContract.KEY_ADD, emptyArray())
                putStringArray(ConfigContract.KEY_REMOVE, emptyArray())
            }
        }

        val snapshot = FeatureRepository.get(ctx).resolveForProvider(targetPackage!!)
        return Bundle().apply {
            putStringArray(ConfigContract.KEY_ADD, snapshot.add)
            putStringArray(ConfigContract.KEY_REMOVE, snapshot.remove)
            putInt(ConfigContract.KEY_VERSION, snapshot.version)
            putBoolean(ConfigContract.KEY_Configured, snapshot.hasActiveRules)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
