package io.github.indusy55.featuremanager.ui.components
import androidx.compose.runtime.getValue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.indusy55.featuremanager.core.FeatureCategory
import io.github.indusy55.featuremanager.data.AppFeatureConfig
import io.github.indusy55.featuremanager.data.AppListRepository
import io.github.indusy55.featuremanager.data.Decision
import io.github.indusy55.featuremanager.data.DeviceFeatureItem
import io.github.indusy55.featuremanager.data.FeatureTemplate
import io.github.indusy55.featuremanager.data.LoadedProcessState
import io.github.indusy55.featuremanager.service.FrameworkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppIcon(packageName: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = androidx.compose.runtime.remember(context) { AppListRepository.get(context) }
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName, repo) {
        value = repo.getCachedIcon(packageName) ?: repo.loadAppIcon(packageName)
    }
    val shape = RoundedCornerShape(size / 4)
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape)
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packageName.substringAfterLast('.').take(1).uppercase(Locale.ROOT),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

enum class BadgeTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone = BadgeTone.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BadgeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BadgeTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BadgeTone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        modifier = modifier.height(28.dp),
        onClick = {},
        enabled = false,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = content,
            disabledLeadingIconContentColor = content
        ),
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDecisionSelector(
    current: Decision,
    onSelect: (Decision) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Decision.entries.forEach { decision ->
            val label = when (decision) {
                Decision.DEFAULT -> "默认"
                Decision.FORCE_TRUE -> "开启"
                Decision.FORCE_FALSE -> "关闭"
            }
            FilterChip(
                selected = current == decision,
                onClick = { onSelect(decision) },
                label = {
                    Text(
                        text = label,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (current == decision) {
                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(14.dp)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.height(if (compact) 30.dp else 34.dp)
            )
        }
    }
}

@Composable
fun FeatureRuleRow(
    item: DeviceFeatureItem,
    decision: Decision,
    onDecisionChange: (Decision) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (decision != Decision.DEFAULT) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.spec.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.spec.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.spec.category == FeatureCategory.CUSTOM) {
                    StatusBadge(text = "自定义", tone = BadgeTone.NEUTRAL)
                } else if (item.deviceAvailableByDefault) {
                    StatusBadge(text = "设备原生", tone = BadgeTone.INFO)
                }
            }
            if (!compact && item.spec.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            RuleDecisionSelector(
                current = decision,
                onSelect = onDecisionChange,
                compact = compact
            )
        }
    }
}

@Composable
fun AppScopeStatusChip(
    packageName: String,
    frameworkStatus: FrameworkStatus,
    loadedState: LoadedProcessState?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val now = System.currentTimeMillis()
    val isFreshLoaded = loadedState != null && now - loadedState.timestamp < 15 * 60 * 1000L
    val (text, tone) = when {
        isFreshLoaded -> "已生效 (v${loadedState!!.configVersion})" to BadgeTone.SUCCESS
        frameworkStatus.isProcessHooked(packageName) -> "进程运行中" to BadgeTone.INFO
        frameworkStatus.isPackageInScope(packageName) -> "已在作用域" to BadgeTone.WARNING
        else -> "未加入作用域" to BadgeTone.ERROR
    }
    StatusBadge(
        text = if (compact && text.length > 10) text.substringBefore(" (") else text,
        tone = tone,
        modifier = modifier
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() })
    )
}

@Composable
fun TemplateCard(
    template: FeatureTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = template.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = template.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (template.badge.isNotBlank()) {
                    StatusBadge(
                        text = template.badge,
                        tone = if (template.builtIn) BadgeTone.SUCCESS else BadgeTone.INFO
                    )
                }
            }
            if (template.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(text = "开启 ${template.enabledCount}", tone = BadgeTone.SUCCESS)
                StatusBadge(text = "关闭 ${template.disabledCount}", tone = BadgeTone.ERROR)
                Spacer(Modifier.weight(1f))
                if (onDuplicate != null) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Text("⧉", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除模板",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfiguredAppCard(
    packageName: String,
    label: String,
    config: AppFeatureConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadedVersion: Int? = null,
    compact: Boolean = false
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(packageName = packageName, size = if (compact) 36.dp else 48.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (config.enabledCount > 0) {
                    StatusBadge(text = "+${config.enabledCount}", tone = BadgeTone.SUCCESS, compact = true)
                }
                if (config.disabledCount > 0) {
                    StatusBadge(text = "-${config.disabledCount}", tone = BadgeTone.ERROR, compact = true)
                }
            }
            if (loadedVersion != null) {
                Spacer(Modifier.height(4.dp))
                StatusBadge(text = "已生效 v$loadedVersion", tone = BadgeTone.SUCCESS, compact = true)
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    tone: BadgeTone,
    compact: Boolean
) {
    if (compact) {
        Box(
            modifier = Modifier
                .background(
                    when (tone) {
                        BadgeTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        BadgeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = when (tone) {
                    BadgeTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                    BadgeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    } else {
        StatusBadge(text = text, tone = tone)
    }
}

fun formatLoadedTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

@Composable
fun RuleCountSummary(config: AppFeatureConfig, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusBadge(text = "强制开启 ${config.enabledCount}", tone = BadgeTone.SUCCESS)
        StatusBadge(text = "强制关闭 ${config.disabledCount}", tone = BadgeTone.ERROR)
    }
}
