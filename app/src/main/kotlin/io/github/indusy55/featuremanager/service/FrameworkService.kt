package io.github.indusy55.featuremanager.service

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class HookedProcessSummary(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val stateName: String,
    val loadedVersionCode: Long
)

data class FrameworkStatus(
    val isConnected: Boolean = false,
    val frameworkName: String = "未连接",
    val frameworkVersion: String = "-",
    val frameworkVersionCode: Long = 0L,
    val apiVersion: Int = 0,
    val frameworkProperties: Long = 0L,
    val scopePackages: Set<String> = emptySet(),
    val runningProcesses: List<HookedProcessSummary> = emptyList(),
    val lastScopeMessage: String? = null
) {
    fun isPackageInScope(packageName: String): Boolean {
        return scopePackages.contains(packageName)
    }

    fun isProcessHooked(packageName: String): Boolean {
        return runningProcesses.any {
            it.processName == packageName || it.processName.startsWith("$packageName:")
        }
    }
}

/**
 * Manages the connection to the modern libxposed service (API 102) to inspect
 * whether the module is active, check which apps are in scope, request/remove scope,
 * and inspect running hooked processes.
 */
object FrameworkService {

    private val registered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var xposedService: XposedService? = null

    private val _status = MutableStateFlow(FrameworkStatus())
    val status: StateFlow<FrameworkStatus> = _status.asStateFlow()

    fun registerOnce() {
        if (!registered.compareAndSet(false, true)) return
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    xposedService = service
                    refreshStatus()
                }

                override fun onServiceDied(service: XposedService) {
                    if (xposedService === service) {
                        xposedService = null
                    }
                    _status.value = FrameworkStatus(
                        isConnected = false,
                        lastScopeMessage = "Xposed 框架服务连接已断开"
                    )
                }
            })
        }
    }

    fun refreshStatus(messageOverride: String? = null) {
        val service = xposedService
        if (service == null) {
            _status.value = _status.value.copy(
                isConnected = false,
                lastScopeMessage = messageOverride ?: _status.value.lastScopeMessage
            )
            return
        }

        runCatching {
            val api = service.apiVersion
            val name = service.frameworkName
            val version = service.frameworkVersion
            val versionCode = service.frameworkVersionCode
            val props = service.frameworkProperties
            val scope = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
            val running = if (api >= XposedService.API_102) {
                runCatching {
                    service.runningTargets.map { target: HookedTarget ->
                        HookedProcessSummary(
                            pid = target.pid,
                            uid = target.uid,
                            processName = target.processName,
                            stateName = target.state.name,
                            loadedVersionCode = target.loadedVersionCode
                        )
                    }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            _status.value = FrameworkStatus(
                isConnected = true,
                frameworkName = name,
                frameworkVersion = version,
                frameworkVersionCode = versionCode,
                apiVersion = api,
                frameworkProperties = props,
                scopePackages = scope,
                runningProcesses = running,
                lastScopeMessage = messageOverride ?: _status.value.lastScopeMessage
            )
        }.onFailure { err ->
            _status.value = _status.value.copy(
                lastScopeMessage = "读取框架状态失败: ${err.message ?: err.javaClass.simpleName}"
            )
        }
    }

    fun requestScope(
        packages: List<String>,
        onComplete: ((approved: List<String>, error: String?) -> Unit)? = null
    ) {
        val clean = packages.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return
        val service = xposedService
        if (service == null) {
            val msg = "Xposed 框架未激活或服务未连接，请在管理器中启用本模块并勾选作用域"
            _status.value = _status.value.copy(lastScopeMessage = msg)
            onComplete?.invoke(emptyList(), msg)
            return
        }

        runCatching {
            service.requestScope(clean, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    mainHandler.post {
                        val msg = if (approved.isEmpty()) {
                            "作用域请求未获批准"
                        } else {
                            "已将 ${approved.size} 个应用加入模块作用域"
                        }
                        refreshStatus(messageOverride = msg)
                        onComplete?.invoke(approved, null)
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    mainHandler.post {
                        val msg = "请求作用域失败: $message"
                        refreshStatus(messageOverride = msg)
                        onComplete?.invoke(emptyList(), message)
                    }
                }
            })
        }.onFailure { e ->
            val msg = "请求作用域异常: ${e.message ?: e.javaClass.simpleName}"
            _status.value = _status.value.copy(lastScopeMessage = msg)
            onComplete?.invoke(emptyList(), msg)
        }
    }

    fun removeScope(packages: List<String>, onComplete: ((String?) -> Unit)? = null) {
        val clean = packages.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return
        val service = xposedService ?: run {
            onComplete?.invoke("Xposed 服务未连接")
            return
        }
        runCatching {
            service.removeScope(clean)
            refreshStatus(messageOverride = "已从模块作用域移除 ${clean.size} 个应用")
            onComplete?.invoke(null)
        }.onFailure { e ->
            val msg = "移除作用域失败: ${e.message}"
            refreshStatus(messageOverride = msg)
            onComplete?.invoke(msg)
        }
    }
}
