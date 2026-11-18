package io.github.indusy55.featuremanager

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeTab : NavKey

@Serializable
data object AppsTab : NavKey

@Serializable
data object TemplatesTab : NavKey

@Serializable
data object SettingsTab : NavKey

@Serializable
data class AppRulesScreen(val packageName: String) : NavKey

@Serializable
data class TemplateEditorScreen(val templateId: String?) : NavKey
