package com.yunkong.monitor.data

import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.security.SecureRandom

internal fun element(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to element(it.value) })
    is Iterable<*> -> JsonArray(value.map(::element))
    else -> JsonPrimitive(value.toString())
}
internal fun json(vararg values: Pair<String, Any?>) = element(values.toMap()) as JsonObject
val PRETTY_JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }
fun JsonElement.pretty(): String = PRETTY_JSON.encodeToString(JsonElement.serializer(), this)

data class NodeResult(val nodes: List<JsonObject>, val errors: List<String> = emptyList(), val warnings: List<String> = emptyList())
data class RouteEntry(val kind: String, val values: String, val target: String)
data class RouteSetEntry(val tag: String, val url: String, val format: String = "binary", val target: String = "direct")

object SingBoxCore {
    private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    private fun base64(s: String): String = runCatching { String(Base64.getDecoder().decode(s.filterNot(Char::isWhitespace)), Charsets.UTF_8) }
        .recoverCatching { String(Base64.getUrlDecoder().decode(s.filterNot(Char::isWhitespace)), Charsets.UTF_8) }.getOrThrow()
    private data class Link(val user: String, val host: String, val port: Int, val query: Map<String, String>, val name: String)
    private fun address(raw: String): Pair<String, Int> {
        val s = raw.trimEnd('/')
        val host: String
        val portText: String?
        if (s.startsWith("[")) {
            require(s.contains(']')) { "IPv6 地址缺少右括号" }
            host = s.substringAfter('[').substringBefore(']')
            portText = s.substringAfter(']', "").removePrefix(":").ifEmpty { null }
        } else {
            host = s.substringBeforeLast(':', s)
            portText = if (s.contains(':')) s.substringAfterLast(':') else null
        }
        val port = if (portText == null) 443 else portText.toIntOrNull() ?: error("端口无效")
        require(host.isNotBlank() && port in 1..65535) { "地址或端口无效" }
        return host to port
    }
    private fun split(raw: String): Link {
        val main = raw.substringBefore('#')
        val authority = main.substringBefore('?')
        val at = authority.lastIndexOf('@')
        require(at >= 0) { "缺少认证凭据" }
        val (host, port) = address(authority.substring(at + 1))
        val q = main.substringAfter('?', "").split('&').filter { it.isNotBlank() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }
        return Link(authority.substring(0, at), host, port, q, decode(raw.substringAfter('#', "")))
    }

