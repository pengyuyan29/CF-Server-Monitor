package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.API_JSON
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.data.ServerState
import com.yunkong.monitor.data.int
import com.yunkong.monitor.data.bool
import com.yunkong.monitor.data.isOnline
import com.yunkong.monitor.data.long
import com.yunkong.monitor.data.num
import com.yunkong.monitor.data.str
import com.yunkong.monitor.ui.components.Badge
import com.yunkong.monitor.ui.components.ChartPoint
import com.yunkong.monitor.ui.components.ChartSeries
import com.yunkong.monitor.ui.components.GlassCard
import com.yunkong.monitor.ui.components.InfoItem
import com.yunkong.monitor.ui.components.LineChart
import com.yunkong.monitor.ui.theme.Amber
import com.yunkong.monitor.ui.theme.BrandBlue
import com.yunkong.monitor.ui.theme.Cyan
import com.yunkong.monitor.ui.theme.Green
import com.yunkong.monitor.ui.theme.Purple
import com.yunkong.monitor.util.Format
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: MonitorViewModel, id: String, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val detail by vm.detail.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val server = detail.server

    LaunchedEffect(id) {
        vm.openDetail(id)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(s.primary.copy(alpha = 0.08f), Color.Transparent, s.primary.copy(alpha = 0.05f))
                )
            )
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    server?.str("name") ?: "详情",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = s.onSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        if (detail.loading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = s.primary)
                Spacer(Modifier.height(10.dp))
                Text("加载中…", color = s.onSurfaceVariant, fontSize = 13.sp)
            }
            return@Column
        }

        if (detail.error != null && server == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(detail.error ?: "加载失败", color = s.error, fontSize = 13.sp)
            }
            return@Column
        }

        server ?: return@Column

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 基本信息
            item { HeaderCard(server, ui.config) }
            // 实时状态
            item { LiveCard(server, ui.config) }
            // 图表
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("历史指标", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                            Spacer(Modifier.weight(1f))
                            if (detail.historyLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = s.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (h in listOf(1.0 / 6, 0.5, 1.0, 6.0, 12.0, 24.0, 48.0, 96.0, 168.0)) {
                                val label = when (h) {
                                    1.0 / 6 -> "10分钟"; 0.5 -> "30分钟"; 1.0 -> "1小时"; 6.0 -> "6小时"; 12.0 -> "12小时"; 24.0 -> "24小时"; 48.0 -> "2天"; 96.0 -> "4天"; else -> "7天"
                                }
                                val selected = detail.hours == h
                                Box(
                                    Modifier
                                        .background(
                                            if (selected) s.primary else s.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { vm.setHours(h) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        color = if (selected) s.onPrimary else s.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        detail.error?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = s.error, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CPU 占用率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        ChartPoint(t, row.num("cpu"))
                                    },
                                    BrandBlue, "CPU"
                                )
                            ),
                            yMax = 100.0
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("内存占用率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        val total = row.num("ram_total")
                                        val used = row.num("ram_used")
                                        if (total == null || total <= 0 || used == null) ChartPoint(t, null)
                                        else ChartPoint(t, used / total * 100.0)
                                    },
                                    Cyan, "内存"
                                )
                            ),
                            yMax = 100.0
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("网络速率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        ChartPoint(t, row.num("net_in_speed"))
                                    },
                                    BrandBlue, "下行"
                                ),
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        ChartPoint(t, row.num("net_out_speed"))
                                    },
                                    Purple, "上行"
                                )
                            ),
                            yFormat = { v -> Format.bytes(v, 0) }
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("磁盘 IO", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        ChartPoint(t, row.num("disk_read_bps"))
                                    },
                                    Cyan, "读取"
                                ),
                                ChartSeries(
                                    detail.history.mapNotNull { row ->
                                        val t = row.long("timestamp") ?: return@mapNotNull null
                                        ChartPoint(t, row.num("disk_write_bps"))
                                    },
                                    Amber, "写入"
                                )
                            ),
                            yFormat = { v -> Format.bytes(v, 0) }
                        )
                    }
                }
            }
            extraHistoryPanels(detail.history)
        }
    }
}

