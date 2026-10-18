package com.yunkong.monitor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** 服务端返回的指标字段类型不固定（number / string / null / false），统一用 JsonObject 表示一台服务器 */
typealias ServerState = JsonObject

val API_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** 从 JsonObject 中按 key 取字符串 */
fun JsonElement?.str(key: String): String? {
    val obj = this as? JsonObject ?: return null
    val e = obj[key] ?: return null
    if (e is JsonNull) return null
    return (e as? JsonPrimitive)?.content
}

/** 取数值（容忍字符串数字、null、false） */
fun JsonElement?.num(key: String): Double? {
    val obj = this as? JsonObject ?: return null
    val e = obj[key] ?: return null
    if (e !is JsonPrimitive || e is JsonNull) return null
    return if (e.isString) e.content.toDoubleOrNull() else e.content.toDoubleOrNull()
}

fun JsonElement?.int(key: String): Int? = num(key)?.toInt()
fun JsonElement?.long(key: String): Long? = num(key)?.toLong()

/** 布尔：容忍 "1"/"0"/"true"/false/null */
fun JsonElement?.bool(key: String): Boolean? {
    val obj = this as? JsonObject ?: return null
    val e = obj[key] ?: return null
    if (e is JsonNull) return null
    return when {
        e is JsonPrimitive && !e.isString -> e.booleanOrNull ?: when (e.content) { "1" -> true; "0" -> false; else -> null }
        e is JsonPrimitive -> e.content == "1" || e.content == "true"
        else -> null
    }
}

/** 月流量字段为字节，额度为 GiB；方向必须与源站一致。 */
fun ServerState.trafficUsageBytes(): Double {
    val rx = num("net_rx_monthly") ?: 0.0
    val tx = num("net_tx_monthly") ?: 0.0
    return when (str("traffic_calc_type")) { "dl" -> rx; "ul" -> tx; "max" -> maxOf(rx, tx); else -> rx + tx }
}

fun ServerState.trafficUsagePercent(): Double? = num("traffic_limit")?.takeIf { it > 0 }?.let {
    trafficUsageBytes() / (it * 1024.0 * 1024.0 * 1024.0) * 100.0
}

fun JsonElement?.obj(key: String): JsonObject? {
    val obj = this as? JsonObject ?: return null
    return obj[key] as? JsonObject
}

fun JsonElement?.array(key: String): JsonArray? {
    val obj = this as? JsonObject ?: return null
    return obj[key] as? JsonArray
}

/** 合并 WS 增量样本：把 data 的字段覆盖到目标 server */
fun mergeSample(target: JsonObject, data: JsonObject): JsonObject {
    val merged = LinkedHashMap(target)
    for ((k, v) in data) {
        merged[k] = v
    }
    return JsonObject(merged)
}

/** 服务器是否在线：5 分钟内有上报 */
fun ServerState.isOnline(): Boolean {
    val last = this.long("last_updated")
    if (last != null && last > 0) {
        return System.currentTimeMillis() - last <= 300_000
    }
    return this.bool("is_online") ?: false
}
