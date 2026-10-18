package com.yunkong.monitor.data

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
/** Wire compatible with blog/scripts/build.mjs; no plaintext disk cache. */
object BlogCrypto {
    fun derive(password: CharArray, vault: JsonObject): ByteArray {
        require(vault.int("version") == 1 && vault.int("iterations") == 600000) { "不支持的博客加密版本" }
        require(vault.str("kdf") == "PBKDF2-SHA256") { "不支持的密钥派生格式" }
        val spec = PBEKeySpec(password, Base64.getDecoder().decode(vault.str("salt")), 600000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun decrypt(key: ByteArray, pack: JsonObject): JsonObject {
        val iv = Base64.getDecoder().decode(pack.str("iv"))
        require(iv.size == 12) { "文章加密参数异常" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(Base64.getDecoder().decode(pack.str("data")))
        return try { API_JSON.parseToJsonElement(plain.toString(Charsets.UTF_8)) as JsonObject }
        finally { plain.fill(0) }
    }

    fun articleFile(slug: String): String = MessageDigest.getInstance("SHA-256")
        .digest(slug.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
}

