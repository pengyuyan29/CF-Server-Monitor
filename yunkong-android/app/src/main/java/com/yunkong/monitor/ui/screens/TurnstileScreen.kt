package com.yunkong.monitor.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.data.str

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.needTurnstile) { if (!ui.needTurnstile) onBack() }
    LaunchedEffect(Unit) { vm.ensureTurnstileConfig() }

    DisposableEffect(Unit) {
        onDispose { if (ui.needTurnstile) vm.dismissTurnstile() }
    }

    val siteKey = (ui.config?.str("turnstile_site_key") ?: "").trim()
    val prompt = if (ui.turnstileMode == "login") {
        "登录前需要先完成 Cloudflare 人机验证"
    } else {
        "访问该面板需要先完成 Cloudflare 人机验证"
    }

    Column(Modifier.fillMaxSize().background(s.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = s.onBackground)
            }
            Text("人机验证", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = s.onBackground)
        }
        Text(
            prompt,
            fontSize = 13.sp, color = s.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (siteKey.isEmpty()) {
                CircularProgressIndicator(color = s.primary)
            } else {
                val html = buildString {
                    append("<!DOCTYPE html><html><head>")
                    append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                    append("<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\" async defer></script>")
                    append("</head><body style=\"margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:transparent;\">")
                    append("<div id=\"box\"></div><script>")
                    append("function __ok(t){ try{ AndroidBridge.onToken(t); }catch(e){} }")
                    append("function __err(){ try{ AndroidBridge.onError('widget'); }catch(e){} }")
                    append("window.onload=function(){ turnstile.render('#box',{sitekey:'")
                    append(sanitizeSiteKey(siteKey))
                    append("',callback:__ok,'error-callback':__err,'expired-callback':__err}); }")
                    append("</script></body></html>")
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(Color.TRANSPARENT)
                            addJavascriptInterface(Bridge(vm), "AndroidBridge")
                            // 以面板域名为 base 加载本地页面，使 Turnstile 校验通过面板域名完成
                            loadDataWithBaseURL(vm.baseUrlForWebView(), html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            "验证通过后 1 小时内无需重复验证",
            fontSize = 12.sp, color = s.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)
        )
    }
}

/** 防注入：site key 只保留字母数字与 - _ */
private fun sanitizeSiteKey(k: String): String =
    k.filter { it.isLetterOrDigit() || it == '-' || it == '_' }

private class Bridge(private val vm: MonitorViewModel) {
    @JavascriptInterface
    fun onToken(token: String) {
        vm.submitTurnstileToken(token)
    }

    @JavascriptInterface
    fun onError(msg: String) {
        // 组件加载失败时忽略，用户可返回重试
    }
}
