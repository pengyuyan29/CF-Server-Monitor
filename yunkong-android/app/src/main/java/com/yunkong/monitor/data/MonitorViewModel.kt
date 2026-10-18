package com.yunkong.monitor.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.WebSocket
import java.io.IOException

data class Stats(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val speedIn: Double = 0.0,
    val speedOut: Double = 0.0
)

enum class Conn { NONE, CONNECTING, LIVE, POLLING }

data class UiState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val config: JsonObject? = null,
    val servers: List<ServerState> = emptyList(),
    val stats: Stats = Stats(),
    val conn: Conn = Conn.NONE,
    val error: String? = null,
    val siteTitle: String = "云控",
    val baseUrl: String = "",
    val privateSite: Boolean = false,
    val loggedIn: Boolean = false
)

data class DetailUi(
    val id: String = "",
    val server: ServerState? = null,
    val hours: Double = 24.0,
    val history: List<JsonObject> = emptyList(),
    val loading: Boolean = true,
    val historyLoading: Boolean = true,
    val error: String? = null
)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREF = "yunkong"
        private const val KEY_BASE = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_THEME = "theme_mode" // 0 system / 1 light / 2 dark
    }

    private val prefs = app.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var api: ApiClient? = null
    private var ws: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var wsAttempts = 0

    /** 保持 /api/servers 返回顺序的服务器状态表 */
    private val serverMap = LinkedHashMap<String, ServerState>()

    private val _ui = MutableStateFlow(UiState(configured = prefs.getString(KEY_BASE, null) != null))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow(DetailUi())
    val detail: StateFlow<DetailUi> = _detail.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME, 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    init {
        val saved = prefs.getString(KEY_BASE, null)
        if (saved != null) {
            api = ApiClient(saved, prefs.getString(KEY_TOKEN, null))
            _ui.value = _ui.value.copy(baseUrl = saved)
            connectInternal()
        }
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        prefs.edit().putInt(KEY_THEME, mode).apply()
    }

    /** 首次配置 / 更换地址 */
    fun connect(url: String, username: String? = null, password: String? = null) {
        val clean = url.trim()
        if (clean.isEmpty()) {
            _ui.value = _ui.value.copy(error = "请输入面板地址")
            return
        }
        val withScheme = if (!clean.startsWith("http://") && !clean.startsWith("https://")) "https://$clean" else clean
        _ui.value = _ui.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val client = ApiClient(withScheme)
                val config = client.getConfig()
                val isPublic = config.bool("is_public") ?: true
                var token: String? = prefs.getString(KEY_TOKEN, null)
                client.token = token
                if (!isPublic) {
                    if (username != null && password != null && username.isNotBlank()) {
                        token = client.login(username, password)
                    } else if (token == null) {
                        withContext(Dispatchers.Main) {
                            _ui.value = _ui.value.copy(
                                loading = false,
                                error = "该站点为私有站点，请展开「私有站点登录」并填写管理账号"
                            )
                        }
                        return@launch
                    }
                }
                val savedBase = prefs.getString(KEY_BASE, null)
                if (savedBase != withScheme) token = null
                client.token = token
                api = client
                prefs.edit().putString(KEY_BASE, withScheme).putString(KEY_TOKEN, token).apply()
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        configured = true, loading = false, error = null,
                        baseUrl = withScheme,
                        privateSite = !isPublic, loggedIn = token != null
                    )
                }
                connectInternal()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(loading = false, error = friendly(e))
                }
            }
        }
    }

    /** 私有站点登录 */
    fun login(username: String, password: String) {
        val client = api ?: return
        _ui.value = _ui.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val token = client.login(username, password)
                prefs.edit().putString(KEY_TOKEN, token).apply()
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(loading = false, loggedIn = true, error = null)
                }
                refreshInternal()
                restartWs()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(loading = false, error = friendly(e))
                }
            }
        }
    }

    fun logout() {
        prefs.edit().putString(KEY_TOKEN, null).apply()
        api?.token = null
        _ui.value = _ui.value.copy(loggedIn = false)
        scope.launch {
            refreshInternal()
            restartWs()
        }
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        scope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val client = api ?: return
        try {
            val resp = client.getServers()
            synchronized(serverMap) {
                serverMap.clear()
                for (s in resp.array("servers") ?: JsonArray(emptyList())) {
                    val obj = s as? JsonObject ?: continue
                    val id = obj.str("id") ?: continue
                    serverMap[id] = obj
                }
            }
            emitServers()
        } catch (e: Exception) {
            if (e is ApiException && e.code == 401) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(loading = false, error = friendly(e), loggedIn = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(loading = false, error = friendly(e))
                }
            }
        }
    }

    private fun emitServers() {
        val list: List<ServerState>
        synchronized(serverMap) { list = serverMap.values.toList() }
        val now = System.currentTimeMillis()
        var online = 0
        var speedIn = 0.0
        var speedOut = 0.0
        for (s in list) {
            val last = s.long("last_updated") ?: 0
            val isOn = if (last > 0) now - last <= 300_000 else (s.bool("is_online") ?: false)
            if (isOn) {
                online++
                speedIn += s.num("net_in_speed") ?: 0.0
                speedOut += s.num("net_out_speed") ?: 0.0
            }
        }
        val stats = Stats(
            total = list.size, online = online, offline = list.size - online,
            speedIn = speedIn, speedOut = speedOut
        )
        _ui.value = _ui.value.copy(servers = list, stats = stats, loading = false, conn = _ui.value.conn)
    }

    // ---------- WebSocket ----------

    private fun connectInternal() {
        _ui.value = _ui.value.copy(
            loading = true, error = null, conn = Conn.CONNECTING,
            baseUrl = api?.baseUrl ?: _ui.value.baseUrl
        )
        scope.launch {
            val client = api ?: return@launch
            try {
                val config = client.getConfig()
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        config = config,
                        siteTitle = config.str("site_title")?.takeIf { it.isNotBlank() } ?: "云控",
                        privateSite = !(config.bool("is_public") ?: true)
                    )
                }
            } catch (e: Exception) {
                // 配置读取失败不阻断主流程
            }
            refreshInternal()
        }
        startPolling()
        restartWs()
    }

    fun restartWs() {
        val client = api ?: return
        ws?.close(1000, null)
        ws = null
        reconnectJob?.cancel()
        val req = client.wsRequest()
        ws = client.http.newWebSocket(req, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                wsAttempts = 0
                val ids: List<String> = synchronized(serverMap) { ArrayList(serverMap.keys) }
                val subscribe = "{\"type\":\"subscribe\",\"scope\":\"all\",\"ids\":" +
                    ids.joinToString(",", "[", "]") { s -> "\"" + s.replace("\"", "") + "\"" } + "}"
                webSocket.send(subscribe)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = API_JSON.parseToJsonElement(text) as? JsonObject ?: return
                    when (msg.str("type")) {
                        "hello", "subscribed" -> {
                            _ui.value = _ui.value.copy(conn = Conn.LIVE)
                        }
                        "batchUpdate" -> handleBatch(msg)
                    }
                } catch (e: Exception) {
                    // 忽略解析失败的消息
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect()
            }
        })
    }

    private fun handleBatch(msg: JsonObject) {
        val updates = msg.array("updates") ?: return
        var changed = false
        synchronized(serverMap) {
            for (u in updates) {
                val obj = u as? JsonObject ?: continue
                val serverId = obj.str("serverId") ?: continue
                val current = serverMap[serverId] ?: continue
                var merged = current
                for (sample in obj.array("samples") ?: JsonArray(emptyList())) {
                    val s = sample as? JsonObject ?: continue
                    val data = s.obj("data") ?: continue
                    merged = mergeSample(merged, data)
                }
                if (merged != current) {
                    serverMap[serverId] = merged
                    changed = true
                }
            }
        }
        if (changed) emitServers()
    }

    private fun scheduleReconnect() {
        _ui.value = _ui.value.copy(conn = Conn.POLLING)
        if (api == null) return
        reconnectJob?.cancel()
        val delayMs = minOf(5000L * (wsAttempts + 1), 30000L)
        wsAttempts++
        reconnectJob = scope.launch {
            delay(delayMs)
            restartWs()
        }
    }

    /** WS 不可用时的轮询兜底 */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(15_000)
                if (_ui.value.conn != Conn.LIVE) {
                    refreshInternal()
                }
            }
        }
    }

    // ---------- 详情 ----------

    fun openDetail(id: String) {
        _detail.value = DetailUi(id = id)
        scope.launch {
            try {
                val client = api ?: throw IOException("未连接")
                val s = client.getServer(id)
                _detail.value = _detail.value.copy(server = s, loading = false)
            } catch (e: Exception) {
                _detail.value = _detail.value.copy(loading = false, error = friendly(e))
            }
        }
        loadHistory(id, _detail.value.hours)
    }

    fun setHours(hours: Double) {
        val id = _detail.value.id
        _detail.value = _detail.value.copy(hours = hours)
        loadHistory(id, hours)
    }

    private fun loadHistory(id: String, hours: Double) {
        if (id.isEmpty()) return
        _detail.value = _detail.value.copy(historyLoading = true)
        scope.launch {
            try {
                val client = api ?: throw IOException("未连接")
                val arr = client.getHistory(id, hours)
                val rows = arr.mapNotNull { it as? JsonObject }
                    .sortedBy { it.long("timestamp") ?: 0L }
                _detail.value = _detail.value.copy(history = rows, historyLoading = false)
            } catch (e: Exception) {
                _detail.value = _detail.value.copy(historyLoading = false, error = friendly(e))
            }
        }
    }

    fun clearDetailError() {
        _detail.value = _detail.value.copy(error = null)
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "请求失败 (${e.code})"
        is IOException -> "网络连接失败：${e.message ?: "请检查地址与网络"}"
        else -> "出错了：${e.message ?: e.javaClass.simpleName}"
    }

    override fun onCleared() {
        super.onCleared()
        ws?.close(1000, null)
        reconnectJob?.cancel()
        pollJob?.cancel()
    }
}
