package com.yunkong.monitor.data

import java.io.File
import java.util.Base64
import kotlinx.serialization.json.*

fun main(args: Array<String>) {
    var checks = 0
    fun test(name: String, block: () -> Unit) { block(); checks++; println("PASS $name") }
    val uuid = "123e4567-e89b-12d3-a456-426614174000"
    test("VLESS Reality and IPv6") {
        val n = SingBoxCore.parseLink("vless://$uuid@[2001:db8::1]:443?security=reality&pbk=example&sid=aa&flow=xtls-rprx-vision#%E9%A6%99%E6%B8%AF").first
        check(n.str("server") == "2001:db8::1" && n.str("tag") == "香港")
        check(n.obj("tls")?.obj("reality")?.str("public_key") == "example")
    }
    test("Trojan percent decoding exactly once") {
        val n = SingBoxCore.parseLink("trojan://a%2Bb%2540@example.com:8443?sni=example.net&type=ws&path=%2Fa%3Fb%3D1#test").first
        check(n.str("password") == "a+b%40")
        check(n.obj("transport")?.str("path") == "/a?b=1")
    }
    test("Shadowsocks SIP002") {
        val credentials = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-128-gcm:a:b".toByteArray())
        val n = SingBoxCore.parseLink("ss://$credentials@example.com:8388#SS").first
        check(n.str("type") == "shadowsocks" && n.str("password") == "a:b")
    }
    test("Legacy Shadowsocks entire-base64") {
        val content = Base64.getEncoder().encodeToString("aes-256-gcm:password@example.com:1234".toByteArray())
        check(SingBoxCore.parseLink("ss://$content").first.int("server_port") == 1234)
    }
    test("VMess base64 with websocket TLS") {
        val payload = json("add" to "example.org", "port" to "443", "id" to uuid, "net" to "ws", "path" to "/hello", "host" to "cdn.example.org", "tls" to "tls")
        val n = SingBoxCore.parseLink("vmess://${Base64.getEncoder().encodeToString(payload.toString().toByteArray())}").first
        check(n.obj("tls")?.str("server_name") == "cdn.example.org" && n.obj("transport")?.str("type") == "ws")
    }
    test("Hysteria2 obfuscation and bandwidth") {
        val n = SingBoxCore.parseLink("hy2://secret@example.com:443?obfs=salamander&obfs-password=mask&up=100&alpn=h3").first
        check(n.str("type") == "hysteria2" && n.int("up_mbps") == 100 && n.obj("obfs")?.str("password") == "mask")
    }
    test("TUIC TLS and credentials") {
        val n = SingBoxCore.parseLink("tuic://$uuid:p%3Aa@example.com:443?alpn=h3&congestion_control=bbr").first
        check(n.str("password") == "p:a" && n.str("congestion_control") == "bbr" && n.obj("tls")?.array("alpn")?.size == 1)
    }
    test("Reject invalid and missing ports") {
        check(runCatching { SingBoxCore.parseLink("vless://$uuid@example.com:99999") }.isFailure)
        check(runCatching { SingBoxCore.parseLink("unknown://x@example.com") }.isFailure)
        check(runCatching { SingBoxCore.parseLink("tuic://missing@example.com:443") }.isFailure)
    }
    test("Subscription line numbers and duplicate names") {
        val r = SingBoxCore.subscription("# comment\nvless://$uuid@one.example:443#name\nnot-a-link\nvless://$uuid@two.example:443#name")
        check(r.nodes.size == 2 && r.errors.single().startsWith("第 3 行"))
        check(r.nodes[1].str("tag") == "name 2")
    }
    val nodes = SingBoxCore.subscription("vless://$uuid@example.com:443#AUTO").nodes
    test("Base64 subscription") {
        val b64 = Base64.getEncoder().encodeToString("vless://$uuid@example.com:443".toByteArray())
        check(SingBoxCore.subscription(b64).nodes.size == 1)
    }
    test("Reserved group tags never collide") { check(nodes.single().str("tag") == "AUTO 2") }
    test("TUN config references resolve") {
        val config = SingBoxCore.buildConfig(nodes)
        check(config.array("inbounds")?.single().str("type") == "tun")
        check(SingBoxCore.validate(config.toString()).isEmpty())
        check(config.array("outbounds")?.none { it.str("type") == "block" } == true)
    }
    test("Mixed config without AUTO") {
        val config = SingBoxCore.buildConfig(nodes, tun = false, auto = false)
        check(config.array("inbounds")?.single().str("type") == "mixed")
        check(config.array("outbounds")?.none { it.str("type") == "urltest" } == true)
        check(SingBoxCore.validate(config.toString()).isEmpty())
    }
    test("Fresh API secret per export") { check(SingBoxCore.buildConfig(nodes).obj("experimental")?.obj("clash_api")?.str("secret") != SingBoxCore.buildConfig(nodes).obj("experimental")?.obj("clash_api")?.str("secret")) }
    test("1.12 and 1.13 preset excludes 1.14-only fields") {
        val config = SingBoxCore.forVersion(SingBoxCore.buildConfig(nodes), false)
        check(!config.containsKey("http_clients") && config.obj("route")?.str("default_http_client") == null)
        check(config.obj("route")?.array("rule_set")?.all { it.str("http_client") == null && it.str("download_detour") == "direct" } == true)
        check(config.obj("experimental")?.obj("cache_file")?.containsKey("store_dns") == false)
        check(SingBoxCore.validate(config.toString()).isEmpty())
    }
    test("Reject legacy and dangling outbounds") {
        val broken = json("outbounds" to listOf(json("type" to "block", "tag" to "one"), json("type" to "selector", "tag" to "one", "outbounds" to listOf("missing"))))
        val errors = SingBoxCore.validate(broken.toString())
        check(errors.any { "重复" in it } && errors.any { "missing" in it } && errors.any { "block" in it })
    }
    test("Node extraction from HTML and ampersands") { check(SingBoxCore.extract("<a href=\"vless://$uuid@example.com:443?security=tls&amp;sni=x.example\">节点</a>").nodes.single().obj("tls")?.str("server_name") == "x.example") }
    test("JSON config extraction skips selector/direct") { check(SingBoxCore.subscription(SingBoxCore.buildConfig(nodes).toString()).nodes.size == 1) }
    test("Route reject action and port types") {
        val route = SingBoxCore.route(listOf(RouteEntry("domain_suffix", "ads.example", "reject"), RouteEntry("port", "80,443", "direct")), true)
        val rules = route.array("rules")!!
        check(rules[2].str("action") == "reject" && rules[2].str("outbound") == null)
        check(((rules[3].array("port")!![0]) as JsonPrimitive).isString.not())
    }
    test("Reject invalid rule ports/protocols") {
        check(runCatching { SingBoxCore.route(listOf(RouteEntry("port", "80abc", "direct")), false) }.isFailure)
        check(runCatching { SingBoxCore.route(listOf(RouteEntry("protocol", "unknown", "proxy")), false) }.isFailure)
    }
    test("Custom remote rule sets precede manual rules") {
        val route = SingBoxCore.route(listOf(RouteEntry("port", "443", "direct")), true, listOf(RouteSetEntry("ads", "https://example.com/ads.srs", target = "reject")))
        check(route.array("rules")?.first().str("action") == "reject")
        check(route.array("rule_set")?.single().str("tag") == "ads")
        check(runCatching { SingBoxCore.route(emptyList(), false, listOf(RouteSetEntry("x", "javascript:alert(1)"))) }.isFailure)
    }
    test("CF boolean numeric compatibility") { check(json("is_public" to 1).bool("is_public") == true && json("is_public" to "0").bool("is_public") == false) }
    test("Traffic calculation direction and byte units") {
        val sample = json("net_rx_monthly" to 1_073_741_824L, "net_tx_monthly" to 2_147_483_648L, "traffic_limit" to 4)
        for ((kind, expected) in listOf("dl" to 25.0, "ul" to 50.0, "max" to 50.0, "total" to 75.0)) check(JsonObject(sample + ("traffic_calc_type" to JsonPrimitive(kind))).trafficUsagePercent() == expected)
        check(json("traffic_limit" to 0).trafficUsagePercent() == null)
    }
    test("Probe missing/false/null distinctions preserved") { check(json("ping_ct" to false).num("ping_ct") == null && json("ping_cu" to null)["ping_cu"] is JsonNull) }
    val vault = API_JSON.parseToJsonElement(File(args.single()).readText()) as JsonObject
    val key = BlogCrypto.derive("测试päss🔐".toCharArray(), vault)
    test("WebCrypto/Node to JVM PBKDF2 AES-GCM Unicode password") { check(BlogCrypto.decrypt(key, vault).array("posts")?.single().str("title") == "原生阅读兼容") }
    test("Encrypted article filename matches JavaScript") { check(BlogCrypto.articleFile("原生-测试") == vault.str("expectedFile")) }
    test("Wrong blog password fails closed") {
        val wrong = BlogCrypto.derive("wrong".toCharArray(), vault)
        check(runCatching { BlogCrypto.decrypt(wrong, vault) }.isFailure); wrong.fill(0)
    }
    test("Tampered ciphertext rejected") {
        val bytes = Base64.getDecoder().decode(vault.str("data")); bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val modified = JsonObject(vault + ("data" to JsonPrimitive(Base64.getEncoder().encodeToString(bytes))))
        check(runCatching { BlogCrypto.decrypt(key, modified) }.isFailure)
    }
    key.fill(0)
    println("$checks core checks passed")
}
