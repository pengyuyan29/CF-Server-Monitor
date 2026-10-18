package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunkong.monitor.data.*
import com.yunkong.monitor.ui.components.*
import com.yunkong.monitor.ui.theme.*
import com.yunkong.monitor.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.Locale

@Composable
fun ServerRings(cpu: Double?, ram: Double?, disk: Double?) {
    val palette = term()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("CPU" to cpu, "RAM" to ram, "DISK" to disk).forEach { (label, number) ->
            val color = when { number == null -> palette.textSec; number >= 95 -> palette.red; number >= 80 -> palette.yellow; number >= 50 -> palette.blue; else -> palette.green }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                        drawArc(palette.border, 0f, 360f, false, style = Stroke(5.dp.toPx()))
                        drawArc(color, -90f, ((number ?: 0.0).coerceIn(0.0, 100.0) * 3.6).toFloat(), false, style = Stroke(5.dp.toPx()))
                    }
                    Text(Format.percent(number), color = palette.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text(label, color = palette.textSec, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ServerLatency(server: ServerState, config: JsonObject?) {
    val colors = listOf(Green, BrandBlue, Purple, Amber)
    val fields = listOf("ct" to "电信", "cu" to "联通", "cm" to "移动", "bd" to "BGP")
    val exists = fields.any { (key, _) -> server.containsKey("ping_$key") && server["ping_$key"] != JsonPrimitive(false) }
    if (!exists && server.array("ping").isNullOrEmpty()) return
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        fields.forEachIndexed { i, (key, label) ->
            val raw = server["ping_$key"]
            if (raw != null && raw != JsonPrimitive(false)) Column {
                Text(config.str("custom_${key}_name")?.ifBlank { null } ?: label, fontSize = 10.sp, color = colors[i])
                Text(if (raw is JsonNull) "超时" else "${server.num("ping_$key")?.toInt() ?: "—"}ms", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                server.num("loss_$key")?.let { Text("丢包 ${Format.percent(it, 1)}", fontSize = 9.sp, color = if (it > 0) Red else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (config.bool("show_three_net_details") != false) {
        val points = server.array("ping").orEmpty()
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LineChart(fields.take(3).mapIndexed { i, (key, label) -> ChartSeries(points.mapNotNull { row -> row.long("ts")?.let { ChartPoint(it, row.num(key)) } }, colors[i], label) }, height = 58, yFormat = { "${it.toInt()}ms" })
        }
    }
}

@Composable
fun ServerTable(servers: List<ServerState>, config: JsonObject?, onOpen: (String) -> Unit) {
    val t = term()
    val headers = buildList { addAll(listOf("服务器", "状态", "CPU", "内存", "磁盘", "负载", "下行 / 上行", "月下行 / 上行")); if (config.bool("show_tf") != false) add("额度用量"); if (config.bool("show_price") != false) add("价格"); if (config.bool("show_expire") != false) add("到期") }
    val scroll = rememberScrollState()
    Column(Modifier.horizontalScroll(scroll).padding(16.dp)) {
        Row(Modifier.background(t.surface).padding(8.dp)) { headers.forEach { Text(it, Modifier.width(if (it in listOf("服务器", "下行 / 上行", "月下行 / 上行")) 170.dp else 110.dp), fontSize = 11.sp, color = t.textSec) } }
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            items(servers, key = { it.str("id").orEmpty() }) { server ->
                fun percent(used: String, total: String): String = server.num(total)?.takeIf { it > 0 }?.let { Format.percent((server.num(used) ?: 0.0) / it * 100, 1) } ?: "—"
                val values = buildList {
                    add(server.str("name").orEmpty()); add(if (server.isOnline()) "在线" else "离线"); add(Format.percent(server.num("cpu"), 1)); add(percent("ram_used", "ram_total")); add(percent("disk_used", "disk_total")); add(server.str("load_avg") ?: "—"); add("${Format.speed(server.num("net_in_speed"))}\n${Format.speed(server.num("net_out_speed"))}"); add("${Format.bytes(server.num("net_rx_monthly"))}\n${Format.bytes(server.num("net_tx_monthly"))}")
                    if (config.bool("show_tf") != false) add(server.trafficUsagePercent()?.let { Format.percent(it, 2) } ?: "∞")
                    if (config.bool("show_price") != false) add("${server.str("currency").orEmpty()}${server.str("price") ?: "—"}")
                    if (config.bool("show_expire") != false) add(server.str("expire_date")?.ifBlank { "—" } ?: "—")
                }
                Row(Modifier.clickable { onOpen(server.str("id").orEmpty()) }.padding(8.dp)) { values.forEachIndexed { index, value -> Text(value, Modifier.width(if (index in listOf(0, 6, 7)) 170.dp else 110.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (index == 1 && server.isOnline()) t.green else t.text, maxLines = 2) } }
            }
        }
    }
}

private data class MapPolygon(val region: String, val rings: List<List<Pair<Float, Float>>>)

@Composable
fun ServerMap(servers: List<ServerState>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val t = term()
    var mapError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val polygons by produceState(emptyList<MapPolygon>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val root = API_JSON.parseToJsonElement(context.assets.open("world.zh.json").bufferedReader().use { it.readText() }) as JsonObject
                root.array("features").orEmpty().flatMap { feature ->
                    val geometry = feature.obj("geometry") ?: return@flatMap emptyList()
                    val region = feature.obj("properties").str("iso_a2").orEmpty().lowercase()
                    val coordinates = geometry.array("coordinates").orEmpty()
                    val raw = if (geometry.str("type") == "Polygon") listOf(JsonArray(coordinates)) else coordinates
                    raw.mapNotNull { poly -> (poly as? JsonArray)?.let { rings -> MapPolygon(region, rings.mapNotNull { ring -> (ring as? JsonArray)?.mapNotNull { p ->
                        val xy = p as? JsonArray ?: return@mapNotNull null
                        if (xy.size < 2) null else ((xy[0] as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null) to ((xy[1] as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null)
                    } }) } }
                }
            }.onFailure { mapError = "地图资源读取失败：${it.message}" }.getOrDefault(emptyList())
        }
    }
    // Approximate region centres only; source does not expose precise server coordinates.
    val centres = remember(polygons) {
        polygons.groupBy { it.region }.mapNotNull { (region, parts) ->
            val ring = parts.mapNotNull { it.rings.firstOrNull() }.maxByOrNull { points ->
                if (points.size < 3) 0.0 else kotlin.math.abs(points.zipWithNext().sumOf { (a, b) -> (a.first * b.second - b.first * a.second).toDouble() })
            }
            if (ring.isNullOrEmpty()) null else region to ((ring.minOf { it.first } + ring.maxOf { it.first }) / 2f to (ring.minOf { it.second } + ring.maxOf { it.second }) / 2f)
        }.toMap() + mapOf("cn" to (104f to 35f), "hk" to (114f to 22f), "tw" to (121f to 24f), "us" to (-99f to 39f), "ru" to (100f to 60f))
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(6.dp), color = t.surface) {
                Canvas(Modifier.fillMaxWidth().height(230.dp).graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                    .pointerInput(Unit) { detectTransformGestures { _, amount, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 8f); pan += amount } }) {
                    fun point(x: Float, y: Float) = Offset((x + 180f) / 360f * size.width, (85f - y) / 170f * size.height)
                    polygons.forEach { polygon ->
                        val path = Path()
                        path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        polygon.rings.forEach { ring -> ring.forEachIndexed { index, (x, y) -> val p = point(x, y); if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }; path.close() }
                        drawPath(path, t.border); drawPath(path, t.textSec.copy(alpha = .4f), style = Stroke(.5.dp.toPx()))
                    }
                    servers.groupBy { it.str("region").orEmpty().lowercase() }.forEach { (region, members) -> centres[region]?.let { (x, y) ->
                        drawCircle(if (members.any { it.isOnline() }) t.green else t.red, 4.dp.toPx(), point(x, y))
                    } }
                }
            }
            Row { TextButton({ zoom = (zoom * 1.4f).coerceAtMost(8f) }) { Text("放大") }; TextButton({ zoom = (zoom / 1.4f).coerceAtLeast(1f) }) { Text("缩小") }; TextButton({ zoom = 1f; pan = Offset.Zero }) { Text("复位") } }
            mapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("按服务器地区定位，非机器精确坐标。下方列出全部地区与服务器。", fontSize = 10.sp, color = t.textSec)
        }
        servers.groupBy { it.str("region").orEmpty() }.forEach { (region, members) ->
            item { Text("${if (region.isBlank()) "未知地区" else Locale("", region.uppercase()).getDisplayCountry(Locale.CHINA).ifBlank { region }} · ${members.size}", color = t.green) }
            items(members, key = { it.str("id").orEmpty() }) { server ->
                Surface(Modifier.fillMaxWidth().clickable { onOpen(server.str("id").orEmpty()) }, color = t.surface, shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(14.dp)) { Text(server.str("name").orEmpty(), Modifier.weight(1f), color = t.text); Text(if (server.isOnline()) "在线" else "离线", color = if (server.isOnline()) t.green else t.red) }
                }
            }
        }
    }
}
