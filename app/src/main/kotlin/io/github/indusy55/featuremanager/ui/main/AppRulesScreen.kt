package io.github.indusy55.featuremanager.ui.main
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.core.FeatureCategory
import io.github.indusy55.featuremanager.data.AppFeatureConfig
import io.github.indusy55.featuremanager.data.AppListRepository
import io.github.indusy55.featuremanager.data.Decision
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.service.FrameworkService
import io.github.indusy55.featuremanager.ui.components.AppIcon
import io.github.indusy55.featuremanager.ui.components.AppScopeStatusChip
import io.github.indusy55.featuremanager.ui.components.EmptyState
import io.github.indusy55.featuremanager.ui.components.FeatureRuleRow
import io.github.indusy55.featuremanager.ui.components.RuleCountSummary
import io.github.indusy55.featuremanager.ui.components.SearchField
import io.github.indusy55.featuremanager.ui.components.SectionHeader
import io.github.indusy55.featuremanager.ui.components.formatLoadedTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    packageName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { FeatureRepository.get(context) }
    val appRepo = remember(context) { AppListRepository.get(context) }
    val state by repo.stateFlow.collectAsStateWithLifecycle()
    val installedApps by appRepo.installedApps.collectAsStateWithLifecycle()
    val deviceFeatures by appRepo.deviceFeatures.collectAsStateWithLifecycle()
    val framework by FrameworkService.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { appRepo.ensureLoaded() }

    val config = remember(state.appConfigs, packageName) {
        state.appConfigs[packageName] ?: AppFeatureConfig(packageName = packageName)
    }
    val loadedState = state.loadedStates[packageName]
    val appInfo = remember(installedApps, packageName) { appRepo.resolveAppInfo(packageName) }
    val appLabel = appInfo.label.takeIf { it.isNotBlank() } ?: packageName

    var query by remember { mutableStateOf("") }
    var featureInput by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    var templateMenuExpanded by remember { mutableStateOf(false) }
    var undoRules by remember(packageName) { mutableStateOf<Map<String, Decision>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message.i18n()) }
    }

    // buildCatalogWithDeviceState() already merges FeatureCatalog.KNOWN_FEATURES with
    // device-native features and any custom keys stored in this app's rules.
    val catalog = remember(deviceFeatures, config.rules) {
        appRepo.buildCatalogWithDeviceState(extraKeys = config.rules.keys)
    }
    val groupedFeatures = remember(catalog, query) {
        val keyword = query.trim()
        catalog.asSequence()
            .filter { item ->
                keyword.isEmpty() ||
                    item.spec.name.contains(keyword, ignoreCase = true) ||
                    item.spec.label.contains(keyword, ignoreCase = true) ||
                    item.spec.category.title.contains(keyword, ignoreCase = true)
            }
            .groupBy { it.spec.category }
    }

    val customFeature = featureInput.trim()
    val customFeatureValid = ConfigContract.isValidPackageName(customFeature)

    fun requestScope() {
        if (!framework.isConnected) {
            notify("Xposed 框架未连接，无法申请作用域")
            return
        }
        FrameworkService.requestScope(listOf(packageName)) { approved, error ->
            val message = error ?: if (approved.isNotEmpty()) {
                "已将 $packageName 加入模块作用域"
            } else {
                "作用域请求未获批准"
            }
            notify(message)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "overview") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(packageName = packageName, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("规则概览", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "配置版本 v${state.revision} · 已加载 v${loadedState?.configVersion ?: "未知"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "加载时间：${formatLoadedTime(loadedState?.timestamp ?: 0L)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "应用版本：${appInfo.versionName.takeIf { it.isNotBlank() } ?: "未知"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AppScopeStatusChip(
                                    packageName = packageName,
                                    frameworkStatus = framework,
                                    loadedState = loadedState,
                                    compact = state.settings.compactCards
                                )
                            }
                            if (config.appliedTemplates.isNotEmpty()) {
                                Text(
                                    text = "已应用模板：${config.appliedTemplates.joinToString("、")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RuleCountSummary(config = config)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { requestScope() },
                                    enabled = framework.isConnected && !framework.isPackageInScope(packageName)
                                ) {
                                    Text(
                                        if (framework.isPackageInScope(packageName)) "已在作用域" else "申请作用域"
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        undoRules = config.rules
                                        repo.clearAppRules(packageName)
                                        notify("已清空 $appLabel 的规则")
                                    },
                                    enabled = config.hasActiveRules
                                ) {
                                    Text("清空规则")
                                }
                                OutlinedButton(
                                    onClick = {
                                        undoRules?.let { previous ->
                                            repo.setAppRules(packageName, previous)
                                            undoRules = null
                                            notify("已撤销上次修改")
                                        }
                                    },
                                    enabled = undoRules != null
                                ) {
                                    Text("撤销")
                                }
                            }
                        }
                    }
                }

                item(key = "search") {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "搜索 Feature 名称"
                    )
                }

                item(key = "custom_feature") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("添加自定义 Feature", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = featureInput,
                                onValueChange = { featureInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Feature 名称") },
                                placeholder = { Text("例如 org.chromium.arc") },
                                singleLine = true,
                                isError = customFeature.isNotEmpty() && !customFeatureValid
                            )
                            if (customFeature.isNotEmpty() && !customFeatureValid) {
                                Text(
                                    text = "名称格式无效，请输入包含点号的名称，例如 org.chromium.arc",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = {
                                    undoRules = config.rules
                                    repo.setDecision(packageName, customFeature, Decision.FORCE_TRUE)
                                    featureInput = ""
                                    notify("已添加并强制开启：$customFeature")
                                },
                                enabled = customFeatureValid,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("添加并强制开启")
                            }
                        }
                    }
                }

                item(key = "template") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("应用模板", style = MaterialTheme.typography.titleSmall)
                            Box {
                                OutlinedButton(
                                    onClick = { templateMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("选择模板")
                                }
                                DropdownMenu(
                                    expanded = templateMenuExpanded,
                                    onDismissRequest = { templateMenuExpanded = false }
                                ) {
                                    val templates = state.allTemplates
                                    if (templates.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("暂无可用模板") },
                                            onClick = { templateMenuExpanded = false }
                                        )
                                    } else {
                                        templates.forEach { template ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(template.name)
                                                        Text(
                                                            text = template.subtitle,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    templateMenuExpanded = false
                                                    undoRules = config.rules
                                                    repo.applyTemplatePatch(
                                                        listOf(packageName),
                                                        template,
                                                        replaceExisting
                                                    )
                                                    notify(
                                                        if (replaceExisting) {
                                                            "已替换规则并应用模板：${template.name}"
                                                        } else {
                                                            "已合并应用模板：${template.name}"
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = replaceExisting,
                                    onCheckedChange = { replaceExisting = it }
                                )
                                Text("替换当前规则（默认仅合并模板规则）")
                            }
                        }
                    }
                }

                FeatureCategory.entries.forEach { category ->
                    val featureItems = groupedFeatures[category].orEmpty()
                    if (featureItems.isNotEmpty()) {
                        item(key = "header_${category.name}") {
                            SectionHeader(
                                title = category.title,
                                subtitle = "共 ${featureItems.size} 项"
                            )
                        }
                        items(featureItems, key = { it.spec.name }) { item ->
                            FeatureRuleRow(
                                item = item,
                                decision = config.rules[item.spec.name] ?: Decision.DEFAULT,
                                onDecisionChange = { decision ->
                                    undoRules = config.rules
                                    repo.setDecision(packageName, item.spec.name, decision)
                                },
                                compact = state.settings.compactCards
                            )
                        }
                    }
                }

                if (groupedFeatures.values.all { it.isEmpty() }) {
                    item(key = "no_features") {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "没有匹配的 Feature",
                            message = "试试其他关键词，或在上方添加自定义 Feature。"
                        )
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