    fun parseLink(input: String): Pair<JsonObject, List<String>> {
        val scheme = input.substringBefore("://").lowercase()
        require(input.contains("://")) { "不是有效的分享链接" }
        var raw = input.substringAfter("://")
        val warnings = mutableListOf<String>()
        if (scheme == "vmess") {
            val source = API_JSON.parseToJsonElement(base64(raw.substringBefore('#'))) as? JsonObject ?: error("VMess 载荷不是 JSON")
            val host = source.str("add").orEmpty()
            val port = source.int("port") ?: error("缺少端口")
            require(host.isNotBlank() && port in 1..65535 && !source.str("id").isNullOrBlank()) { "缺少地址或 UUID" }
            val values = linkedMapOf<String, Any?>("type" to "vmess", "tag" to (source.str("ps")?.ifBlank { null } ?: host), "server" to host, "server_port" to port, "uuid" to source.str("id"), "alter_id" to (source.int("aid") ?: 0), "security" to (source.str("scy") ?: "auto"))
            if (source.str("tls") in listOf("tls", "true")) values["tls"] = json("enabled" to true, "server_name" to (source.str("sni")?.ifBlank { null } ?: source.str("host")?.ifBlank { null } ?: host))
            transport(source.str("net") ?: "tcp", source.str("path") ?: "/", source.str("host"), source.str("path"), warnings)?.let { values["transport"] = it }
            return element(values) as JsonObject to warnings
        }
        if (scheme == "ss") {
            val tag = decode(raw.substringAfter('#', ""))
            var authority = raw.substringBefore('#').substringBefore('?')
            if (!authority.contains('@')) authority = base64(authority)
            val at = authority.lastIndexOf('@')
            require(at > 0) { "SS 缺少地址" }
            val credentials = authority.substring(0, at)
            val clear = runCatching { base64(credentials) }.getOrNull()?.takeIf { it.contains(':') } ?: decode(credentials)
            require(clear.contains(':')) { "SS 缺少 method:password" }
            val (host, port) = address(authority.substring(at + 1))
            if (raw.contains("plugin=")) warnings += "SIP003 插件未迁移，请在客户端补充插件配置"
            return json("type" to "shadowsocks", "tag" to tag.ifBlank { "$host:$port" }, "server" to host, "server_port" to port, "method" to clear.substringBefore(':'), "password" to clear.substringAfter(':')) to warnings
        }
        require(scheme in listOf("vless", "trojan", "hysteria2", "hy2", "tuic")) { "暂不支持的协议：$scheme" }
        val link = split(raw)
        val q = link.query
        val type = if (scheme == "hy2") "hysteria2" else scheme
        val values = linkedMapOf<String, Any?>("type" to type, "tag" to link.name.ifBlank { "${link.host}:${link.port}" }, "server" to link.host, "server_port" to link.port)
        val tls = linkedMapOf<String, Any?>("enabled" to true, "server_name" to (q["sni"] ?: q["peer"] ?: if (type == "tuic") link.host else ""))
        when (type) {
            "vless" -> {
                require(link.user.isNotBlank()) { "缺少 UUID" }
                values["uuid"] = decode(link.user); values["flow"] = q["flow"] ?: ""; values["packet_encoding"] = "xudp"
                val security = q["security"] ?: "none"
                require(security in listOf("none", "", "tls", "reality", "xtls")) { "不支持的安全类型：$security" }
                if (security !in listOf("none", "")) {
                    q["fp"]?.let { tls["utls"] = json("enabled" to true, "fingerprint" to it) }
                    if (security == "reality") {
                        tls["utls"] = json("enabled" to true, "fingerprint" to (q["fp"] ?: "chrome"))
                        tls["reality"] = json("enabled" to true, "public_key" to q["pbk"].orEmpty(), "short_id" to q["sid"].orEmpty())
                        if (q["pbk"].isNullOrBlank()) warnings += "Reality 缺少 pbk，无法握手"
                    }
                    values["tls"] = element(tls)
                }
            }
            "tuic" -> {
                require(link.user.contains(':')) { "缺少 uuid:password" }
                values["uuid"] = decode(link.user.substringBefore(':')); values["password"] = decode(link.user.substringAfter(':'))
                values["congestion_control"] = q["congestion_control"] ?: "cubic"
                q["udp_relay_mode"]?.let { values["udp_relay_mode"] = it }
                tls["insecure"] = (q["allow_insecure"] ?: q["insecure"]) in listOf("1", "true")
                q["alpn"]?.let { tls["alpn"] = it.split(',').filter(String::isNotBlank) }
                values["tls"] = element(tls)
            }
            else -> {
                require(link.user.isNotBlank()) { "缺少认证密码" }
                values["password"] = decode(link.user)
                tls["insecure"] = (q["insecure"] ?: q["allowInsecure"]) in listOf("1", "true")
                q["alpn"]?.let { tls["alpn"] = it.split(',').filter(String::isNotBlank) }
                values["tls"] = element(tls)
                if (type == "hysteria2") {
                    for (key in listOf("up", "down")) q[key]?.takeWhile(Char::isDigit)?.toIntOrNull()?.let { values["${key}_mbps"] = it }
                    if (q["obfs"] == "salamander" && !q["obfs-password"].isNullOrBlank()) values["obfs"] = json("type" to "salamander", "password" to q["obfs-password"])
                    else if (!q["obfs"].isNullOrBlank()) warnings += "不支持此混淆类型"
                    if (q.containsKey("mport") || q.containsKey("ports")) warnings += "端口跳跃暂不支持，已保留单端口"
                }
            }
        }
        if (type in listOf("vless", "trojan")) transport(q["type"] ?: "tcp", q["path"] ?: "/", q["host"], q["serviceName"], warnings)?.let { values["transport"] = it }
        return element(values) as JsonObject to warnings
    }

