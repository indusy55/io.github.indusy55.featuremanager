package io.github.indusy55.featuremanager.ui.main
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.indusy55.featuremanager.data.AppListRepository
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.service.FrameworkService
import io.github.indusy55.featuremanager.ui.components.AppIcon
import io.github.indusy55.featuremanager.ui.components.AppScopeStatusChip
import io.github.indusy55.featuremanager.ui.components.EmptyState
import io.github.indusy55.featuremanager.ui.components.SearchField
import kotlinx.coroutines.launch

private enum class AppFilter(val label: String) {
    ALL("全部"),
    SYSTEM("系统应用"),
    THIRD_PARTY("用户应用"),
    CONFIGURED("已配置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onOpenAppRules: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appRepo = remember(context) { AppListRepository.get(context) }
    val repo = remember(context) { FeatureRepository.get(context) }
    val installedApps by appRepo.installedApps.collectAsStateWithLifecycle()
    val isLoading by appRepo.isLoadingApps.collectAsStateWithLifecycle()
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val framework by FrameworkService.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { appRepo.ensureLoaded() }
    val scopeMessage = framework.lastScopeMessage
    LaunchedEffect(scopeMessage) {
        if (!scopeMessage.isNullOrBlank()) snackbarHostState.showSnackbar(scopeMessage)
    }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }

    val configuredPackages = remember(state.appConfigs) {
        state.appConfigs.filterValues { it.hasActiveRules }.keys
    }
    val visibleApps = remember(installedApps, configuredPackages, query, filter) {
        val keyword = query.trim()
        installedApps.asSequence()
            .filter { app ->
                val matchesQuery = keyword.isEmpty() ||
                    app.label.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.SYSTEM -> app.isSystemApp
                    AppFilter.THIRD_PARTY -> !app.isSystemApp
                    AppFilter.CONFIGURED -> app.packageName in configuredPackages
                }
                matchesQuery && matchesFilter
            }
            .sortedBy { it.label }
            .toList()
    }

    fun requestScope(packageName: String) {
        if (!framework.isConnected) {
            scope.launch { snackbarHostState.showSnackbar("Xposed 框架未连接，无法申请作用域".i18n()) }
            return
        }
        FrameworkService.requestScope(listOf(packageName)) { approved, error ->
            val message = error ?: if (approved.isNotEmpty()) {
                "已将 $packageName 加入模块作用域"
            } else {
                "作用域请求未获批准"
            }
            scope.launch { snackbarHostState.showSnackbar(message.i18n()) }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        text = "应用列表",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { appRepo.refreshInstalledApps() }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新应用列表")
                    }
                }
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索应用名称或包名"
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleApps.isEmpty() && !isLoading) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Apps,
                            title = "没有匹配的应用",
                            message = if (query.isBlank()) {
                                "调整筛选条件，或点击右上角刷新应用列表。"
                            } else {
                                "没有名称或包名匹配“$query”的应用。"
                            }
                        )
                    }
                } else {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val inScope = framework.isPackageInScope(app.packageName)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenAppRules(app.packageName) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(packageName = app.packageName, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = app.label.takeIf { it.isNotBlank() } ?: app.packageName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val version = app.versionName.takeIf { it.isNotBlank() }
                                            ?: "code ${app.versionCode}"
                                        Text(
                                            text = "版本 $version" + if (app.isSystemApp) " · 系统应用" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    AppScopeStatusChip(
                                        packageName = app.packageName,
                                        frameworkStatus = framework,
                                        loadedState = state.loadedStates[app.packageName],
                                        compact = state.settings.compactCards
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (!inScope) {
                                        OutlinedButton(
                                            onClick = { requestScope(app.packageName) },
                                            enabled = framework.isConnected,
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("申请作用域", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
