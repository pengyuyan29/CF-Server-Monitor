package com.yunkong.monitor.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

private fun InputStream.readBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "博客包超过大小限制" }; out.write(buffer, 0, n) }
    return out.toByteArray()
}

class BlogRepository {
    private val http = OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    private var key: ByteArray? = null
    private var origin: String = ""
    private var archive: File? = null

    companion object {
        private fun fromArchive(file: File, path: String): JsonObject = ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory && (it.name == path || it.name.endsWith("/$path")) }.toList()
            require(entries.size == 1) { "加密博客 ZIP 中缺少或重复 $path" }
            val entry = entries.single()
            require(entry.size <= 32L * 1024 * 1024) { "文章包太大" }
            val bytes = zip.getInputStream(entry).use { it.readBounded(32 * 1024 * 1024) }
            API_JSON.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: error("加密博客格式异常")
        }
        fun validateArchive(file: File) {
            val vault = fromArchive(file, "vault.json")
            require(vault.int("version") == 1 && vault.int("iterations") == 600000 && vault.str("data") != null) { "ZIP 不是博客 dist 的加密产物" }
        }
    }

    private fun read(base: String, path: String): JsonObject {
        archive?.let { return fromArchive(it, path) }
        val url = (base.trim().trimEnd('/') + "/").toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "请输入 HTTPS 博客地址" }
        val target = url.resolve(path) ?: throw IOException("地址无效")
        http.newCall(Request.Builder().url(target).header("Cache-Control", "no-store").build()).execute().use {
            if (!it.isSuccessful) throw IOException("博客请求失败 (${it.code})，请检查 Pages 地址")
            val body = it.body ?: throw IOException("空响应")
            require(body.contentLength() <= 32L * 1024 * 1024) { "文章包超过大小限制" }
            val bytes = body.byteStream().use { stream -> stream.readBounded(32 * 1024 * 1024) }
            return API_JSON.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: throw IOException("博客包格式异常")
        }
    }

    @Synchronized fun unlock(base: String, password: CharArray, localArchive: File? = null): List<JsonObject> {
        lock()
        archive = localArchive
        var derived: ByteArray? = null
        try {
            val vault = read(base, "vault.json")
            derived = BlogCrypto.derive(password, vault)
            val metadata = BlogCrypto.decrypt(derived, vault)
            key = derived
            origin = base
            return metadata.array("posts")?.mapNotNull { it as? JsonObject }.orEmpty()
        } catch (e: Exception) {
            derived?.fill(0)
            if (e is javax.crypto.AEADBadTagException) throw IOException("密码不正确，或加密包已损坏")
            throw e
        } finally { password.fill('\u0000') }
    }

    @Synchronized fun article(slug: String): String {
        val activeKey = key ?: throw IOException("请先解锁博客")
        return BlogCrypto.decrypt(activeKey, read(origin, "articles/${BlogCrypto.articleFile(slug)}.json")).str("html")
            ?: throw IOException("文章内容格式异常")
    }

    @Synchronized fun lock() { key?.fill(0); key = null; origin = ""; archive = null }
}