    private fun transport(type: String, path: String, host: String?, service: String?, warnings: MutableList<String>): JsonObject? = when (type.lowercase()) {
        "tcp", "" -> null
        "ws" -> if (host.isNullOrBlank()) json("type" to "ws", "path" to path) else json("type" to "ws", "path" to path, "headers" to json("Host" to host))
        "grpc" -> json("type" to "grpc", "service_name" to service.orEmpty())
        "h2", "http" -> if (host.isNullOrBlank()) json("type" to "http", "path" to path) else json("type" to "http", "path" to path, "host" to host.split(','))
        else -> { warnings += "传输层 $type 暂不支持，按 TCP 处理"; null }
    }

    fun subscription(text: String): NodeResult {
        val s = text.trim(); require(s.isNotBlank()) { "内容为空" }
        if (s.startsWith('{') || s.startsWith('[')) {
            val parsed = API_JSON.parseToJsonElement(s)
            require(parsed.obj("proxies") == null && parsed.array("proxies") == null) { "Clash 配置暂不支持，请提供分享链接" }
            val arr = (parsed as? JsonArray) ?: parsed.array("outbounds") ?: error("需要 outbounds 或出站数组")
            val nodes = arr.mapNotNull { it as? JsonObject }.filter { it.str("type") in listOf("ss", "shadowsocks", "vmess", "vless", "trojan", "hysteria2", "tuic", "socks", "http", "ssh", "wireguard") }
                .map { if (it.str("type") == "ss") JsonObject(it + ("type" to JsonPrimitive("shadowsocks"))) else it }
            require(nodes.isNotEmpty()) { "没有可用的代理节点" }
            return NodeResult(dedupe(nodes))
        }
        val decoded = if (!s.contains("://")) runCatching { base64(s) }.getOrElse { error("不是链接、Base64 订阅或 sing-box JSON") } else s
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>(); val nodes = mutableListOf<JsonObject>()
        decoded.lines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith('#') && !line.startsWith("//")) {
                try { val (node, warn) = parseLink(line); nodes += node; warnings += warn }
                catch (e: Exception) { errors += "第 ${i + 1} 行：${e.message}" }
            }
        }
        require(nodes.isNotEmpty()) { errors.firstOrNull() ?: "没有解析出节点" }
        return NodeResult(dedupe(nodes), errors, warnings.distinct())
    }

    fun dedupe(nodes: List<JsonObject>): List<JsonObject> {
        val used = mutableSetOf("direct", "AUTO", "DNS", "节点选择")
        return nodes.map { node ->
            val name = node.str("tag")?.ifBlank { null } ?: node.str("server") ?: "节点"
            var candidate = name; var index = 2
            while (!used.add(candidate)) candidate = "$name ${index++}"
            JsonObject(node + ("tag" to JsonPrimitive(candidate)))
        }
    }

    fun buildConfig(nodes: List<JsonObject>, tun: Boolean = true, auto: Boolean = true): JsonObject {
        require(nodes.isNotEmpty()) { "节点库为空" }
        val clean = dedupe(nodes); val tags = clean.map { it.str("tag").orEmpty() }
        val out = mutableListOf<JsonObject>(json("type" to "selector", "tag" to "节点选择", "interrupt_exist_connections" to false, "outbounds" to (tags + (if (auto) listOf("AUTO") else emptyList()) + "direct")))
        if (auto) out += json("type" to "urltest", "tag" to "AUTO", "outbounds" to tags, "url" to "https://www.gstatic.com/generate_204", "interval" to "5m", "tolerance" to 50)
        out += json("type" to "selector", "tag" to "DNS", "interrupt_exist_connections" to false, "outbounds" to ((if (auto) listOf("AUTO") else emptyList()) + tags))
        out += clean; out += json("type" to "direct", "tag" to "direct")
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        val inbound = if (tun) json("type" to "tun", "tag" to "tun-in", "address" to listOf("172.19.0.1/30", "fd00::1/126"), "auto_route" to true, "strict_route" to true, "stack" to "mixed")
            else json("type" to "mixed", "tag" to "mixed-in", "listen" to "127.0.0.1", "listen_port" to 2080)
        return json(
            "log" to json("level" to "warn", "timestamp" to true),
            "experimental" to json("clash_api" to json("external_controller" to "127.0.0.1:20123", "secret" to secret, "default_mode" to "rule"), "cache_file" to json("enabled" to true, "path" to "cache.db", "store_dns" to true)),
            "http_clients" to listOf(json("tag" to "http-direct")), "inbounds" to listOf(inbound), "outbounds" to out,
            "route" to json("rules" to listOf(json("inbound" to inbound.str("tag"), "action" to "sniff"), json("protocol" to "dns", "action" to "hijack-dns"), json("ip_version" to 6, "action" to "reject"), json("ip_is_private" to true, "outbound" to "direct"), json("rule_set" to "geoip-cn", "outbound" to "direct"), json("rule_set" to "geosite-cn", "outbound" to "direct")),
                "rule_set" to defaultRuleSets(), "auto_detect_interface" to true, "final" to "节点选择", "default_http_client" to "http-direct", "default_domain_resolver" to "dns-local"),
            "dns" to json("servers" to listOf(json("type" to "https", "tag" to "dns-local", "server" to "223.5.5.5"), json("type" to "https", "tag" to "dns-remote", "server" to "1.1.1.1", "detour" to "DNS")),
                "rules" to listOf(json("action" to "route", "clash_mode" to "direct", "server" to "dns-local"), json("action" to "route", "clash_mode" to "global", "server" to "dns-remote"), json("action" to "route", "rule_set" to "geosite-cn", "server" to "dns-local")), "final" to "dns-remote", "strategy" to "ipv4_only"))
    }

    private fun defaultRuleSets() = listOf("geoip-cn", "geosite-cn").map { tag -> json("tag" to tag, "type" to "remote", "format" to "binary", "url" to "https://gh-proxy.com/https://github.com/lyc8503/sing-box-rules/raw/rule-set-${tag.substringBefore('-')}/$tag.srs", "http_client" to "http-direct") }

    /** http_clients/default_http_client were introduced in 1.14, not 1.12. */
    fun forVersion(config: JsonObject, modern: Boolean): JsonObject {
        if (modern) return config
        val converted = config.toMutableMap()
        converted.remove("http_clients")
        config.obj("route")?.let { route ->
            val values = route.toMutableMap(); values.remove("default_http_client")
            route.array("rule_set")?.let { sets -> values["rule_set"] = JsonArray(sets.map { set ->
                val fields = (set as? JsonObject)?.toMutableMap() ?: return@map set
                if (fields.remove("http_client") != null) fields["download_detour"] = JsonPrimitive("direct")
                JsonObject(fields)
            }) }
            converted["route"] = JsonObject(values)
        }
        config.obj("experimental")?.let { experimental -> experimental.obj("cache_file")?.let { cache ->
            converted["experimental"] = JsonObject(experimental + ("cache_file" to JsonObject(cache.filterKeys { it != "store_dns" })))
        } }
        return JsonObject(converted)
    }

    fun route(entries: List<RouteEntry>, includeChina: Boolean, remoteSets: List<RouteSetEntry> = emptyList()): JsonObject {
        val rules = entries.mapIndexed { index, e ->
            require(e.kind in listOf("domain_suffix", "domain", "domain_keyword", "domain_regex", "ip_cidr", "ip_is_private", "port", "process_name", "protocol")) { "第 ${index + 1} 条规则类型无效" }
            val values = e.values.split(Regex("[,，\\s]+" )).filter { it.isNotBlank() }.distinct()
            require(e.kind == "ip_is_private" || values.isNotEmpty()) { "第 ${index + 1} 条规则缺少值" }
            val match = when (e.kind) {
                "ip_is_private" -> JsonPrimitive(true)
                "port" -> element(values.map { it.toIntOrNull()?.takeIf { n -> n in 1..65535 } ?: error("无效端口：$it") })
                "protocol" -> element(values.map { it.lowercase().also { p -> require(p in listOf("dns", "stun", "quic", "tls", "ssh", "http", "bittorrent")) { "不支持协议：$p" } } })
                else -> element(values)
            }
            val target = when (e.target) { "reject" -> "action" to JsonPrimitive("reject"); "direct" -> "outbound" to JsonPrimitive("direct"); else -> "outbound" to JsonPrimitive("节点选择") }
            JsonObject(mapOf(e.kind to match, target))
        }.toMutableList()
        if (remoteSets.isNotEmpty()) {
            require(remoteSets.map { it.tag }.distinct().size == remoteSets.size) { "规则集 tag 重复" }
            val definitions = remoteSets.map { rs ->
                require(rs.tag.isNotBlank() && rs.format in listOf("binary", "source")) { "规则集名称或格式无效" }
                val uri = java.net.URI(rs.url)
                require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "规则集地址无效" }
                json("tag" to rs.tag, "type" to "remote", "format" to rs.format, "url" to rs.url, "http_client" to "http-direct")
            }
            rules.addAll(0, remoteSets.map { rs -> if (rs.target == "reject") json("rule_set" to rs.tag, "action" to "reject") else json("rule_set" to rs.tag, "outbound" to if (rs.target == "direct") "direct" else "节点选择") })
            return json("rules" to rules, "rule_set" to definitions)
        }
        if (includeChina) rules.addAll(0, listOf(json("rule_set" to "geoip-cn", "outbound" to "direct"), json("rule_set" to "geosite-cn", "outbound" to "direct")))
        return if (includeChina) json("rules" to rules, "rule_set" to defaultRuleSets()) else json("rules" to rules)
    }

    /** Structural checks only; never claims to replace the sing-box executable. */
    fun validate(text: String): List<String> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: error("配置必须是 JSON 对象")
        val issues = mutableListOf<String>()
        val out = root.array("outbounds") ?: run { issues += "缺少 outbounds"; JsonArray(emptyList()) }
        if (root.array("inbounds").isNullOrEmpty()) issues += "缺少 inbounds"
        if (root.obj("route") == null) issues += "缺少 route"
        val tags = out.mapNotNull { it.str("tag") }
        tags.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { issues += "重复出站 tag：$it" }
        val known = tags.toSet()
        fun check(ref: String?) { if (!ref.isNullOrBlank() && ref !in known) issues += "引用不存在的出站：$ref" }
        out.forEach { o ->
            if (o.str("type") in listOf("ss", "block", "dns")) issues += "${o.str("type")} 不是目标版本的可用出站类型"
            o.array("outbounds")?.forEach { check((it as? JsonPrimitive)?.content) }
            check(o.str("detour"))
        }
        root.obj("route")?.let { route -> check(route.str("final")); route.array("rules")?.forEach { check(it.str("outbound")) } }
        val clients = root.array("http_clients")?.mapNotNull { it.str("tag") }.orEmpty().toSet()
        root.obj("route")?.array("rule_set")?.forEach { set -> set.str("http_client")?.let { if (it !in clients) issues += "规则集引用不存在的 HTTP client：$it" } }
        root.obj("dns")?.array("servers")?.forEach { check(it.str("detour")) }
        return issues.distinct()
    }

    fun extract(text: String): NodeResult {
        if (text.trim().startsWith('{') || text.trim().startsWith('[')) return subscription(text)
        val decoded = HtmlEntities.decode(text)
        val links = Regex("(?:ss|vmess|vless|trojan|hysteria2|hy2|tuic)://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(decoded).map { it.value }.distinct().toList()
        return subscription(links.joinToString("\n"))
    }
}

private object HtmlEntities {
    fun decode(s: String): String = s.replace("&amp;", "&").replace("&#38;", "&").replace("&#x26;", "&")
}
