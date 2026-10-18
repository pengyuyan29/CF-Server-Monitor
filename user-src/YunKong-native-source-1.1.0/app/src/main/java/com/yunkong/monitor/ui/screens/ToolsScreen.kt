package com.yunkong.monitor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.yunkong.monitor.data.*
import com.yunkong.monitor.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val secret = remember { SecretStore(context.applicationContext) }
    var tool by rememberSaveable { mutableStateOf("链接 → 配置") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tun by rememberSaveable { mutableStateOf(true) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var mixin by rememberSaveable { mutableStateOf(false) }
    var modern by rememberSaveable { mutableStateOf(true) }
    var library by remember { mutableStateOf(emptyList<JsonObject>()) }
    var parsed by remember { mutableStateOf(emptyList<JsonObject>()) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var kind by rememberSaveable { mutableStateOf("domain_suffix") }
    var target by rememberSaveable { mutableStateOf("direct") }
    var ruleValue by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf(emptyList<RouteEntry>()) }
    var china by rememberSaveable { mutableStateOf(true) }
    var remoteSets by remember { mutableStateOf("") }
    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""
        scope.launch {
            try { block() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = e.message ?: "处理失败"
            } finally { busy = false }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) task { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(output.toByteArray()) } ?: error("无法打开文件") }; message = "文件已保存" }
    }
    val saveQr = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = qr
        if (uri != null && bitmap != null) task { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("无法写入图片") }; message = "二维码图片已保存" }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) task { input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().also { data -> require(data.size <= 8 * 1024 * 1024) { "文件太大" } }.toString(Charsets.UTF_8) } ?: error("无法读取文件") } }
    }
    val importQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) task {
            input = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("图片无法读取")
                require(bytes.size <= 16 * 1024 * 1024) { "图片太大" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 2048) }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("图片格式不支持")
                val pixels = IntArray(bmp.width * bmp.height); bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                try { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, pixels))), mapOf(DecodeHintType.TRY_HARDER to true)).text }
                finally { bmp.recycle() }
            }
            output = input; message = "二维码已识别"
        }
    }
    LaunchedEffect(Unit) { task { library = withContext(Dispatchers.IO) { secret.get("nodes")?.let { SingBoxCore.subscription(it).nodes }.orEmpty() } } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(title = { Text("TOOL//BOX") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("链接 → 配置", "订阅转换", "配置校验", "规则生成器", "二维码", "节点提取").forEach { name ->
                FilterChip(tool == name, { tool = name; output = ""; message = ""; qr = null }, { Text(name) })
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("在本机处理 · 节点不会上传到转换服务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(modern, { modern = true }, { Text("sing-box 1.14+") })
                    FilterChip(!modern, { modern = false }, { Text("1.12 / 1.13") })
                }
            }
            if (tool == "规则生成器") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("domain_suffix", "domain", "domain_keyword", "domain_regex", "ip_cidr", "ip_is_private", "port", "process_name", "protocol").forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k) }) }
                        }
                        OutlinedTextField(ruleValue, { ruleValue = it }, label = { Text("匹配值，逗号或空白分隔") }, enabled = kind != "ip_is_private", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("direct", "proxy", "reject").forEach { t -> FilterChip(target == t, { target = t }, { Text(t) }) } }
                        Button({ rules = rules + RouteEntry(kind, ruleValue, target); ruleValue = "" }, enabled = kind == "ip_is_private" || ruleValue.isNotBlank()) { Text("添加规则") }
                        Row { Switch(china, { china = it }); Text("附加中国 IP / 域名直连规则集", Modifier.padding(12.dp)) }
                        OutlinedTextField(remoteSets, { remoteSets = it }, label = { Text("自定义远程规则集（优先于默认规则集）") }, supportingText = { Text("每行：名称 | https://规则集地址 | binary 或 source | direct、proxy 或 reject") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    }
                }
                itemsIndexed(rules) { index, rule ->
                    GlassCard { Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${rule.kind}: ${rule.values}\n→ ${rule.target}", Modifier.weight(1f))
                        TextButton({ rules = rules.filterIndexed { i, _ -> i != index } }) { Text("移除") }
                    } }
                }
            } else item {
                OutlinedTextField(input, { input = it }, label = { Text(when (tool) { "配置校验" -> "sing-box JSON 配置"; "订阅转换" -> "Base64、链接列表、JSON 或 HTTPS 订阅地址"; "节点提取" -> "粘贴文本、网页源码或配置"; "二维码" -> "二维码内容"; else -> "分享链接，每行一个" }) }, minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ importFile.launch("*/*") }) { Text("导入文件") }
                    TextButton({ input = clipboard.getText()?.text.orEmpty() }) { Text("粘贴") }
                    TextButton({ input = ""; output = ""; parsed = emptyList(); qr = null }) { Text("清空") }
                }
            }
            if (tool in listOf("链接 → 配置", "订阅转换")) item {
                Column {
                    Row { Switch(tun, { tun = it }); Text(if (tun) "TUN 入站" else "Mixed 入站", Modifier.padding(12.dp)) }
                    Row { Switch(auto, { auto = it }); Text("自动测速组", Modifier.padding(12.dp)) }
                    Row { Switch(mixin, { mixin = it }); Text("导出 GUI.for.SingBox 混入", Modifier.padding(12.dp)) }
                }
            }
            item {
                if (tool == "订阅转换" && input.trim().startsWith("https://")) OutlinedButton({ task {
                    val url = input.trim().toHttpUrl(); require(url.username.isEmpty() && url.password.isEmpty()) { "地址不可含用户凭据" }
                    input = withContext(Dispatchers.IO) {
                        OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build().newCall(Request.Builder().url(url).build()).execute().use { response ->
                            require(response.isSuccessful) { "订阅下载失败 (${response.code})" }
                            val body = response.body ?: error("空响应")
                            require(body.contentLength() <= 8 * 1024 * 1024) { "订阅太大" }; body.string()
                        }
                    }; message = "已下载，点击转换处理"
                } }, enabled = !busy) { Text("下载此订阅") }
                if (tool == "二维码") OutlinedButton({ importQr.launch("image/*") }) { Text("从图片识别二维码") }
                Button({ task {
                    when (tool) {
                        "配置校验" -> {
                            val issues = withContext(Dispatchers.Default) { SingBoxCore.validate(input) }
                            output = if (issues.isEmpty()) "结构检查通过。仍需目标版本 sing-box check 验证内核兼容性。" else issues.joinToString("\n")
                        }
                        "规则生成器" -> output = withContext(Dispatchers.Default) {
                            val sets = remoteSets.lines().filter { it.isNotBlank() }.map { line -> val cells = line.split('|').map(String::trim); require(cells.size == 4) { "远程规则集需要名称、地址、格式、去向四列" }; RouteSetEntry(cells[0], cells[1], cells[2], cells[3]) }
                            SingBoxCore.forVersion(json("http_clients" to listOf(json("tag" to "http-direct")), "route" to SingBoxCore.route(rules, china, sets)), modern).pretty()
                        }
                        "二维码" -> {
                            qr = withContext(Dispatchers.Default) {
                                val bits = QRCodeWriter().encode(input, BarcodeFormat.QR_CODE, 768, 768, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
                                val pixels = IntArray(bits.width * bits.height) { i -> if (bits[i % bits.width, i / bits.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                                Bitmap.createBitmap(pixels, bits.width, bits.height, Bitmap.Config.ARGB_8888)
                            }; output = input
                        }
                        else -> {
                            val result = withContext(Dispatchers.Default) { if (tool == "节点提取") SingBoxCore.extract(input) else SingBoxCore.subscription(input) }
                            parsed = result.nodes
                            output = withContext(Dispatchers.Default) {
                                when { tool == "节点提取" -> JsonArray(parsed).pretty(); mixin -> json("outbounds" to (parsed + json("type" to "direct", "tag" to "direct"))).pretty(); else -> SingBoxCore.forVersion(SingBoxCore.buildConfig(parsed, tun, auto), modern).pretty() }
                            }
                            message = "解析 ${parsed.size} 个节点" + (result.errors + result.warnings).joinToString("\n", prefix = if (result.errors.isEmpty() && result.warnings.isEmpty()) "" else "\n")
                        }
                    }
                } }, enabled = !busy && (tool == "规则生成器" || input.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text(when (tool) { "配置校验" -> "检查配置"; "规则生成器" -> "生成路由规则"; "二维码" -> "生成二维码"; "节点提取" -> "提取节点"; else -> "转换配置" }) }
            }
            if (message.isNotBlank()) item { SelectionContainer { Text(message, color = MaterialTheme.colorScheme.primary) } }
            qr?.let { bitmap -> item { Image(bitmap.asImageBitmap(), "生成的二维码", Modifier.fillMaxWidth().height(300.dp)); TextButton({ saveQr.launch("qrcode.png") }) { Text("保存二维码图片") } } }
            if (parsed.isNotEmpty()) item {
                OutlinedButton({ task {
                    val nodes = SingBoxCore.dedupe(library + parsed)
                    withContext(Dispatchers.IO) { secret.put("nodes", JsonArray(nodes).toString()) }
                    library = nodes; message = "已加密保存 ${nodes.size} 个节点"
                } }, enabled = !busy) { Text("加入节点库（${library.size}）") }
            }
            if (library.isNotEmpty()) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ output = SingBoxCore.forVersion(SingBoxCore.buildConfig(library, tun, auto), modern).pretty() }) { Text("从节点库生成配置") }
                    TextButton({ task { withContext(Dispatchers.IO) { secret.remove("nodes") }; library = emptyList(); message = "节点库已清空" } }) { Text("清空节点库") }
                } }
                itemsIndexed(library) { index, node -> Row(Modifier.fillMaxWidth()) {
                    Text("${node.str("tag")} · ${node.str("type")}", Modifier.weight(1f))
                    TextButton({ task { val next = library.filterIndexed { i, _ -> i != index }; withContext(Dispatchers.IO) { if (next.isEmpty()) secret.remove("nodes") else secret.put("nodes", JsonArray(next).toString()) }; library = next } }) { Text("移除") }
                } }
            }
            if (output.isNotBlank()) item {
                Row { TextButton({ clipboard.setText(AnnotatedString(output)); message = "已复制" }) { Text("复制结果") }; TextButton({ saveFile.launch(if (tool == "规则生成器") "route.json" else "sing-box.json") }) { Text("导出文件") } }
                SelectionContainer { Text(output, fontFamily = FontFamily.Monospace, modifier = Modifier.horizontalScroll(rememberScrollState())) }
            }
        }
    }
}
