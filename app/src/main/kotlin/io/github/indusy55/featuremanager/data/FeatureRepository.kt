package io.github.indusy55.featuremanager.data

import android.content.Context
import android.content.Intent
import io.github.indusy55.featuremanager.core.ConfigContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedConfigSnapshot(
    val packageName: String,
    val add: Array<String>,
    val remove: Array<String>,
    val version: Int,
    val hasActiveRules: Boolean
)

class FeatureRepository private constructor(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var currentState: PersistedAppState = loadFromDisk()

    private val _stateFlow = MutableStateFlow(currentState)
    val stateFlow: StateFlow<PersistedAppState> = _stateFlow.asStateFlow()

    val current: PersistedAppState
        get() = currentState

    fun getAppConfig(packageName: String): AppFeatureConfig {
        return currentState.appConfigs[packageName] ?: AppFeatureConfig(packageName = packageName)
    }

    fun resolveForProvider(packageName: String): ResolvedConfigSnapshot {
        val state = currentState
        val config = state.appConfigs[packageName]
        if (config == null || !config.hasActiveRules) {
            return ResolvedConfigSnapshot(
                packageName = packageName,
                add = emptyArray(),
                remove = emptyArray(),
                version = state.revision,
                hasActiveRules = false
            )
        }
        return ResolvedConfigSnapshot(
            packageName = packageName,
            add = config.addedFeatures.toTypedArray(),
            remove = config.removedFeatures.toTypedArray(),
            version = state.revision,
            hasActiveRules = true
        )
    }

    fun setDecision(packageName: String, featureName: String, decision: Decision) {
        val cleanFeature = featureName.trim()
        if (cleanFeature.isEmpty()) return
        mutate(bumpRevision = true, notifyChange = true) { state ->
            val existing = state.appConfigs[packageName] ?: AppFeatureConfig(packageName = packageName)
            val nextRules = existing.rules.toMutableMap()
            if (decision == Decision.DEFAULT) {
                nextRules.remove(cleanFeature)
            } else {
                nextRules[cleanFeature] = decision
            }
            val nextConfigs = state.appConfigs.toMutableMap()
            if (nextRules.isEmpty()) {
                nextConfigs.remove(packageName)
            } else {
                nextConfigs[packageName] = existing.copy(
                    rules = nextRules,
                    updatedAt = System.currentTimeMillis()
                )
            }
            state.copy(appConfigs = nextConfigs)
        }
    }

    fun setAppRules(
        packageName: String,
        rules: Map<String, Decision>,
        appliedTemplates: List<String>? = null
    ) {
        mutate(bumpRevision = true, notifyChange = true) { state ->
            val existing = state.appConfigs[packageName] ?: AppFeatureConfig(packageName = packageName)
            val cleaned = rules
                .mapKeys { it.key.trim() }
                .filter { it.key.isNotEmpty() && it.value != Decision.DEFAULT }
            val nextConfigs = state.appConfigs.toMutableMap()
            if (cleaned.isEmpty()) {
                nextConfigs.remove(packageName)
            } else {
                nextConfigs[packageName] = existing.copy(
                    rules = cleaned,
                    appliedTemplates = appliedTemplates ?: existing.appliedTemplates,
                    updatedAt = System.currentTimeMillis()
                )
            }
            state.copy(appConfigs = nextConfigs)
        }
    }

    fun clearAppRules(packageName: String) {
        mutate(bumpRevision = true, notifyChange = true) { state ->
            val nextConfigs = state.appConfigs.toMutableMap()
            nextConfigs.remove(packageName)
            state.copy(appConfigs = nextConfigs)
        }
    }

    /**
     * Applies a [FeatureTemplate] as a patch over one or more target packages.
     * - When [replaceExisting] is false (default patch mode), non-DEFAULT rules in [template]
     *   override matching features in the target app while leaving unrelated features intact.
     * - When [replaceExisting] is true, the app's rules are replaced with the template's rules.
     */
    fun applyTemplatePatch(
        packageNames: Collection<String>,
        template: FeatureTemplate,
        replaceExisting: Boolean = false
    ) {
        if (packageNames.isEmpty()) return
        val now = System.currentTimeMillis()
        mutate(bumpRevision = true, notifyChange = true) { state ->
            val nextConfigs = state.appConfigs.toMutableMap()
            for (pkg in packageNames) {
                if (!ConfigContract.isValidPackageName(pkg)) continue
                val existing = nextConfigs[pkg] ?: AppFeatureConfig(packageName = pkg)
                val mergedRules = if (replaceExisting) {
                    mutableMapOf()
                } else {
                    existing.rules.toMutableMap()
                }
                for ((feat, decision) in template.rules) {
                    val key = feat.trim()
                    if (key.isEmpty()) continue
                    if (decision == Decision.DEFAULT) {
                        mergedRules.remove(key)
                    } else {
                        mergedRules[key] = decision
                    }
                }
                val nextTemplates = if (replaceExisting) {
                    listOf(template.name)
                } else {
                    (existing.appliedTemplates + template.name).distinct().takeLast(4)
                }
                if (mergedRules.isEmpty()) {
                    nextConfigs.remove(pkg)
                } else {
                    nextConfigs[pkg] = existing.copy(
                        rules = mergedRules,
                        appliedTemplates = nextTemplates,
                        updatedAt = now
                    )
                }
            }
            state.copy(appConfigs = nextConfigs)
        }
    }

    fun saveCustomTemplate(template: FeatureTemplate) {
        val cleanedRules = template.rules
            .mapKeys { it.key.trim() }
            .filter { it.key.isNotEmpty() && it.value != Decision.DEFAULT }
        val normalized = template.copy(
            name = template.name.trim().ifEmpty { "未命名模板" },
            subtitle = template.subtitle.trim().ifEmpty { "自定义规则集" },
            rules = cleanedRules,
            builtIn = false
        )
        mutate(bumpRevision = true, notifyChange = false) { state ->
            val list = state.customTemplates.toMutableList()
            val index = list.indexOfFirst { it.id == normalized.id }
            if (index >= 0) {
                list[index] = normalized
            } else {
                list.add(normalized)
            }
            state.copy(customTemplates = list)
        }
    }

    fun deleteCustomTemplate(templateId: String) {
        mutate(bumpRevision = true, notifyChange = false) { state ->
            state.copy(
                customTemplates = state.customTemplates.filterNot { it.id == templateId }
            )
        }
    }

    fun duplicateAsCustomTemplate(source: FeatureTemplate): FeatureTemplate {
        val copy = FeatureTemplate(
            id = "custom_${System.currentTimeMillis()}",
            name = "${source.name} (副本)",
            subtitle = source.subtitle,
            description = source.description,
            rules = LinkedHashMap(source.rules),
            builtIn = false,
            badge = "自定义"
        )
        saveCustomTemplate(copy)
        return copy
    }

    fun updateSettings(transform: (AppearanceSettings) -> AppearanceSettings) {
        mutate(bumpRevision = false, notifyChange = false) { state ->
            state.copy(settings = transform(state.settings))
        }
    }

    fun markConfigLoaded(loaded: LoadedProcessState) {
        mutate(bumpRevision = false, notifyChange = false) { state ->
            val nextLoaded = state.loadedStates.toMutableMap()
            nextLoaded[loaded.packageName] = loaded
            state.copy(loadedStates = nextLoaded)
        }
    }

    fun exportToJsonString(): String {
        return serializeState(currentState).toString(2)
    }

    fun importFromJsonString(rawJson: String): Result<Int> {
        return runCatching {
            val parsed = deserializeState(JSONObject(rawJson))
            mutate(bumpRevision = true, notifyChange = true) { current ->
                current.copy(
                    appConfigs = current.appConfigs + parsed.appConfigs,
                    customTemplates = (current.customTemplates + parsed.customTemplates)
                        .distinctBy { it.id }
                )
            }
            parsed.appConfigs.size
        }
    }

    private fun mutate(
        bumpRevision: Boolean,
        notifyChange: Boolean,
        block: (PersistedAppState) -> PersistedAppState
    ) {
        val updated = synchronized(lock) {
            val candidate = block(currentState)
            val withRev = if (bumpRevision) {
                candidate.copy(revision = currentState.revision + 1)
            } else {
                candidate
            }
            saveToDiskLocked(withRev)
            currentState = withRev
            withRev
        }
        _stateFlow.value = updated
        if (notifyChange) {
            runCatching {
                appContext.sendBroadcast(
                    Intent(ConfigContract.ACTION_CONFIG_CHANGED).apply {
                        putExtra(ConfigContract.EXTRA_VERSION, updated.revision)
                    }
                )
            }
        }
    }

    private fun loadFromDisk(): PersistedAppState {
        val raw = prefs.getString(KEY_STATE_JSON, null) ?: return PersistedAppState()
        return runCatching { deserializeState(JSONObject(raw)) }
            .getOrElse { PersistedAppState() }
    }

    private fun saveToDiskLocked(state: PersistedAppState) {
        val serialized = serializeState(state).toString()
        prefs.edit().putString(KEY_STATE_JSON, serialized).commit()
    }

    private fun serializeState(state: PersistedAppState): JSONObject {
        val root = JSONObject()
        root.put("revision", state.revision)

        val appsObj = JSONObject()
        for ((pkg, cfg) in state.appConfigs) {
            if (!cfg.hasActiveRules) continue
            val cfgObj = JSONObject()
            val rulesObj = JSONObject()
            for ((feat, dec) in cfg.rules) {
                if (dec != Decision.DEFAULT) {
                    rulesObj.put(feat, dec.name)
                }
            }
            cfgObj.put("rules", rulesObj)
            cfgObj.put("appliedTemplates", JSONArray(cfg.appliedTemplates))
            cfgObj.put("updatedAt", cfg.updatedAt)
            appsObj.put(pkg, cfgObj)
        }
        root.put("appConfigs", appsObj)

        val templatesArr = JSONArray()
        for (tpl in state.customTemplates) {
            val tplObj = JSONObject()
            tplObj.put("id", tpl.id)
            tplObj.put("name", tpl.name)
            tplObj.put("subtitle", tpl.subtitle)
            tplObj.put("description", tpl.description)
            tplObj.put("badge", tpl.badge)
            val rulesObj = JSONObject()
            for ((feat, dec) in tpl.rules) {
                if (dec != Decision.DEFAULT) {
                    rulesObj.put(feat, dec.name)
                }
            }
            tplObj.put("rules", rulesObj)
            templatesArr.put(tplObj)
        }
        root.put("customTemplates", templatesArr)

        val settingsObj = JSONObject().apply {
            put("themeMode", state.settings.themeMode.name)
            put("dynamicColor", state.settings.dynamicColor)
            put("pureBlackDark", state.settings.pureBlackDark)
            put("accentStyle", state.settings.accentStyle.name)
            put("homeGridColumns", state.settings.homeGridColumns)
            put("compactCards", state.settings.compactCards)
            put("showSystemAppsByDefault", state.settings.showSystemAppsByDefault)
            put("autoRequestScopeOnSave", state.settings.autoRequestScopeOnSave)
        }
        root.put("settings", settingsObj)

        val loadedObj = JSONObject()
        for ((pkg, l) in state.loadedStates) {
            loadedObj.put(
                pkg,
                JSONObject().apply {
                    put("processName", l.processName)
                    put("configVersion", l.configVersion)
                    put("timestamp", l.timestamp)
                    put("addedCount", l.addedCount)
                    put("removedCount", l.removedCount)
                }
            )
        }
        root.put("loadedStates", loadedObj)
        return root
    }

    private fun deserializeState(root: JSONObject): PersistedAppState {
        val revision = root.optInt("revision", 1).coerceAtLeast(1)

        val appConfigs = LinkedHashMap<String, AppFeatureConfig>()
        val appsObj = root.optJSONObject("appConfigs")
        if (appsObj != null) {
            val keys = appsObj.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val cfgObj = appsObj.optJSONObject(pkg) ?: continue
                val rulesObj = cfgObj.optJSONObject("rules")
                val rules = LinkedHashMap<String, Decision>()
                if (rulesObj != null) {
                    val featKeys = rulesObj.keys()
                    while (featKeys.hasNext()) {
                        val feat = featKeys.next()
                        val decName = rulesObj.optString(feat)
                        val dec = runCatching { Decision.valueOf(decName) }
                            .getOrDefault(Decision.DEFAULT)
                        if (dec != Decision.DEFAULT) {
                            rules[feat] = dec
                        }
                    }
                }
                val appliedTemplates = mutableListOf<String>()
                val tplArr = cfgObj.optJSONArray("appliedTemplates")
                if (tplArr != null) {
                    for (i in 0 until tplArr.length()) {
                        val name = tplArr.optString(i)
                        if (name.isNotBlank()) appliedTemplates.add(name)
                    }
                }
                if (rules.isNotEmpty()) {
                    appConfigs[pkg] = AppFeatureConfig(
                        packageName = pkg,
                        rules = rules,
                        appliedTemplates = appliedTemplates,
                        updatedAt = cfgObj.optLong("updatedAt", 0L)
                    )
                }
            }
        }

        val customTemplates = mutableListOf<FeatureTemplate>()
        val templatesArr = root.optJSONArray("customTemplates")
        if (templatesArr != null) {
            for (i in 0 until templatesArr.length()) {
                val tplObj = templatesArr.optJSONObject(i) ?: continue
                val id = tplObj.optString("id").ifBlank { "custom_$i" }
                val name = tplObj.optString("name").ifBlank { "自定义模板" }
                val subtitle = tplObj.optString("subtitle", "自定义规则集")
                val description = tplObj.optString("description", "")
                val badge = tplObj.optString("badge", "自定义")
                val rulesObj = tplObj.optJSONObject("rules")
                val rules = LinkedHashMap<String, Decision>()
                if (rulesObj != null) {
                    val featKeys = rulesObj.keys()
                    while (featKeys.hasNext()) {
                        val feat = featKeys.next()
                        val dec = runCatching { Decision.valueOf(rulesObj.optString(feat)) }
                            .getOrDefault(Decision.DEFAULT)
                        if (dec != Decision.DEFAULT) {
                            rules[feat] = dec
                        }
                    }
                }
                customTemplates.add(
                    FeatureTemplate(
                        id = id,
                        name = name,
                        subtitle = subtitle,
                        description = description,
                        rules = rules,
                        builtIn = false,
                        badge = badge
                    )
                )
            }
        }

        val settingsObj = root.optJSONObject("settings")
        val settings = if (settingsObj != null) {
            AppearanceSettings(
                themeMode = runCatching {
                    ThemeMode.valueOf(settingsObj.optString("themeMode", ThemeMode.SYSTEM.name))
                }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = settingsObj.optBoolean("dynamicColor", true),
                pureBlackDark = settingsObj.optBoolean("pureBlackDark", false),
                accentStyle = runCatching {
                    AccentStyle.valueOf(settingsObj.optString("accentStyle", AccentStyle.INDIGO.name))
                }.getOrDefault(AccentStyle.INDIGO),
                homeGridColumns = settingsObj.optInt("homeGridColumns", 2).coerceIn(1, 4),
                compactCards = settingsObj.optBoolean("compactCards", false),
                showSystemAppsByDefault = settingsObj.optBoolean("showSystemAppsByDefault", false),
                autoRequestScopeOnSave = settingsObj.optBoolean("autoRequestScopeOnSave", true)
            )
        } else {
            AppearanceSettings()
        }

        val loadedStates = LinkedHashMap<String, LoadedProcessState>()
        val loadedObj = root.optJSONObject("loadedStates")
        if (loadedObj != null) {
            val keys = loadedObj.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val item = loadedObj.optJSONObject(pkg) ?: continue
                loadedStates[pkg] = LoadedProcessState(
                    packageName = pkg,
                    processName = item.optString("processName", pkg),
                    configVersion = item.optInt("configVersion", 0),
                    timestamp = item.optLong("timestamp", 0L),
                    addedCount = item.optInt("addedCount", 0),
                    removedCount = item.optInt("removedCount", 0)
                )
            }
        }

        return PersistedAppState(
            revision = revision,
            appConfigs = appConfigs,
            customTemplates = customTemplates,
            settings = settings,
            loadedStates = loadedStates
        )
    }

    companion object {
        private const val PREFS_NAME = "feature_manager_store"
        private const val KEY_STATE_JSON = "persisted_state_v1"

        @Volatile
        private var instance: FeatureRepository? = null

        fun get(context: Context): FeatureRepository {
            return instance ?: synchronized(this) {
                instance ?: FeatureRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
