@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.indusy55.featuremanager.ui.main
import androidx.compose.foundation.layout.WindowInsets

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.core.FeatureCategory
import io.github.indusy55.featuremanager.data.AppListRepository
import io.github.indusy55.featuremanager.data.Decision
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.data.FeatureTemplate
import io.github.indusy55.featuremanager.ui.components.BadgeTone
import io.github.indusy55.featuremanager.ui.components.EmptyState
import io.github.indusy55.featuremanager.ui.components.FeatureRuleRow
import io.github.indusy55.featuremanager.ui.components.SearchField
import io.github.indusy55.featuremanager.ui.components.SectionHeader
import io.github.indusy55.featuremanager.ui.components.StatusBadge
import kotlinx.coroutines.launch

@Composable
fun TemplateEditorScreen(
    templateId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { FeatureRepository.get(context) }
    val appRepo = remember(context) { AppListRepository.get(context) }
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val deviceFeatures by appRepo.deviceFeatures.collectAsStateWithLifecycle()
    val settings = state.settings

    val source = if (templateId == null) {
        null
    } else {
        state.allTemplates.firstOrNull { it.id == templateId }
    }
    val isBuiltInSource = source != null && source.builtIn && source.id == templateId

    var editingId by remember(templateId) { mutableStateOf(templateId) }
    var name by remember(templateId) { mutableStateOf(source?.name.orEmpty()) }
    var subtitle by remember(templateId) { mutableStateOf(source?.subtitle.orEmpty()) }
    var description by remember(templateId) { mutableStateOf(source?.description.orEmpty()) }
    var rules by remember(templateId) {
        mutableStateOf<Map<String, Decision>>(LinkedHashMap(source?.rules.orEmpty()))
    }
    var customFeatureName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(templateId, source?.id) {
        val template = source ?: return@LaunchedEffect
        name = template.name
        subtitle = template.subtitle
        description = template.description
        rules = LinkedHashMap(template.rules)
    }
    LaunchedEffect(Unit) { appRepo.ensureLoaded() }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val editableCustom = if (editingId == null) {
        null
    } else {
        state.customTemplates.firstOrNull { it.id == editingId }
    }

    fun setDecision(feature: String, decision: Decision) {
        val next = LinkedHashMap(rules)
        if (decision == Decision.DEFAULT) {
            next.remove(feature)
        } else {
            next[feature] = decision
        }
        rules = next
    }

    fun addCustomFeature() {
        val feature = customFeatureName.trim()
        if (!ConfigContract.isValidPackageName(feature)) {
            notify("Feature 名称无效，请使用类似 org.chromium.arc 的格式")
            return
        }
        if (rules.containsKey(feature)) {
            notify("该 Feature 已存在")
            return
        }
        val next = LinkedHashMap(rules)
        next[feature] = Decision.FORCE_TRUE
        rules = next
        customFeatureName = ""
        notify("已添加 Feature：$feature")
    }

    fun saveTemplate() {
        val finalName = name.trim().ifEmpty { "未命名模板" }
        val finalId = when {
            editingId.isNullOrBlank() || isBuiltInSource -> "custom_${System.currentTimeMillis()}"
            else -> editingId!!
        }
        val badge = when {
            isBuiltInSource -> "内置副本"
            else -> source?.badge?.takeIf { it.isNotBlank() } ?: "自定义"
        }
        repo.saveCustomTemplate(
            FeatureTemplate(
                id = finalId,
                name = finalName,
                subtitle = subtitle.trim().ifEmpty { "自定义规则集" },
                description = description.trim(),
                rules = rules.filterValues { it != Decision.DEFAULT },
                builtIn = false,
                badge = badge
            )
        )
        editingId = finalId
        notify("模板「$finalName」已保存")
    }

    val catalog = remember(deviceFeatures, rules) {
        appRepo.buildCatalogWithDeviceState(rules.keys)
    }
    val keyword = query.trim()
    val grouped = remember(catalog, keyword) {
        catalog.filter { item ->
            keyword.isEmpty() ||
                item.spec.name.contains(keyword, ignoreCase = true) ||
                item.spec.label.contains(keyword, ignoreCase = true) ||
                item.spec.description.contains(keyword, ignoreCase = true)
        }.groupBy { it.spec.category }
    }

    val title = when {
        templateId == null -> "新建模板"
        source == null -> "模板不存在"
        isBuiltInSource -> "编辑模板（另存副本）"
        else -> "编辑模板"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { saveTemplate() }) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (templateId != null && source == null) {
            EmptyState(
                icon = Icons.Filled.Dashboard,
                title = "模板不存在",
                message = "该模板可能已被删除，请返回模板列表后重试。",
                actionLabel = "返回",
                onAction = onBack,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("模板名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = subtitle,
                                onValueChange = { subtitle = it },
                                label = { Text("副标题") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("说明") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusBadge(
                                    text = "开启 ${rules.values.count { it == Decision.FORCE_TRUE }}",
                                    tone = BadgeTone.SUCCESS
                                )
                                StatusBadge(
                                    text = "关闭 ${rules.values.count { it == Decision.FORCE_FALSE }}",
                                    tone = BadgeTone.ERROR
                                )
                                StatusBadge(text = "共 ${rules.size} 条")
                            }
                            if (isBuiltInSource) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "内置模板不可直接修改；保存后将创建一个新的自定义模板副本。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (editableCustom != null) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("删除模板")
                                }
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
                            Text("添加自定义 Feature", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "输入合法的 Feature 名称（例如 org.chromium.arc），添加后默认为强制开启。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customFeatureName,
                                    onValueChange = { customFeatureName = it },
                                    label = { Text("Feature 名称") },
                                    placeholder = { Text("org.chromium.arc") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addCustomFeature() }),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.size(8.dp))
                                Button(onClick = { addCustomFeature() }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("添加")
                                }
                            }
                        }
                    }
                }

                item {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "搜索 Feature 名称或说明"
                    )
                }

                FeatureCategory.entries.forEach { category ->
                    val categoryItems = grouped[category]
                    if (!categoryItems.isNullOrEmpty()) {
                        item {
                            SectionHeader(
                                title = category.title,
                                subtitle = "${categoryItems.size} 项特性"
                            )
                        }
                        items(categoryItems, key = { it.spec.name }) { feature ->
                            FeatureRuleRow(
                                item = feature,
                                decision = rules[feature.spec.name] ?: Decision.DEFAULT,
                                onDecisionChange = { setDecision(feature.spec.name, it) },
                                compact = settings.compactCards
                            )
                        }
                    }
                }

                if (grouped.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Dashboard,
                            title = "没有匹配的 Feature",
                            message = "换个关键词，或在上方添加自定义 Feature。"
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && editableCustom != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除模板") },
            text = { Text("确定删除模板「${editableCustom.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteCustomTemplate(editableCustom.id)
                    showDeleteConfirm = false
                    notify("模板已删除")
                    onBack()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
