package com.yunkong.monitor.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/** CF-Server-Monitor REST API 客户端 */
class ApiClient(
    val baseUrl: String,
    @Volatile var token: String? = null
) {

    /** Turnstile 验证凭证（服务端下发，1 小时有效） */
    @Volatile var turnstileVerified: String? = null
    @Volatile var turnstileVerifiedExpires: Long = 0L

    fun hasValidTurnstile(): Boolean =
        turnstileVerified != null && System.currentTimeMillis() < turnstileVerifiedExpires

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun cleanBase() = baseUrl.trim().trimEnd('/')

    private fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder(cleanBase()).append(path)
        if (query.isNotEmpty()) {
            sb.append('?')
            sb.append(query.entries.joinToString("&") {
                java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
            })
        }
        return sb.toString()
    }

    private fun request(path: String, query: Map<String, String> = emptyMap(), turnstileToken: String? = null): Request {
        val b = Request.Builder().url(url(path, query))
            .header("User-Agent", "YunKong/1.0")
        token?.let { b.header("Authorization", "Bearer $it") }
        turnstileVerified?.takeIf { System.currentTimeMillis() < turnstileVerifiedExpires }?.let {
            b.header("X-Turnstile-Verified", it)
        }
        turnstileToken?.let { b.header("X-Turnstile-Token", it) }
        return b.build()
    }

    private fun getJson(path: String, query: Map<String, String> = emptyMap(), turnstileToken: String? = null): JsonObject {
        val resp: Response = http.newCall(request(path, query, turnstileToken)).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, errorMessage(body, r.code))
            return API_JSON.parseToJsonElement(body).let { it as? JsonObject }
                ?: throw IOException("响应格式异常")
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        return try {
            val obj = API_JSON.parseToJsonElement(body) as? JsonObject
            obj?.str("error") ?: obj?.str("message") ?: "请求失败 ($code)"
        } catch (e: Exception) {
            when (code) {
                401 -> "未授权，请先登录（私有站点需要管理账号）"
                404 -> "接口不存在（404）"
                else -> "请求失败 ($code)"
            }
        }
    }

    /** 站点配置（无凭证时服务端旁路 Turnstile） */
    fun getConfig(): JsonObject = getJson("/api/config")

    /** 用一次性 Turnstile token 换取 1 小时 verified 凭证（响应含 turnstile_verified） */
    fun exchangeTurnstile(token: String): JsonObject = getJson("/api/config", turnstileToken = token)

    /** 服务器列表 */
    fun getServers(): JsonObject = getJson("/api/servers")

    /** 单台服务器详情 */
    fun getServer(id: String): JsonObject = getJson("/api/server", mapOf("id" to id))

    /** 历史指标 */
    fun getHistory(id: String, hours: Double): JsonArray {
        val resp = http.newCall(request("/api/history/all", mapOf("id" to id, "hours" to trimHours(hours)))).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, errorMessage(body, r.code))
            return API_JSON.parseToJsonElement(body).let { it as? JsonArray } ?: JsonArray(emptyList())
        }
    }

    private fun trimHours(h: Double): String =
        if (h == h.toLong().toDouble()) h.toLong().toString() else h.toString()

    /** 管理端登录（私有站点），返回 JWT；开启 Turnstile 时需要一次性 token */
    fun login(username: String, password: String, turnstileToken: String? = null): String {
        val payload = buildJsonObject {
            put("action", "login")
            put("username", username)
            put("password", password)
        }.toString()
        val builder = Request.Builder()
            .url(url("/admin/api"))
            .header("User-Agent", "YunKong/1.0")
        turnstileToken?.let { builder.header("X-Turnstile-Token", it) }
        val req = builder
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, errorMessage(body, r.code))
            val obj = API_JSON.parseToJsonElement(body) as? JsonObject
            val t = obj?.str("token") ?: throw IOException("登录失败：未返回令牌")
            token = t
            return t
        }
    }

    /** WebSocket 连接请求 */
    fun wsRequest(): Request {
        val query = linkedMapOf("subscribe" to "all")
        token?.let { query["token"] = it }
        return Request.Builder().url(url("/api/ws", query)).build()
    }
}
