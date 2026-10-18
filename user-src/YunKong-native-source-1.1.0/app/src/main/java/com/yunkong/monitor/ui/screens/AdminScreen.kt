package com.yunkong.monitor.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.*
import com.yunkong.monitor.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private val serverFields = "name server_group tags note price billing_cycle auto_renewal currency expire_date traffic_limit traffic_calc_type interface reset_day collect_interval report_interval wss_report_interval connection_mode ping_mode auto_update custom_ct custom_cu custom_cm custom_bd rx_correction tx_correction offline_notify_disabled is_hidden".split(' ')
private val fieldLabels = mapOf("name" to "名称", "server_group" to "分组", "tags" to "标签", "note" to "备注", "price" to "价格", "billing_cycle" to "计费周期", "currency" to "货币", "expire_date" to "到期日期", "traffic_limit" to "流量额度", "traffic_calc_type" to "计费方向 total/dl/ul/max", "reset_day" to "重置日 0–31", "collect_interval" to "采集间隔", "report_interval" to "HTTP 上报间隔", "wss_report_interval" to "WSS 上报间隔", "is_hidden" to "隐藏 0/1", "interface" to "统计网卡", "connection_mode" to "连接模式 auto/http", "ping_mode" to "探测 tcp/icmp", "site_title" to "站点标题", "tg_bot_token" to "通知渠道配置", "tg_chat_id" to "接收会话", "notification_timezone" to "通知时区", "password" to "新管理员密码（留空保留）", "username" to "管理员账号")

