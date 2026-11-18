package io.github.indusy55.featuremanager.ui.main
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import io.github.indusy55.featuremanager.i18n.Text
import io.github.indusy55.featuremanager.i18n.i18n
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.indusy55.featuremanager.data.AppListRepository
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.data.LoadedProcessState
import io.github.indusy55.featuremanager.service.FrameworkService
import io.github.indusy55.featuremanager.ui.components.ConfiguredAppCard
import io.github.indusy55.featuremanager.ui.components.EmptyState
import io.github.indusy55.featuremanager.ui.components.SearchField

private const val LOADED_FRESH_MS = 15 * 60 * 1000L

private fun isFreshLoaded(loaded: LoadedProcessState?, revision: Int): Boolean {
    return loaded != null && loaded.configVersion == revision && loaded.timestamp > 0L &&
        System.currentTimeMillis() - loaded.timestamp < LOADED_FRESH_MS
}

private enum class HomeFilter(val label: String) {
    ALL("全部"),
    EFFECTIVE("仅已生效")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenAppRules: (String) -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { FeatureRepository.get(context) }
    val appRepo = remember(context) { AppListRepository.get(context) }
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val installedApps by appRepo.installedApps.collectAsStateWithLifecycle()
    val framework by FrameworkService.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { appRepo.ensureLoaded() }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HomeFilter.ALL) }

    val labels = remember(installedApps) {
        installedApps.associate { it.packageName to it.label }
    }
    fun labelOf(pkg: String): String = labels[pkg]?.takeIf { it.isNotBlank() } ?: pkg

    fun openAppDetails(pkg: String) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$pkg")
            )
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "打开应用信息失败".i18n(), Toast.LENGTH_SHORT).show()
        }
    }


    val configuredApps = remember(state.appConfigs, state.loadedStates, labels, query, filter) {
        val keyword = query.trim()
        state.appConfigs.values.asSequence()
            .filter { it.hasActiveRules }
            .filter {
                filter == HomeFilter.ALL || isFreshLoaded(state.loadedStates[it.packageName], state.revision)
            }
            .filter { config ->
                keyword.isEmpty() ||
                    config.packageName.contains(keyword, ignoreCase = true) ||
                    labelOf(config.packageName).contains(keyword, ignoreCase = true)
            }
            .sortedBy { labelOf(it.packageName) }
            .toList()
    }

    val hasAnyConfig = state.appConfigs.values.any { it.hasActiveRules }
    val showFrameworkBanner = !framework.isConnected
    val showEmptyBanner = !hasAnyConfig

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0.dp),
            title = {
                Text(
                    text = "Feature Manager",
                    style = MaterialTheme.typography.titleLarge
                )
            },
        )

        if (showFrameworkBanner || showEmptyBanner) {
            val containerColor = if (showFrameworkBanner) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val contentColor = if (showFrameworkBanner) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showFrameworkBanner) {
                        Text(
                            text = "Xposed 框架未连接：请在框架管理器中启用本模块并选择应用作用域。",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    }
                    if (showEmptyBanner) {
                        Text(
                            text = "尚未配置应用规则，点击“添加应用规则”开始为应用定制系统特性。",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Button(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                Text("添加应用规则")
            }
            Spacer(Modifier.height(12.dp))
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索已配置应用"
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (configuredApps.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Apps,
                title = "没有匹配的应用",
                message = if (query.isBlank()) {
                    "为应用添加规则后，它们会显示在这里。"
                } else {
                    "没有名称或包名匹配“$query”的已配置应用。"
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            val cellMinWidth = when (state.settings.homeGridColumns.coerceIn(1, 4)) {
                1 -> 176.dp
                2 -> 128.dp
                3 -> 104.dp
                else -> 88.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = cellMinWidth),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(configuredApps, key = { it.packageName }) { config ->
                    val loaded = state.loadedStates[config.packageName]
                    ConfiguredAppCard(
                        packageName = config.packageName,
                        label = labelOf(config.packageName),
                        config = config,
                        onClick = { onOpenAppRules(config.packageName) },
                        loadedVersion = if (isFreshLoaded(loaded, state.revision)) loaded?.configVersion else null,
                        compact = state.settings.compactCards,
                        onAppInfo = { openAppDetails(config.packageName) },
                        onRemoveRules = { repo.clearAppRules(config.packageName) }
                    )
                }
            }
        }
    }

}
