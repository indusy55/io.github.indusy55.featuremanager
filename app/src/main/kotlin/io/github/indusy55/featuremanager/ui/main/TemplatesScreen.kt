@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.indusy55.featuremanager.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.indusy55.featuremanager.data.FeatureTemplate
import io.github.indusy55.featuremanager.data.InstalledAppInfo
import io.github.indusy55.featuremanager.service.FrameworkService
import io.github.indusy55.featuremanager.ui.components.BadgeTone
import io.github.indusy55.featuremanager.ui.components.EmptyState
import io.github.indusy55.featuremanager.ui.components.SearchField
import io.github.indusy55.featuremanager.ui.components.StatusBadge
import io.github.indusy55.featuremanager.ui.components.TemplateCard
import kotlinx.coroutines.launch

private enum class TemplateFilter(val label: String) {
    ALL("全部"),
    BUILT_IN("内置"),
    CUSTOM("自定义")
}

@Composable
fun TemplatesScreen(
    onOpenTemplateEditor: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { FeatureRepository.get(context) }
    val appRepo = remember(context) { AppListRepository.get(context) }
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val installedApps by appRepo.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by appRepo.isLoadingApps.collectAsStateWithLifecycle()
    val framework by FrameworkService.status.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TemplateFilter.ALL) }
    var applyTarget by remember { mutableStateOf<FeatureTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<FeatureTemplate?>(null) }

    LaunchedEffect(Unit) { appRepo.ensureLoaded() }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                notify("无法读取所选文件")
                return@launch
            }
            repo.importFromJsonString(raw)
                .onSuccess { count -> notify("配置导入成功，恢复 $count 项应用配置") }
                .onFailure { error -> notify("导入失败：${error.message ?: "文件内容无效"}") }
        }
    }

    fun exportConfig() {
        val json = repo.exportToJsonString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            notify("剪贴板服务不可用，导出失败")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Feature Manager 配置", json))
        notify("配置已复制到剪贴板（${json.length} 字符）")
    }

    val templates = state.allTemplates
    val visibleTemplates = remember(templates, query, filter) {
        val keyword = query.trim()
        templates.filter { template ->
            val matchesFilter = when (filter) {
                TemplateFilter.ALL -> true
                TemplateFilter.BUILT_IN -> template.builtIn
                TemplateFilter.CUSTOM -> !template.builtIn
            }
            val matchesQuery = keyword.isEmpty() ||
                listOf(template.name, template.subtitle, template.description, template.badge)
                    .any { it.contains(keyword, ignoreCase = true) } ||
                template.rules.keys.any { it.contains(keyword, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("模板管理") },
                actions = {
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入配置")
                    }
                    IconButton(onClick = { exportConfig() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "导出配置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text("模板是一组可复用的 Feature 规则", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "应用模板采用补丁（patch）方式：仅覆盖模板列出的特性，其余规则保持不变；勾选“替换现有规则”才会清空原有规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onOpenTemplateEditor(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建模板")
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "共 ${templates.size} 个模板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索模板名称、说明或 Feature"
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (visibleTemplates.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Dashboard,
                    title = "没有匹配的模板",
                    message = "试试其他关键词或筛选条件，也可以新建一个自定义模板。",
                    actionLabel = "新建模板",
                    onAction = { onOpenTemplateEditor(null) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleTemplates, key = { it.id }) { template ->
                        Column {
                            TemplateCard(
                                template = template,
                                onClick = { onOpenTemplateEditor(template.id) },
                                onDuplicate = {
                                    val copy = repo.duplicateAsCustomTemplate(template)
                                    notify("已复制为自定义模板：${copy.name}")
                                },
                                onDelete = if (template.builtIn) null else {
                                    { deleteTarget = template }
                                }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { applyTarget = template },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("应用到应用")
                                }
                                StatusBadge(
                                    text = if (template.builtIn) "内置" else "自定义",
                                    tone = if (template.builtIn) BadgeTone.INFO else BadgeTone.NEUTRAL
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    applyTarget?.let { template ->
        ApplyTemplateDialog(
            template = template,
            apps = installedApps,
            isLoading = isLoadingApps,
            autoRequestScope = state.settings.autoRequestScopeOnSave && framework.isConnected,
            onDismiss = { applyTarget = null },
            onConfirm = { packages, replaceExisting ->
                repo.applyTemplatePatch(packages, template, replaceExisting)
                if (state.settings.autoRequestScopeOnSave && framework.isConnected) {
                    val needScope = packages.filterNot { pkg -> framework.isPackageInScope(pkg) }
                    if (needScope.isNotEmpty()) FrameworkService.requestScope(needScope)
                }
                applyTarget = null
                notify("已将「${template.name}」应用到 ${packages.size} 个应用")
            }
        )
    }

    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除模板") },
            text = { Text("确定删除自定义模板「${template.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteCustomTemplate(template.id)
                    deleteTarget = null
                    notify("模板「${template.name}」已删除")
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ApplyTemplateDialog(
    template: FeatureTemplate,
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    autoRequestScope: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (packages: List<String>, replaceExisting: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replaceExisting by remember { mutableStateOf(false) }

    val keyword = query.trim()
    val visibleApps = if (keyword.isEmpty()) {
        apps
    } else {
        apps.filter {
            it.label.contains(keyword, ignoreCase = true) ||
                it.packageName.contains(keyword, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用模板：${template.name}") },
        text = {
            Column {
                Text(
                    text = "选择目标应用（可多选）。默认只覆盖模板列出的特性，其他规则保持不变。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                SearchField(query = query, onQueryChange = { query = it }, placeholder = "搜索应用名称或包名")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                    Text("替换现有规则", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                when {
                    isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("正在读取应用列表…", style = MaterialTheme.typography.bodySmall)
                    }

                    visibleApps.isEmpty() -> Text(
                        text = "没有匹配的应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(visibleApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selected
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selected = if (isSelected) {
                                        selected - app.packageName
                                    } else {
                                        selected + app.packageName
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = {
                                    Column {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodySmall,
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
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("已选择 ${selected.size} 个应用")
                        if (autoRequestScope) append(" · 应用后将自动申请作用域")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.toList(), replaceExisting) },
                enabled = selected.isNotEmpty()
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
