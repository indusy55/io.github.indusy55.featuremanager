@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.indusy55.featuremanager.ui.main
import androidx.compose.foundation.layout.WindowInsets

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import io.github.indusy55.featuremanager.i18n.Text
import io.github.indusy55.featuremanager.i18n.i18n
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.indusy55.featuremanager.data.AccentStyle
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.data.ThemeMode
import io.github.indusy55.featuremanager.service.FrameworkService
import io.github.indusy55.featuremanager.ui.components.BadgeTone
import io.github.indusy55.featuremanager.ui.components.SectionHeader
import io.github.indusy55.featuremanager.ui.components.StatusBadge

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember(context) { FeatureRepository.get(context) }
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val framework by FrameworkService.status.collectAsStateWithLifecycle()
    val settings = state.settings
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(text = "设置", style = MaterialTheme.typography.titleLarge)
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = "外观与交互", subtitle = "修改后立即保存")

                        ChipChoiceRow(
                            label = "主题模式",
                            entries = ThemeMode.entries,
                            selected = settings.themeMode,
                            labelOf = { it.label }
                        ) { mode ->
                            repo.updateSettings { it.copy(themeMode = mode) }
                        }

                        SettingSwitch(
                            title = "动态取色",
                            description = if (dynamicColorAvailable) {
                                "使用系统壁纸配色（Android 12 及以上）"
                            } else {
                                "当前系统版本不支持动态取色"
                            },
                            checked = settings.dynamicColor,
                            enabled = dynamicColorAvailable,
                            onCheckedChange = { enabled ->
                                repo.updateSettings { it.copy(dynamicColor = enabled) }
                            }
                        )

                        SettingSwitch(
                            title = "纯黑深色背景",
                            description = "深色模式下使用纯黑背景，适合 OLED 屏幕",
                            checked = settings.pureBlackDark,
                            onCheckedChange = { enabled ->
                                repo.updateSettings { it.copy(pureBlackDark = enabled) }
                            }
                        )

                        ChipChoiceRow(
                            label = "强调色",
                            entries = AccentStyle.entries,
                            selected = settings.accentStyle,
                            labelOf = { it.label }
                        ) { style ->
                            repo.updateSettings { it.copy(accentStyle = style) }
                        }

                        ChipChoiceRow(
                            label = "首页网格列数",
                            entries = listOf(1, 2, 3, 4),
                            selected = settings.homeGridColumns.coerceIn(1, 4),
                            labelOf = { "$it 列" }
                        ) { columns ->
                            repo.updateSettings { it.copy(homeGridColumns = columns) }
                        }

                        SettingSwitch(
                            title = "紧凑卡片",
                            description = "在列表和规则页使用更紧凑的布局",
                            checked = settings.compactCards,
                            onCheckedChange = { enabled ->
                                repo.updateSettings { it.copy(compactCards = enabled) }
                            }
                        )

                        SettingSwitch(
                            title = "默认显示系统应用",
                            description = "应用列表中默认包含系统应用",
                            checked = settings.showSystemAppsByDefault,
                            onCheckedChange = { enabled ->
                                repo.updateSettings { it.copy(showSystemAppsByDefault = enabled) }
                            }
                        )

                        SettingSwitch(
                            title = "保存规则后自动申请作用域",
                            description = "应用模板或规则后，自动为目标应用申请模块作用域",
                            checked = settings.autoRequestScopeOnSave,
                            onCheckedChange = { enabled ->
                                repo.updateSettings { it.copy(autoRequestScopeOnSave = enabled) }
                            }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            title = "模块状态",
                            subtitle = "Xposed 框架连接与作用域",
                            trailing = {
                                IconButton(onClick = { FrameworkService.refreshStatus() }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "刷新状态")
                                }
                            }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = if (framework.isConnected) "已连接" else "未激活",
                                tone = if (framework.isConnected) BadgeTone.SUCCESS else BadgeTone.ERROR
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = listOf(framework.frameworkName, framework.frameworkVersion)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
                                    .ifBlank { "未知框架" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (framework.isConnected) {
                            Text(
                                text = "API 版本：${framework.apiVersion}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "作用域应用：${framework.scopePackages.size} 个",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (framework.scopePackages.isNotEmpty()) {
                                Text(
                                    text = framework.scopePackages.take(4).joinToString("、") +
                                        if (framework.scopePackages.size > 4) " 等" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            if (framework.runningProcesses.isEmpty()) {
                                Text(
                                    text = "暂无运行中的 Hook 进程",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text("运行中的 Hook 进程：", style = MaterialTheme.typography.bodySmall)
                                framework.runningProcesses.take(6).forEach { process ->
                                    Text(
                                        text = "· ${process.processName}（PID ${process.pid}）· v${process.loadedVersionCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (framework.runningProcesses.size > 6) {
                                    Text(
                                        text = "…另有 ${framework.runningProcesses.size - 6} 个进程",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "在 Xposed 管理器中启用本模块、勾选目标应用作用域并重启应用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val scopeMessage = framework.lastScopeMessage
                        if (!scopeMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "最近消息：$scopeMessage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = "关于", subtitle = "工作原理与配置状态")
                        Text(
                            text = "Feature Manager 通过 Xposed Hook 拦截目标应用对系统 Feature 的查询，可按应用强制开启或关闭指定特性；模板是可复用的规则集，可用于批量配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "规则生效状态由 ConfigProvider 快照查询与 LOADED 广播更新；最近生效时间与配置版本可在应用规则页查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "配置版本 (revision)：${state.revision}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "已配置应用：${state.appConfigs.size} · 自定义模板：${state.customTemplates.size} · 已上报进程：${state.loadedStates.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun <T> ChipChoiceRow(
    label: String,
    entries: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                FilterChip(
                    selected = entry == selected,
                    onClick = { onSelect(entry) },
                    label = {
                        Text(
                            text = labelOf(entry),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
