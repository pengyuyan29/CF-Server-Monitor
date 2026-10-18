package com.yunkong.monitor.ui.screens

import android.graphics.BitmapFactory
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.*
import com.yunkong.monitor.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Base64

private data class ReadingBlock(val kind: String, val body: String, val title: String = "", val id: String = "")

private fun readingBlocks(html: String): List<ReadingBlock> {
    val out = mutableListOf<ReadingBlock>()
    fun visit(e: Element) {
        when (e.tagName()) {
            "script", "style", "iframe", "form" -> Unit
            "pre" -> out += ReadingBlock("code", e.wholeText().trimEnd())
            "img" -> out += ReadingBlock("image", e.attr("src"), e.attr("alt"))
            "h1", "h2", "h3", "h4", "h5", "h6" -> out += ReadingBlock("heading", e.text(), id = e.id())
            "table" -> out += ReadingBlock("table", e.select("tr").joinToString("\n") { row -> row.select("th,td").joinToString("  │  ") { it.text() } })
            "ul", "ol" -> e.children().forEachIndexed { i, li ->
                out += ReadingBlock("html", "${if (e.tagName() == "ol") "${i + 1}." else "•"} ${li.html()}")
                li.select("img").forEach { out += ReadingBlock("image", it.attr("src"), it.attr("alt")) }
            }
            "p", "blockquote" -> {
                if (e.text().isNotBlank()) out += ReadingBlock("html", e.html())
                e.select("img").forEach { out += ReadingBlock("image", it.attr("src"), it.attr("alt")) }
            }
            "hr" -> out += ReadingBlock("divider", "")
            else -> if (e.children().isEmpty()) {
                if (e.text().isNotBlank()) out += ReadingBlock("html", e.html())
            } else e.children().forEach(::visit)
        }
    }
    Jsoup.parseBodyFragment(html).body().children().forEach(::visit)
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogScreen(vm: BlogViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var base by rememberSaveable(ui.baseUrl) { mutableStateOf(ui.baseUrl) }
    // Password deliberately excluded from SavedStateHandle / rememberSaveable.
    var password by remember { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(false) }
    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { vm.importArchive(uri); local = true } }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var section by remember { mutableStateOf("手记") }
    var outline by remember { mutableStateOf(false) }
    val blocks by produceState(emptyList<ReadingBlock>(), ui.html) {
        value = withContext(Dispatchers.Default) { readingBlocks(ui.html) }
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(ui.selected != null) { vm.back() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopAppBar(
            title = { Text(ui.selected?.str("title") ?: "一隅 · 手记", maxLines = 1) },
            navigationIcon = { if (ui.selected != null) IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回文章列表") } },
            actions = {
                if (blocks.isNotEmpty()) IconButton(onClick = { outline = true }) { Icon(Icons.Rounded.List, "文章目录") }
                if (ui.unlocked) IconButton(onClick = { password = ""; search = ""; vm.lock() }) { Icon(Icons.Rounded.Lock, "锁定博客") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (!ui.unlocked) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("留一隅，记所见。", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("输入已发布的博客地址和访问密码，在本机解锁阅读。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(base, { base = it }, label = { Text("博客 Pages 地址") }, singleLine = true, enabled = !(local && ui.localAvailable), modifier = Modifier.fillMaxWidth())
                OutlinedButton({ importArchive.launch("application/zip") }, enabled = !ui.busy) { Text("导入加密博客 ZIP") }
                if (ui.localAvailable) Row { Switch(local, { local = it }); Text("读取本机加密博客", Modifier.padding(12.dp)) }
                OutlinedTextField(password, { password = it }, label = { Text("访问密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val secret = password.toCharArray(); password = ""; vm.unlock(base, secret, local && ui.localAvailable) }, enabled = !ui.busy && (base.isNotBlank() || local) && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("解锁手记") }
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else if (ui.selected != null) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(state = list, contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ui.selected.str("title").orEmpty(), style = MaterialTheme.typography.headlineMedium)
                        Text("${ui.selected.str("date").orEmpty()} · ${ui.selected.str("category").orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(blocks) { block -> ReadingBlockView(block) }
                if (ui.error != null) item { TextButton(onClick = { ui.selected?.let(vm::open) }) { Text("重新加载文章") } }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("手记", "归档", "关于").forEach { name -> FilterChip(section == name, { section = name }, { Text(name) }) }
            }
            if (section == "关于") {
                SelectionContainer { Text("这里是一隅，一个记录技术与实践的地方。\n\n内容来自你已发布的加密博客。密码与解密内容仅保留在当前进程内存，点击锁定即可关闭阅读。", modifier = Modifier.padding(24.dp), lineHeight = 28.sp) }
            } else {
                OutlinedTextField(search, { search = it }, label = { Text("搜索标题、标签与全文") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("全部") + ui.posts.mapNotNull { it.str("category") }.distinct()).forEach { name -> FilterChip(category == name, { category = name }, { Text(name) }) }
                }
                val filtered = ui.posts.filter { post ->
                    (category == "全部" || post.str("category") == category) &&
                        listOf("title", "subtitle", "description", "text").any { post.str(it).orEmpty().contains(search, true) } ||
                        (category == "全部" || post.str("category") == category) && post.array("tags").toString().contains(search, true)
                }.sortedByDescending { it.str("date") }
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (filtered.isEmpty()) item { Text("没有找到相关文章", modifier = Modifier.padding(16.dp)) }
                    var year = ""
                    filtered.forEach { post ->
                        if (section == "归档" && year != post.str("date").orEmpty().take(4)) {
                            year = post.str("date").orEmpty().take(4)
                            val heading = year
                            item { Text(heading, style = MaterialTheme.typography.headlineSmall) }
                        }
                        item(key = post.str("slug")) {
                            GlassCard(Modifier.fillMaxWidth().clickable { vm.open(post) }) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(post.str("title").orEmpty(), style = MaterialTheme.typography.titleMedium)
                                    Text("${post.str("date").orEmpty()} · ${post.str("category").orEmpty()} · ${post.int("minutes") ?: 1} 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (section == "手记") Text(post.str("description").orEmpty(), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (outline) AlertDialog(onDismissRequest = { outline = false }, title = { Text("文章目录") }, text = {
        LazyColumn { blocks.forEachIndexed { index, block -> if (block.kind == "heading") item {
            TextButton(onClick = { outline = false; scope.launch { list.animateScrollToItem(index + 1) } }) { Text(block.body) }
        } } }
    }, confirmButton = { TextButton(onClick = { outline = false }) { Text("关闭") } })
}

@Composable
private fun ReadingBlockView(block: ReadingBlock) {
    val clipboard = LocalClipboardManager.current
    when (block.kind) {
        "heading" -> Text(block.body, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        "divider" -> HorizontalDivider()
        "code", "table" -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(block.body)) }) { Text("复制${if (block.kind == "code") "代码" else "表格"}") }
                SelectionContainer { Text(block.body, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.horizontalScroll(rememberScrollState())) }
            }
        }
        "image" -> PrivateArticleImage(block.body, block.title)
        else -> {
            val color = MaterialTheme.colorScheme.onSurface.toArgb()
            val link = MaterialTheme.colorScheme.primary.toArgb()
            AndroidView(factory = { TextView(it).apply { textSize = 17f; setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance(); setLineSpacing(0f, 1.5f) } },
                update = { it.setTextColor(color); it.setLinkTextColor(link); it.text = Html.fromHtml(block.body, Html.FROM_HTML_MODE_COMPACT) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrivateArticleImage(source: String, alt: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, source) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                require(source.startsWith("data:image/") && source.contains(";base64,"))
                val bytes = Base64.getDecoder().decode(source.substringAfter(","))
                require(bytes.size <= 16 * 1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 4096) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull()
        }
    }
    var expanded by remember { mutableStateOf(false) }
    bitmap?.let { bmp ->
        Image(bmp.asImageBitmap(), alt, Modifier.fillMaxWidth().clickable { expanded = true })
        if (expanded) Dialog({ expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var zoom by remember { mutableFloatStateOf(1f) }
            var x by remember { mutableFloatStateOf(0f) }
            var y by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTransformGestures { _, pan, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 8f); x += pan.x; y += pan.y } }) {
                Image(bmp.asImageBitmap(), alt, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = x; translationY = y })
                TextButton({ expanded = false }, Modifier.statusBarsPadding()) { Text("关闭", color = Color.White) }
            }
        }
    } ?: Text(alt.ifBlank { "图片加载中或格式不支持" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