private fun editableServer(server: JsonObject): JsonObject = JsonObject(serverFields.associateWith { key ->
    server[key] ?: when (key) {
        "reset_day" -> JsonPrimitive(1); "collect_interval" -> JsonPrimitive(1); "report_interval" -> JsonPrimitive(60); "wss_report_interval" -> JsonPrimitive(2)
        "connection_mode" -> JsonPrimitive("auto"); "ping_mode" -> JsonPrimitive("tcp"); "traffic_calc_type" -> JsonPrimitive("total")
        else -> JsonPrimitive("")
    }
} + mapOf("id" to (server["id"] ?: JsonPrimitive(""))))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("服务器") }
    var servers by remember { mutableStateOf(emptyList<JsonObject>()) }
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var edit by remember { mutableStateOf<JsonObject?>(null) }
    var editingSettings by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""
        scope.launch {
            try { block() }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; message = (e.message ?: "操作失败") + "。写入结果不明时请刷新核实，避免重复提交。" }
            finally { busy = false }
        }
    }
    suspend fun reload() {
        servers = vm.admin("list").array("servers")?.mapNotNull { it as? JsonObject }.orEmpty()
        settings = vm.admin("get_settings").obj("settings")
        selected = emptySet()
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) task { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray()) } ?: error("文件不可写") }; message = "已导出服务器配置" }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) task {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("无法读取") }
            val parsed = API_JSON.parseToJsonElement(text)
            val rows = (parsed as? JsonArray) ?: parsed.array("servers") ?: error("需要服务器数组")
            confirm = "导入 ${rows.size} 台服务器的配置？现有相同 ID 会由服务端跳过。" to {
                task { result = vm.admin("import_servers", json("servers" to rows)).pretty(); reload(); vm.refresh() }
            }
        }
    }
    LaunchedEffect(ui.loggedIn) { if (ui.loggedIn) task { reload() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(title = { Text("面板管理") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }, actions = {
            TextButton({ task { reload() } }, enabled = ui.loggedIn && !busy) { Text("刷新") }
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!ui.loggedIn) { Text("请在「我的」中登录管理员账号。公开站点同样需要登录后才能管理。", Modifier.padding(24.dp)); return@Column }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("服务器", "站点设置", "通知", "用量与维护", "网页主题").forEach { label -> FilterChip(page == label, { page = label; result = "" }, { Text(label) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.error) }
            when (page) {
                "服务器" -> {
                    item {
                        OutlinedTextField(newName, { newName = it }, label = { Text("新服务器名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button({ task { vm.admin("add", json("name" to newName, "server_group" to "Default")); newName = ""; reload(); vm.refresh() } }, enabled = !busy && newName.isNotBlank()) { Text("新增") }
                            TextButton({ importFile.launch("application/json") }, enabled = !busy) { Text("导入") }
                            TextButton({ task { exportText = vm.admin("export_servers").pretty(); exportFile.launch("cf-servers.json") } }, enabled = !busy) { Text("导出") }
                            TextButton({ task { vm.admin("save_order", json("orders" to servers.map { it.str("id") })); message = "排序已保存"; vm.refresh() } }, enabled = !busy && servers.isNotEmpty()) { Text("保存排序") }
                        }
                        if (selected.isNotEmpty()) TextButton({ confirm = "删除选中的 ${selected.size} 台服务器？此操作不可撤销。" to { task { vm.admin("batch_delete", json("ids" to selected.toList())); reload(); vm.refresh() } } }, enabled = !busy) { Text("删除所选 (${selected.size})", color = MaterialTheme.colorScheme.error) }
                    }
                    items(servers, key = { it.str("id").orEmpty() }) { server ->
                        val id = server.str("id").orEmpty()
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Checkbox(id in selected, { checked -> selected = if (checked) selected + id else selected - id })
                                    Column(Modifier.weight(1f).padding(top = 8.dp)) { Text(server.str("name").orEmpty(), style = MaterialTheme.typography.titleMedium); Text("${server.str("server_group")} · ${if (server.bool("is_hidden") == true) "隐藏" else "显示"}", style = MaterialTheme.typography.labelSmall) }
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    TextButton({ editingSettings = false; edit = editableServer(server) }, enabled = !busy) { Text("编辑 / 采集配置") }
                                    TextButton({ task {
                                        val payload = JsonObject(editableServer(server) + ("is_hidden" to JsonPrimitive(if (server.bool("is_hidden") == true) "0" else "1")))
                                        vm.admin("edit", payload); reload(); vm.refresh()
                                    } }, enabled = !busy) { Text(if (server.bool("is_hidden") == true) "显示" else "隐藏") }
                                    val index = servers.indexOf(server)
                                    TextButton({ servers = servers.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = index > 0 && !busy) { Text("上移") }
                                    TextButton({ servers = servers.toMutableList().apply { add(index + 1, removeAt(index)) } }, enabled = index < servers.lastIndex && !busy) { Text("下移") }
                                    TextButton({ confirm = "删除「${server.str("name")}」？此操作不可撤销。" to { task { vm.admin("delete", json("id" to id)); reload(); vm.refresh() } } }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                "站点设置", "通知" -> {
                    item {
                        Text(if (page == "通知") "通知渠道、SMTP/Webhook、离线与资源告警、到期提醒均由 CF 后端执行。" else "完整保留服务端设置对象。外观设置保存时整体回传，避免部分更新覆盖原配置。")
                        Button({ editingSettings = true; edit = settings?.let { JsonObject(it.filterKeys { k -> k != "password_configured" } + ("password" to JsonPrimitive(""))) } }, enabled = !busy && settings != null) { Text("编辑完整设置") }
                        if (page == "通知") OutlinedButton({ confirm = "向当前设置中的接收者发送一条测试通知？" to {
                            task {
                                val fields = settings ?: error("请先读取设置")
                                result = vm.admin("send_test_notification", fields).pretty()
                            }
                        } }, enabled = !busy && settings != null) { Text("发送测试通知") }
                    }
                    settings?.let { current -> items(current.entries.filter { (k, _) -> !Regex("secret|token|password", RegexOption.IGNORE_CASE).containsMatchIn(k) }) { (k, v) ->
                        Text("${fieldLabels[k] ?: k}\n${if (v is JsonPrimitive) v.content else v.toString()}", style = MaterialTheme.typography.bodyMedium)
                    } }
                }
                "用量与维护" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button({ task { result = vm.admin("d1_usage").pretty() } }, enabled = !busy) { Text("查询 D1 / Workers / DO 用量") }
                        OutlinedButton({ confirm = "执行数据库结构升级？将补齐 CF 后端所需字段。" to { task { result = vm.maintenance("/updateDatabase").pretty() } } }, enabled = !busy) { Text("升级数据库") }
                        OutlinedButton({ confirm = "清空全部历史监控数据？服务器配置保留，历史不可恢复。" to { task { result = vm.maintenance("/clearHistory").pretty() } } }, enabled = !busy) { Text("清空历史数据", color = MaterialTheme.colorScheme.error) }
                    }
                }
                "网页主题" -> item {
                    Text("管理 CF 网站主题配置。原生仪表盘保持 CF 终端布局；网页主题中的 HTML、CSS 和 JavaScript 不在 App 中执行。")
                    Button({ task { result = vm.themes().pretty() } }, enabled = !busy) { Text("读取主题商店") }
                    TextButton({ editingSettings = true; edit = settings?.let { JsonObject(it.filterKeys { k -> k != "password_configured" }) } }, enabled = !busy && settings != null) { Text("配置网页主题地址与主题选项") }
                }
            }
            if (result.isNotEmpty()) item {
                TextButton({ clipboard.setText(AnnotatedString(result)) }) { Text("复制结果") }
                SelectionContainer { Text(result) }
            }
        }
    }
    edit?.let { snapshot -> NativeJsonEditor(snapshot, if (editingSettings) "完整站点设置" else "服务器配置", onDismiss = { edit = null }) { updated ->
        edit = null
        task { if (editingSettings) vm.admin("save_settings", json("settings" to updated)) else vm.admin("edit", updated); reload(); vm.refresh(); message = "已保存" }
    } }
    confirm?.let { (text, action) -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("确认操作") }, text = { Text(text) }, confirmButton = { TextButton({ confirm = null; action() }) { Text("确认") } }, dismissButton = { TextButton({ confirm = null }) { Text("取消") } }) }
}

/** Typed generic native editor retains every server-side key, including future settings. */
@Composable
private fun NativeJsonEditor(initial: JsonObject, title: String, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    var fields by remember(initial) { mutableStateOf(initial.mapValues { (_, v) -> if (v is JsonPrimitive && v !is JsonNull) v.content else v.toString() }) }
    var error by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Row { Checkbox(reveal, { reveal = it }); Text("显示敏感字段", Modifier.padding(top = 12.dp)) }
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(fields.keys.filter { it != "id" }.toList(), key = { it }) { key ->
                    val sensitive = Regex("secret|token|password", RegexOption.IGNORE_CASE).containsMatchIn(key)
                    OutlinedTextField(fields[key].orEmpty(), { fields = fields + (key to it) }, label = { Text(fieldLabels[key] ?: key) }, supportingText = { if (fieldLabels.containsKey(key)) Text(key) },
                        visualTransformation = if (sensitive && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(newKey, { newKey = it }, label = { Text("补充服务端设置字段") })
                    TextButton({ if (newKey.isNotBlank()) { fields = fields + (newKey.trim() to ""); newKey = "" } }) { Text("添加字段") }
                }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({
        runCatching {
            val result = JsonObject(fields.mapValues { (k, v) ->
                val old = initial[k]
                when {
                    k in listOf("reset_day", "collect_interval", "report_interval", "wss_report_interval") -> JsonPrimitive(v.toIntOrNull() ?: error("$k 必须为整数"))
                    old is JsonObject || old is JsonArray -> Json.parseToJsonElement(v)
                    old is JsonNull && v == "null" -> JsonNull
                    old is JsonPrimitive && !old.isString -> Json.parseToJsonElement(v)
                    else -> JsonPrimitive(v)
                }
            })
            onSave(result)
        }.onFailure { error = it.message.orEmpty() }
    }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
