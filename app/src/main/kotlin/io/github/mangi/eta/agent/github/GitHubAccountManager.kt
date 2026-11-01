package io.github.mangi.eta.agent.github

import android.content.Context
import io.github.mangi.eta.data.model.McpAuthorizationType
import io.github.mangi.eta.data.model.McpProtocolMode
import io.github.mangi.eta.data.model.McpServerSetting
import io.github.mangi.eta.data.repository.GitHubAccount
import io.github.mangi.eta.data.repository.GitHubAccountStore
import io.github.mangi.eta.data.repository.McpServerRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface GitHubAccountState {
    data object Disconnected : GitHubAccountState
    data object Connecting : GitHubAccountState
    data class Waiting(val userCode: String, val verificationUri: String, val expiresAt: Long) : GitHubAccountState
    data class Connected(
        val login: String,
        val scopes: String,
        val syncedTerminals: List<String> = emptyList(),
        val pendingTerminals: List<String> = emptyList(),
        val syncError: String? = null,
    ) : GitHubAccountState
    data class Failed(val message: String) : GitHubAccountState
}

/**
 * GitHub 账号的唯一入口：连接走设备码，凭据存 Keystore，
 * 连接成功后自动注册官方的远程 MCP 服务器（工具层直接用），并把同一份 token 同步给终端 git。
 */
internal object GitHubAccountManager {
    const val MCP_SERVER_NAME = "GitHub"
    const val MCP_SERVER_URL = "https://api.githubcopilot.com/mcp/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<GitHubAccountState>(GitHubAccountState.Disconnected)
    val state: StateFlow<GitHubAccountState> = _state.asStateFlow()

    private var store: GitHubAccountStore? = null
    private var pendingJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val store = GitHubAccountStore(appContext!!)
        this.store = store
        val account = store.load()
        _state.value = if (account == null) {
            GitHubAccountState.Disconnected
        } else {
            GitHubAccountState.Connected(login = account.login, scopes = account.scopes)
        }
    }

    fun syncToTerminalEnabled(): Boolean = store?.syncToTerminal() ?: true

    fun connect() {
        val store = store ?: return
        if (pendingJob?.isActive == true) return
        _state.value = GitHubAccountState.Connecting
        pendingJob = scope.launch {
            try {
                val code = GitHubDeviceFlow.request()
                _state.value = GitHubAccountState.Waiting(
                    userCode = code.userCode,
                    verificationUri = code.verificationUri,
                    expiresAt = code.expiresAt,
                )
                val token = GitHubDeviceFlow.awaitToken(code)
                val login = GitHubDeviceFlow.fetchLogin(token)
                val account = GitHubAccount(
                    login = login,
                    token = token,
                    scopes = GitHubDeviceFlow.SCOPES,
                    connectedAt = System.currentTimeMillis(),
                )
                store.save(account)
                registerMcpServer(token)
                val sync = if (store.syncToTerminal()) {
                    GitHubTerminalSync.sync(requireContext(), login, token)
                } else {
                    GitHubTerminalSync.Result()
                }
                _state.value = GitHubAccountState.Connected(
                    login = login,
                    scopes = account.scopes,
                    syncedTerminals = sync.synced,
                    pendingTerminals = sync.skipped,
                    syncError = sync.error,
                )
            } catch (error: Exception) {
                _state.value = GitHubAccountState.Failed(error.message ?: "连接失败")
            }
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
        val account = store?.load()
        _state.value = if (account == null) {
            GitHubAccountState.Disconnected
        } else {
            GitHubAccountState.Connected(account.login, account.scopes)
        }
    }

    fun disconnect() {
        val store = store ?: return
        pendingJob?.cancel()
        pendingJob = null
        scope.launch {
            val serverId = store.synchronizedServerId()
            if (serverId != null) {
                runCatching { McpServerRepository.delete(serverId) }
                store.setSynchronizedServerId(null)
            }
            runCatching { GitHubTerminalSync.clear(requireContext()) }
            store.clear()
            _state.value = GitHubAccountState.Disconnected
        }
    }

    fun setSyncToTerminal(enabled: Boolean) {
        val store = store ?: return
        store.setSyncToTerminal(enabled)
        val account = store.load() ?: return
        scope.launch {
            runCatching {
            val sync = if (enabled) {
                GitHubTerminalSync.sync(requireContext(), account.login, account.token)
            } else {
                GitHubTerminalSync.clear(requireContext())
            }
            _state.value = GitHubAccountState.Connected(
                login = account.login,
                scopes = account.scopes,
                syncedTerminals = sync.synced,
                pendingTerminals = sync.skipped,
                syncError = sync.error,
            )
            }.onFailure { error ->
                _state.value = GitHubAccountState.Connected(
                    login = account.login,
                    scopes = account.scopes,
                    syncError = error.message ?: "同步失败",
                )
            }
        }
    }

    /** 终端环境可能晚于账号创建（比如装完 Linux 才回来），进设置页时补一次同步。 */
    fun syncIfConfigured() {
        val store = store ?: return
        if (!store.syncToTerminal()) return
        val account = store.load() ?: return
        scope.launch {
            runCatching {
                val sync = GitHubTerminalSync.sync(requireContext(), account.login, account.token)
                _state.value = GitHubAccountState.Connected(
                    login = account.login,
                    scopes = account.scopes,
                    syncedTerminals = sync.synced,
                    pendingTerminals = sync.skipped,
                    syncError = sync.error,
                )
            }.onFailure { error ->
                _state.value = GitHubAccountState.Connected(
                    login = account.login,
                    scopes = account.scopes,
                    syncError = error.message ?: "同步失败",
                )
            }
        }
    }

    private suspend fun registerMcpServer(token: String) {
        val store = store ?: return
        val existingId = store.synchronizedServerId()
        val existing = existingId?.let { McpServerRepository.serverById(it) }
        if (existing != null) {
            McpServerRepository.update(existing.copy(enabled = true), bearerToken = token)
            return
        }
        val server = McpServerSetting(
            id = UUID.randomUUID().toString(),
            name = MCP_SERVER_NAME,
            url = MCP_SERVER_URL,
            enabled = true,
            protocolMode = McpProtocolMode.AUTO,
            authorizationType = McpAuthorizationType.BEARER,
            sortOrder = -1,
        )
        runCatching {
            val saved = McpServerRepository.add(server, token)
            store.setSynchronizedServerId(saved.id)
        }
    }

    private var appContext: Context? = null

    private fun requireContext(): Context =
        checkNotNull(appContext) { "GitHubAccountManager.init(context) 未调用" }
}
