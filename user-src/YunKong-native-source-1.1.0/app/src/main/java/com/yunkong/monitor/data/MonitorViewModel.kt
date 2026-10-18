package com.yunkong.monitor.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.WebSocket
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val loggedIn: Boolean = false,
    val liveExpired: Boolean = false,
    val needTurnstile: Boolean = false,
    val turnstileMode: String = "api" // api: 全局访问凭证 / login: 登录一次性 token
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
        private const val KEY_TS_VERIFIED = "ts_verified"
        private const val KEY_TS_EXPIRES = "ts_expires"
        private const val KEY_TS_BASE = "ts_base"
    }

    private val prefs = app.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var historyJob: Job? = null
    @Volatile private var connectionVersion = 0
    @Volatile private var monitoringActive = true
    @Volatile private var wsOpenedAt = 0L
    private var livePaused = false

    private var api: ApiClient? = null
    private var ws: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var wsAttempts = 0

    // Turnstile 待续流程
    private var pendingUrl: String? = null
    private var pendingUser: String? = null
    private var pendingPass: String? = null
    private var pendingLoginToken: String? = null
    private var lastTurnstileDismiss = 0L

    /** 保持 /api/servers 返回顺序的服务器状态表 */
    private val serverMap = LinkedHashMap<String, ServerState>()

    private val _ui = MutableStateFlow(UiState(configured = prefs.getString(KEY_BASE, null) != null))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow(DetailUi())
    val detail: StateFlow<DetailUi> = _detail.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME, 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    private val _compatibleGlass = MutableStateFlow(prefs.getBoolean("compatible_glass", false))
    val compatibleGlass = _compatibleGlass.asStateFlow()
    fun setCompatibleGlass(value: Boolean) { _compatibleGlass.value = value; prefs.edit().putBoolean("compatible_glass", value).apply() }

    init {
        val saved = prefs.getString(KEY_BASE, null)
        if (saved != null) {
            val client = ApiClient(saved, prefs.getString(KEY_TOKEN, null))
            restoreTurnstile(client)
            api = client
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
        val withScheme = try {
            val candidate = (if (!clean.startsWith("http://") && !clean.startsWith("https://")) "https://$clean" else clean).toHttpUrl()
            require(candidate.username.isBlank() && candidate.password.isBlank() && candidate.query == null && candidate.fragment == null)
            candidate.toString().trimEnd('/')
        } catch (_: Exception) { _ui.value = _ui.value.copy(error = "请输入有效的面板地址，不要附带凭据或查询参数"); return }
        _ui.value = _ui.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val client = ApiClient(withScheme)
                restoreTurnstile(client)
                val config = client.getConfig()
                val isPublic = config.bool("is_public") ?: true
                val tsEnabled = config.bool("turnstile_enabled") ?: false
                val tsLoginEnabled = config.bool("turnstile_login_enabled") ?: false
                var token: String? = if (prefs.getString(KEY_BASE, null) == withScheme) prefs.getString(KEY_TOKEN, null) else null
                client.token = token

                // 全局人机验证：所有 /api/* 需要有效凭证
                if (tsEnabled && !client.hasValidTurnstile()) {
                    pendingUrl = withScheme; pendingUser = username; pendingPass = password
                    api = client
                    _ui.value = _ui.value.copy(
                        loading = false, needTurnstile = true, turnstileMode = "api",
                        config = config, error = "该站点开启了人机验证，请先完成验证"
                    )
                    return@launch
                }
                if (!isPublic || !username.isNullOrBlank()) {
                    if (username != null && password != null && username.isNotBlank()) {
                        val loginToken = pendingLoginToken
                        if ((tsEnabled || tsLoginEnabled) && loginToken == null) {
                            pendingUrl = withScheme; pendingUser = username; pendingPass = password
                            api = client
                            _ui.value = _ui.value.copy(
                                loading = false, needTurnstile = true, turnstileMode = "login",
                                config = config, error = "登录需要先完成人机验证"
                            )
                            return@launch
                        }
                        token = client.login(username, password, loginToken)
                        pendingLoginToken = null
                    } else if (token == null) {
                        _ui.value = _ui.value.copy(
                            loading = false,
                            error = "该站点为私有站点，请展开「私有站点登录」并填写管理账号"
                        )
                        return@launch
                    }
                }
                client.token = token
                api = client
                prefs.edit().putString(KEY_BASE, withScheme).putString(KEY_TOKEN, token).apply()
                pendingUrl = null; pendingUser = null; pendingPass = null
                _ui.value = _ui.value.copy(
                    configured = true, loading = false, error = null,
                    baseUrl = withScheme,
                    privateSite = !isPublic, loggedIn = token != null,
                    needTurnstile = false
                )
                connectInternal()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    private fun restoreTurnstile(client: ApiClient) {
        if ((prefs.getString(KEY_TS_BASE, null) ?: prefs.getString(KEY_BASE, null)) != client.baseUrl) return
        val blob = prefs.getString(KEY_TS_VERIFIED, null) ?: return
        val exp = prefs.getLong(KEY_TS_EXPIRES, 0L)
        if (System.currentTimeMillis() < exp) {
            client.turnstileVerified = blob
            client.turnstileVerifiedExpires = exp
        }
    }

    /** 人机验证完成（WebView 回调） */
    fun submitTurnstileToken(token: String) {
        val mode = _ui.value.turnstileMode
        scope.launch {
            try {
                val client = api ?: throw IOException("未连接")
                if (mode == "login") {
                    pendingLoginToken = token
                } else {
                    val cfg = client.exchangeTurnstile(token)
                    val blob = cfg.str("turnstile_verified")
                    if (cfg.bool("verified") != true || blob == null) {
                        throw IOException("人机验证未通过，请重试")
                    }
                    val exp = System.currentTimeMillis() + 3_600_000
                    client.turnstileVerified = blob
                    client.turnstileVerifiedExpires = exp
                    prefs.edit().putString(KEY_TS_VERIFIED, blob).putLong(KEY_TS_EXPIRES, exp).putString(KEY_TS_BASE, client.baseUrl).apply()
                }
                _ui.value = _ui.value.copy(needTurnstile = false, error = null)
                val url = pendingUrl
                if (url != null) connect(url, pendingUser, pendingPass)
                else if (mode != "login") refreshInternal()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(needTurnstile = false, loading = false, error = friendly(e))
            }
        }
    }

    /** 用户取消验证（离开验证页），60 秒内不再自动弹出 */
    fun dismissTurnstile() {
        lastTurnstileDismiss = System.currentTimeMillis()
        pendingLoginToken = null
        _ui.value = _ui.value.copy(needTurnstile = false, loading = false)
    }

    fun ensureTurnstileConfig() {
        if (_ui.value.config != null) return
        val client = api ?: return
        scope.launch {
            try {
                val cfg = client.getConfig()
                _ui.value = _ui.value.copy(config = cfg)
            } catch (_: Exception) {
            }
        }
    }

    fun baseUrlForWebView(): String =
        (api?.baseUrl ?: _ui.value.baseUrl).trimEnd('/') + "/"

    /** 私有站点登录 */
    fun login(username: String, password: String) {
        connect(api?.baseUrl ?: _ui.value.baseUrl, username, password)
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

    fun pauseMonitoring() {
        monitoringActive = false
        connectionVersion++
        reconnectJob?.cancel(); pollJob?.cancel()
        ws?.close(1000, null); ws = null
    }

    fun resumeMonitoring() {
        if (monitoringActive) return
        monitoringActive = true
        if (api != null) { refresh(); startPolling(); if (!livePaused) restartWs() }
    }

    fun continueLive() { livePaused = false; _ui.value = _ui.value.copy(liveExpired = false); restartWs() }
    fun dismissLivePrompt() { _ui.value = _ui.value.copy(liveExpired = false) }

    private suspend fun refreshInternal() {
        val client = api ?: return
        try {
            val resp = client.getServers()
            if (client !== api) return
            synchronized(serverMap) {
                serverMap.clear()
                for (s in resp.array("servers") ?: JsonArray(emptyList())) {
                    val obj = s as? JsonObject ?: continue
                    val id = obj.str("id") ?: continue
                    serverMap[id] = obj
                }
            }
            emitServers()
            subscribeServers()
        } catch (e: Exception) {
            if (e is ApiException && e.code == 403 && e.message.orEmpty().contains("turnstile", true)
                && System.currentTimeMillis() - lastTurnstileDismiss > 60_000
            ) {
                api?.turnstileVerified = null
                prefs.edit().remove(KEY_TS_VERIFIED).remove(KEY_TS_EXPIRES).apply()
                _ui.value = _ui.value.copy(
                    loading = false, error = "人机验证已过期，请重新完成验证",
                    needTurnstile = true, turnstileMode = "api"
                )
            } else if (e is ApiException && e.code == 401) {
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
        _ui.value = _ui.value.copy(servers = list, stats = stats, loading = false, error = null)
        list.firstOrNull { it.str("id") == _detail.value.id }?.let { live ->
            _detail.value = _detail.value.copy(server = _detail.value.server?.let { mergeSample(it, live) } ?: live)
        }
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
                if (client !== api) return@launch
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        config = config,
                        siteTitle = config.str("site_title")?.takeIf { it.isNotBlank() } ?: "云控",
                        privateSite = !(config.bool("is_public") ?: true),
                        loggedIn = client.token != null
                    )
                }
            } catch (e: Exception) {
                // 配置读取失败不阻断主流程
            }
            refreshInternal()
        }
        if (monitoringActive) { startPolling(); livePaused = false; restartWs() }
    }

    fun restartWs() {
        if (!monitoringActive || livePaused) return
        val client = api ?: return
        val version = ++connectionVersion
        ws?.close(1000, null)
        ws = null
        reconnectJob?.cancel()
        val req = client.wsRequest()
        ws = client.http.newWebSocket(req, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                if (version != connectionVersion) { webSocket.close(1000, null); return }
                wsAttempts = 0
                wsOpenedAt = System.currentTimeMillis()
                subscribeServers(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (version != connectionVersion) return
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
                if (version == connectionVersion) scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (version == connectionVersion) scheduleReconnect()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        })
    }

    private fun subscribeServers(socket: WebSocket? = ws) {
        val ids = synchronized(serverMap) { serverMap.keys.toList() }
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("subscribe"))
            put("scope", kotlinx.serialization.json.JsonPrimitive("all"))
            put("ids", JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        socket?.send(payload.toString())
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
                    s.long("ts")?.let { timestamp ->
                        merged = JsonObject(merged + ("last_updated" to kotlinx.serialization.json.JsonPrimitive(timestamp)))
                        if (_detail.value.id == serverId) {
                            val row = JsonObject(data + ("timestamp" to kotlinx.serialization.json.JsonPrimitive(timestamp)))
                            val cutoff = System.currentTimeMillis() - (_detail.value.hours * 3_600_000).toLong()
                            val rows = (_detail.value.history + row).distinctBy { it.long("timestamp") }.filter { (it.long("timestamp") ?: 0) >= cutoff }.sortedBy { it.long("timestamp") }.takeLast(6000)
                            _detail.value = _detail.value.copy(history = rows)
                        }
                    }
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
        if (!monitoringActive || livePaused) return
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
                emitServers()
                val timeout = (_ui.value.config.int("frontend_ws_timeout_minutes") ?: 0).coerceIn(0, 1440)
                if (!livePaused && timeout > 0 && wsOpenedAt > 0 && System.currentTimeMillis() - wsOpenedAt >= timeout * 60_000L) {
                    livePaused = true; connectionVersion++; reconnectJob?.cancel()
                    ws?.close(1000, "session timeout"); ws = null
                    _ui.value = _ui.value.copy(liveExpired = true, conn = Conn.POLLING)
                }
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
                if (_detail.value.id == id) _detail.value = _detail.value.copy(server = s, loading = false)
            } catch (e: Exception) {
                if (_detail.value.id == id) _detail.value = _detail.value.copy(loading = false, error = friendly(e))
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
        historyJob?.cancel()
        _detail.value = _detail.value.copy(historyLoading = true)
        historyJob = scope.launch {
            try {
                val client = api ?: throw IOException("未连接")
                val arr = client.getHistory(id, hours)
                val rows = arr.mapNotNull { it as? JsonObject }
                    .sortedBy { it.long("timestamp") ?: 0L }
                if (_detail.value.id == id && _detail.value.hours == hours)
                    _detail.value = _detail.value.copy(history = rows, historyLoading = false, error = null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_detail.value.id != id || _detail.value.hours != hours) return@launch
                if (e is ApiException && e.code == 403 && e.message.orEmpty().contains("turnstile", true)
                    && System.currentTimeMillis() - lastTurnstileDismiss > 60_000
                ) {
                    api?.turnstileVerified = null
                    prefs.edit().remove(KEY_TS_VERIFIED).remove(KEY_TS_EXPIRES).apply()
                    _detail.value = _detail.value.copy(historyLoading = false, error = "人机验证已过期")
                    _ui.value = _ui.value.copy(needTurnstile = true, turnstileMode = "api")
                } else {
                    _detail.value = _detail.value.copy(historyLoading = false, error = friendly(e))
                }
            }
        }
    }

    fun clearDetailError() {
        _detail.value = _detail.value.copy(error = null)
    }

    suspend fun admin(action: String, fields: JsonObject = JsonObject(emptyMap())): JsonObject =
        withContext(Dispatchers.IO) { (api ?: throw IOException("请先连接面板")).admin(action, fields) }

    suspend fun maintenance(path: String): JsonObject = withContext(Dispatchers.IO) {
        (api ?: throw IOException("请先连接面板")).maintenance(path)
    }

    suspend fun themes(): JsonObject = withContext(Dispatchers.IO) {
        (api ?: throw IOException("请先连接面板")).themes()
    }

    private fun friendly(e: Exception): String {
        if (e is ApiException && e.message.orEmpty().contains("turnstile", true)) {
            return "需要完成 Cloudflare 人机验证"
        }
        return when (e) {
            is ApiException -> e.message ?: "请求失败 (${e.code})"
            is IOException -> "网络连接失败：${e.message ?: "请检查地址与网络"}"
            else -> "出错了：${e.message ?: e.javaClass.simpleName}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        ws?.close(1000, null)
        reconnectJob?.cancel()
        pollJob?.cancel()
        connectionVersion++
        scope.cancel()
    }
}
