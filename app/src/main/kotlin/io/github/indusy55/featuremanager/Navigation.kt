package io.github.indusy55.featuremanager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.indusy55.featuremanager.ui.main.AppRulesScreen
import io.github.indusy55.featuremanager.ui.main.AppsScreen
import io.github.indusy55.featuremanager.ui.main.HomeScreen
import io.github.indusy55.featuremanager.ui.main.SettingsScreen
import io.github.indusy55.featuremanager.ui.main.TemplateEditorScreen
import io.github.indusy55.featuremanager.ui.main.TemplatesScreen

private sealed interface TopLevelRoute {
    val label: String
    val icon: ImageVector
}

private data object HomeRoute : TopLevelRoute {
    override val label = "首页"
    override val icon = Icons.Filled.Home
}

private data object AppsRoute : TopLevelRoute {
    override val label = "应用"
    override val icon = Icons.Filled.Apps
}

private data object TemplatesRoute : TopLevelRoute {
    override val label = "模板"
    override val icon = Icons.Filled.Dashboard
}

private data object SettingsRoute : TopLevelRoute {
    override val label = "设置"
    override val icon = Icons.Filled.Settings
}

private val TOP_LEVEL_ROUTES = listOf(HomeRoute, AppsRoute, TemplatesRoute, SettingsRoute)

@Composable
fun MainNavigation() {
    val nav = remember { TopLevelBackStack<Any>(HomeTab) }
    val context = LocalContext.current
    fun handleBack() {
        if (!nav.removeLast()) {
            context.findActivity()?.finish()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_ROUTES.forEach { route ->
                    val selected = when (route) {
                        HomeRoute -> nav.topLevelKey == HomeTab
                        AppsRoute -> nav.topLevelKey == AppsTab
                        TemplatesRoute -> nav.topLevelKey == TemplatesTab
                        SettingsRoute -> nav.topLevelKey == SettingsTab
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.addTopLevel(
                                when (route) {
                                    HomeRoute -> HomeTab
                                    AppsRoute -> AppsTab
                                    TemplatesRoute -> TemplatesTab
                                    SettingsRoute -> SettingsTab
                                }
                            )
                        },
                        icon = { Icon(route.icon, contentDescription = null) },
                        label = { Text(route.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
            ) {
                NavDisplay(
                    backStack = nav.backStack,
                    onBack = { handleBack() },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(160)) togetherWith
                            fadeOut(animationSpec = tween(160))
                    },
                    popTransitionSpec = {
                        fadeIn(animationSpec = tween(160)) togetherWith
                            fadeOut(animationSpec = tween(160))
                    },
                    predictivePopTransitionSpec = {
                        fadeIn(animationSpec = tween(160)) togetherWith
                            fadeOut(animationSpec = tween(160))
                    },
                    modifier = Modifier.fillMaxSize(),
                    entryProvider = entryProvider {
                        entry<HomeTab> {
                            HomeScreen(
                                onOpenAppRules = { pkg -> nav.add(AppRulesScreen(pkg)) },
                                onOpenApps = { nav.addTopLevel(AppsTab) }
                            )
                        }
                        entry<AppsTab> {
                            AppsScreen(
                                onOpenAppRules = { pkg -> nav.add(AppRulesScreen(pkg)) }
                            )
                        }
                        entry<TemplatesTab> {
                            TemplatesScreen(
                                onOpenTemplateEditor = { id -> nav.add(TemplateEditorScreen(id)) }
                            )
                        }
                        entry<SettingsTab> {
                            SettingsScreen()
                        }
                        entry<AppRulesScreen> { key ->
                            AppRulesScreen(
                                packageName = key.packageName,
                                onBack = { handleBack() }
                            )
                        }
                        entry<TemplateEditorScreen> { key ->
                            TemplateEditorScreen(
                                templateId = key.templateId,
                                onBack = { handleBack() }
                            )
                        }
                    }
                )
            }
        }
    }
}

/** Keeps an independent back stack per top-level tab (Navigation 3 common UI pattern). */
class TopLevelBackStack<T : Any>(startKey: T) {

    private val stacks = linkedMapOf<T, androidx.compose.runtime.snapshots.SnapshotStateList<T>>(
        startKey to mutableStateListOf(startKey)
    )

    var topLevelKey by mutableStateOf(startKey)
        private set

    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() {
        backStack.apply {
            clear()
            addAll(stacks.values.flatten())
        }
    }

    fun addTopLevel(key: T) {
        val existing = stacks.remove(key)
        if (existing != null) {
            stacks[key] = existing
        } else {
            stacks[key] = mutableStateListOf(key)
        }
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        stacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    fun removeLast(): Boolean {
        val currentStack = stacks[topLevelKey] ?: return false
        if (currentStack.size > 1) {
            currentStack.removeLastOrNull()
            updateBackStack()
            return true
        }
        if (stacks.size > 1) {
            stacks.remove(topLevelKey)
            topLevelKey = stacks.keys.last()
            updateBackStack()
            return true
        }
        return false
    }

    fun replaceCurrent(key: T) {
        val currentStack = stacks[topLevelKey] ?: return
        if (currentStack.isNotEmpty()) {
            currentStack[currentStack.lastIndex] = key
        }
        updateBackStack()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