@Composable
private fun HeaderCard(server: ServerState, config: JsonObject?) {
    val s = MaterialTheme.colorScheme
    val online = server.isOnline()
    val boot = server.str("boot_time")?.toLongOrNull()
    val uptime = if (boot != null && boot > 0) System.currentTimeMillis() - boot else null

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(if (online) Green else s.onSurfaceVariant, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (online) "在线" else "离线",
                    fontSize = 12.sp,
                    color = if (online) Green else s.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                val region = server.str("region")
                if (!region.isNullOrBlank() && region != "unknown") Badge(region.uppercase(), BrandBlue)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoItem("系统", server.str("os") ?: "-", Modifier.weight(1.4f))
                InfoItem("架构", server.str("arch") ?: "-", Modifier.weight(1f))
                InfoItem("核心", "${server.int("cpu_cores") ?: "-"} 核", Modifier.weight(0.8f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoItem("CPU", server.str("cpu_info")?.take(28) ?: "-", Modifier.weight(1.6f))
                InfoItem("内核", server.str("kernel_version")?.take(16) ?: "-", Modifier.weight(1f))
            }
            if (uptime != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoItem("已运行", Format.duration(uptime), Modifier.weight(1f))
                    InfoItem(
                        "最近上报",
                        Format.dateTime(server.long("last_updated")),
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCard(server: ServerState, config: JsonObject?) {
    val s = MaterialTheme.colorScheme

    fun pingChips(): List<Triple<String, Double?, Double?>> {
        return listOf(
            Triple("电信", server.num("ping_ct"), server.num("loss_ct")),
            Triple("联通", server.num("ping_cu"), server.num("loss_cu")),
            Triple("移动", server.num("ping_cm"), server.num("loss_cm")),
            Triple("BGP", server.num("ping_bd"), server.num("loss_bd"))
        )
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            com.yunkong.monitor.ui.components.MetricBar("CPU", server.num("cpu"), "${server.int("cpu_cores") ?: 0} 核", BrandBlue)
            Spacer(Modifier.height(12.dp))
            for ((name, key, color) in listOf(Triple("内存", "ram", Purple), Triple("交换分区", "swap", Amber), Triple("磁盘", "disk", Cyan))) {
                val total = server.num("${key}_total")
                val used = server.num("${key}_used")
                if (total != null && total > 0) {
                    com.yunkong.monitor.ui.components.MetricBar(name, used?.div(total)?.times(100), "${Format.mb(used)} / ${Format.mb(total)}", color)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                InfoItem("负载", server.str("load_avg")?.take(20) ?: "-", Modifier.weight(1f))
                InfoItem("进程数", "${server.int("processes") ?: "-"}", Modifier.weight(1f))
                InfoItem(
                    "TCP / UDP",
                    "${server.int("tcp_conn") ?: "-"} / ${server.int("udp_conn") ?: "-"}",
                    Modifier.weight(1.2f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoItem(
                    "本月下行",
                    Format.bytes(server.num("net_rx_monthly")),
                    Modifier.weight(1f)
                )
                InfoItem(
                    "本月上行",
                    Format.bytes(server.num("net_tx_monthly")),
                    Modifier.weight(1f)
                )
                InfoItem(
                    "流量上限",
                    server.str("traffic_limit")?.ifBlank { null } ?: "不限",
                    Modifier.weight(1f)
                )
            }
            val expire = if (config.bool("show_expire") == false) null else server.str("expire_date")
            val price = if (config.bool("show_price") == false) null else server.str("price")
            if (!expire.isNullOrBlank() || (!price.isNullOrBlank() && price != "0")) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoItem("到期时间", expire?.ifBlank { "-" } ?: "-", Modifier.weight(1f))
                    InfoItem(
                        "价格",
                        if (price == "0" || price == "-1") "免费"
                        else "${server.str("currency") ?: ""}${price ?: "-"}",
                        Modifier.weight(1f)
                    )
                }
            }
            // Ping / 丢包
            Spacer(Modifier.height(12.dp))
            Text("网络质量", fontSize = 12.sp, color = s.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((label, ping, loss) in pingChips()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontSize = 10.sp, color = s.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                ping == null && loss == null -> "-"
                                ping == null -> "超时"
                                else -> "${ping.toInt()}ms"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = s.onSurface
                        )
                        val lossText = if (loss != null && loss > 0) "丢包 ${loss.toInt()}%" else null
                        if (lossText != null) {
                            Text(lossText, fontSize = 9.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }
            // GPU
            val gpus = parseGpus(server)
            if (gpus.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("GPU", fontSize = 12.sp, color = s.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                for (g in gpus) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            g.first,
                            fontSize = 12.sp,
                            color = s.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            g.second?.let { Format.percent(it) } ?: "-",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Amber
                        )
                    }
                }
            }
        }
    }
}

/** gpu_info 可能是 JSON 数组字符串或数组：[{id,name,info}] */
private fun parseGpus(server: ServerState): List<Pair<String, Double?>> {
    val element = (server as? JsonObject)?.get("gpu_info") ?: return emptyList()
    val arr = when (element) {
        is JsonArray -> element
        is kotlinx.serialization.json.JsonPrimitive ->
            try {
                API_JSON.parseToJsonElement(element.content) as? JsonArray
            } catch (e: Exception) {
                null
            }
        else -> null
    } ?: return emptyList()
    return arr.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val name = obj.str("name") ?: return@mapNotNull null
        Pair(name, obj.num("info"))
    }
}
