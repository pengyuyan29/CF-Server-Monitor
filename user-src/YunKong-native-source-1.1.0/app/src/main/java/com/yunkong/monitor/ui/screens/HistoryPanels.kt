package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yunkong.monitor.data.*
import com.yunkong.monitor.ui.components.*
import com.yunkong.monitor.ui.theme.*
import com.yunkong.monitor.util.Format
import kotlinx.serialization.json.*

private data class HistoryPanel(val title: String, val series: List<ChartSeries>, val maximum: Double? = null, val format: (Double) -> String = { "%.1f".format(it) })

fun LazyListScope.extraHistoryPanels(rows: List<JsonObject>) {
    val colors = listOf(Green, BrandBlue, Purple, Amber, Cyan, Pink)
    fun series(label: String, index: Int, value: (JsonObject) -> Double?) = ChartSeries(rows.mapNotNull { row -> row.long("timestamp")?.let { ChartPoint(it, value(row)) } }, colors[index % colors.size], label)
    fun field(label: String, key: String, index: Int = 0) = series(label, index) { it.num(key) }
    fun percent(used: String, total: String, row: JsonObject) = row.num(total)?.takeIf { it > 0 }?.let { row.num(used)?.div(it)?.times(100) }
    val panels = mutableListOf(
        HistoryPanel("交换分区", listOf(series("SWAP", 3) { percent("swap_used", "swap_total", it) }), 100.0, { Format.percent(it, 1) }),
        HistoryPanel("磁盘用量", listOf(series("DISK", 4) { percent("disk_used", "disk_total", it) }), 100.0, { Format.percent(it, 1) }),
        HistoryPanel("系统负载", (0..2).map { index -> series(listOf("1 分钟", "5 分钟", "15 分钟")[index], index) { row -> row.str("load_avg")?.trim()?.split(Regex("[\\s,]+"))?.getOrNull(index)?.toDoubleOrNull() } }),
        HistoryPanel("连接数", listOf(field("TCP", "tcp_conn"), field("UDP", "udp_conn", 1)), format = { "${it.toInt()}" }),
        HistoryPanel("进程数", listOf(field("进程", "processes", 2)), format = { "${it.toInt()}" }),
        HistoryPanel("磁盘 IOPS", listOf(field("读", "disk_read_iops"), field("写", "disk_write_iops", 1)), format = { "${it.toInt()} IOPS" }),
        HistoryPanel("磁盘等待", listOf(field("延迟", "disk_await_ms", 3)), format = { "%.1f ms".format(it) }),
        HistoryPanel("磁盘繁忙率", listOf(field("繁忙", "disk_util", 4)), 100.0, { Format.percent(it, 1) })
    )
    val providers = listOf("ct" to "电信", "cu" to "联通", "cm" to "移动", "bd" to "BGP", "node_1" to "节点 1", "node_2" to "节点 2", "node_3" to "节点 3", "node_4" to "节点 4")
    panels += HistoryPanel("网络延迟", providers.mapIndexed { index, (key, label) -> field(label, "ping_$key", index) }, format = { "%.0f ms".format(it) })
    panels += HistoryPanel("网络丢包", providers.mapIndexed { index, (key, label) -> field(label, "loss_$key", index) }, 100.0, { Format.percent(it, 1) })
    fun gpuList(row: JsonObject): List<JsonObject> {
        val raw = row["gpu_info"]
        val parsed = if (raw is JsonPrimitive) runCatching { API_JSON.parseToJsonElement(raw.content) }.getOrNull() else raw
        return (parsed as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
    val devices = rows.flatMap(::gpuList).distinctBy { it.str("id") ?: it.str("name") }
    listOf("info" to "GPU 占用", "mem_used" to "GPU 显存", "sm_clock" to "GPU SM 频率", "power" to "GPU 功耗").forEach { (key, label) ->
        panels += HistoryPanel(label, devices.mapIndexed { index, device -> series(device.str("name") ?: "GPU ${device.str("id")}", index) { row -> gpuList(row).find { it.str("id") == device.str("id") }?.num(key) } },
            if (key == "info") 100.0 else null,
            when (key) { "info" -> { v -> Format.percent(v, 1) }; "mem_used" -> { v -> Format.bytes(v * 1024 * 1024) }; "sm_clock" -> { v -> "${v.toInt()} MHz" }; else -> { v -> "%.1f W".format(v) } })
    }
    panels.filter { panel -> panel.series.any { series -> series.points.any { it.y != null } } }.forEach { panel -> item(key = panel.title) { HistoryPanelView(panel) } }
}

@Composable
private fun HistoryPanelView(panel: HistoryPanel) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) { Text(panel.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); TextButton({ expanded = true }, contentPadding = PaddingValues(0.dp)) { Text("展开") } }
            LineChart(panel.series, yMax = panel.maximum, yFormat = panel.format)
        }
    }
    if (expanded) Dialog({ expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) { Text(panel.title, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(16.dp)); LineChart(panel.series, yMax = panel.maximum, yFormat = panel.format, height = 320); TextButton({ expanded = false }) { Text("收起") } }
        }
    }
}
