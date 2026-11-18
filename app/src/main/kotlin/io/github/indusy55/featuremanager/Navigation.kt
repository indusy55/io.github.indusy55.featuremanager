package io.github.indusy55.featuremanager
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
        NavDisplay(
            backStack = nav.backStack,
            onBack = { nav.removeLast() },
            modifier = Modifier.padding(innerPadding),
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
                        onBack = { nav.removeLast() }
                    )
                }
                entry<TemplateEditorScreen> { key ->
                    TemplateEditorScreen(
                        templateId = key.templateId,
                        onBack = { nav.removeLast() }
                    )
                }
            }
        )
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

    fun removeLast() {
        val currentStack = stacks[topLevelKey] ?: return
        if (currentStack.size <= 1) return
        currentStack.removeLastOrNull()
        updateBackStack()
    }

    fun replaceCurrent(key: T) {
        val currentStack = stacks[topLevelKey] ?: return
        if (currentStack.isNotEmpty()) {
            currentStack[currentStack.lastIndex] = key
        }
        updateBackStack()
    }
}
