package com.yunkong.monitor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.Conn
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.data.ServerState
import com.yunkong.monitor.data.bool
import com.yunkong.monitor.data.isOnline
import com.yunkong.monitor.data.num
import com.yunkong.monitor.data.str
import com.yunkong.monitor.ui.components.Badge
import com.yunkong.monitor.ui.components.StatusDot
import com.yunkong.monitor.ui.theme.TermPalette
import com.yunkong.monitor.ui.theme.term
import com.yunkong.monitor.util.Format
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MonitorViewModel, onOpenDetail: (String) -> Unit) {
    val s = MaterialTheme.colorScheme
    val t = term()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(s.background)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(ui.siteTitle.ifBlank { "云控" }, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dotColor, label) = when (ui.conn) {
                            Conn.LIVE -> t.green to "实时连接"
                            Conn.POLLING -> t.yellow to "轮询模式"
                            Conn.CONNECTING -> t.yellow to "连接中…"
                            Conn.NONE -> s.onSurfaceVariant to "未连接"
                        }
                        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                        Spacer(Modifier.size(5.dp))
                        Text(label, fontSize = 11.sp, color = s.onSurfaceVariant)
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.refresh() }, enabled = !ui.loading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = s.onSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // 汇总统计
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("在线", "${ui.stats.online}", t.green, Modifier.weight(1f))
            StatChip("离线", "${ui.stats.offline}", if (ui.stats.offline > 0) t.red else s.onSurfaceVariant, Modifier.weight(1f))
            StatChip("▼ 下行", Format.speed(ui.stats.speedIn), t.green, Modifier.weight(1.4f))
            StatChip("▲ 上行", Format.speed(ui.stats.speedOut), t.blue, Modifier.weight(1.4f))
        }

        ui.error?.let { err ->
            Text(
                err,
                color = s.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        PullToRefreshBox(
            isRefreshing = ui.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (ui.servers.isEmpty() && !ui.loading) {
                Column(
                    Modifier.fillMaxSize().padding(bottom = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("暂无服务器", color = s.onSurfaceVariant, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("检查面板地址或到面板中添加服务器", color = s.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ui.servers, key = { it.str("id") ?: it.hashCode().toString() }) { server ->
                        ServerCard(server) { id -> onOpenDetail(id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val t = term()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = t.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, t.border)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 10.sp, color = t.textSec, maxLines = 1, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** 原项目 ServerBarCard 风格服务器卡片（终端风：边框卡片 + 等宽数字 + 用量色阶进度条） */
@Composable
private fun ServerCard(server: ServerState, onClick: (String) -> Unit) {
    val t = term()
    val id = server.str("id") ?: return
    val name = server.str("name") ?: "未命名"
    val os = server.str("os")
    val online = server.isOnline()

    val cpu = server.num("cpu")
    val ramPercent = run {
        val total = server.num("ram_total")
        val used = server.num("ram_used")
        if (total != null && total > 0 && used != null) used / total * 100.0 else null
    }
    val diskPercent = run {
        val total = server.num("disk_total")
        val used = server.num("disk_used")
        if (total != null && total > 0 && used != null) used / total * 100.0 else null
    }
    val loads = (server.str("load_avg") ?: "").trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
    val inSpeed = server.num("net_in_speed")
    val outSpeed = server.num("net_out_speed")
    val rxMonthly = server.num("net_rx_monthly")
    val txMonthly = server.num("net_tx_monthly")
    val trafficLimitGb = server.num("traffic_limit")
    val usePercent = run {
        val limit = trafficLimitGb
        if (limit != null && limit > 0) {
            ((rxMonthly ?: 0.0) + (txMonthly ?: 0.0)) / (limit * 1024.0 * 1024.0 * 1024.0) * 100.0
        } else null
    }

    // 价格 / 到期 / 标签 / IP
    val price = server.num("price")
    val currency = server.str("currency") ?: "CNY"
    val cycle = server.str("billing_cycle") ?: ""
    val priceText = price?.takeIf { it > 0 }?.let {
        val sym = when (currency) {
            "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"
            else -> "¥"
        }
        "$sym${if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.2f", it)}/${cycleLabel(cycle)}"
    }
    val expireText = expireRemaining(server.str("expire_date"))
    val tags = (server.str("tags") ?: "").split(',').map { it.trim() }.filter { it.isNotBlank() }
    val v4 = server.str("ip_v4") == "1" || server.bool("ip_v4") == true
    val v6 = server.str("ip_v6") == "1" || server.bool("ip_v6") == true

    val cardShape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(id) },
        shape = cardShape,
        color = t.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, t.border)
    ) {
        Column(Modifier.padding(14.dp)) {
            // 头部：状态点 + 名称 + 状态标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (online) t.green else t.textSec, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.size(6.dp))
                StatusPill(if (online) "在线" else "离线", if (online) t.green else t.red)
            }
            if (!os.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(os, fontSize = 10.sp, color = t.textSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // 元信息：价格 / 到期 / 标签 / IPv4·6
            if (priceText != null || expireText != null || tags.isNotEmpty() || v4 || v6) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    priceText?.let { Text(it, fontSize = 10.sp, color = t.yellow, fontFamily = FontFamily.Monospace) }
                    expireText?.let { (days, label) ->
                        Text(label, fontSize = 10.sp, color = if (days < 0) t.red else t.textSec, fontFamily = FontFamily.Monospace)
                    }
                    tags.forEachIndexed { i, tag ->
                        val colors = listOf(t.cyan, t.purple, t.pink, t.green, t.blue, t.yellow)
                        Badge(tag, colors[i % colors.size])
                    }
                    Spacer(Modifier.weight(1f))
                    if (v4) Badge("V4", t.purple)
                    if (v6) Badge("V6", t.pink)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 统计行（CPU / RAM / DISK / LOAD / NET / TRF / USE）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBarRow("CPU", cpu)
                StatBarRow("RAM", ramPercent)
                StatBarRow("DISK", diskPercent)
                if (loads.isNotEmpty()) {
                    StatRow("LOAD") {
                        val colors = listOf(t.green, t.text, t.blue)
                        loads.take(3).forEachIndexed { i, v ->
                            Text(
                                String.format(Locale.US, "%.2f", v),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors[i.coerceAtMost(colors.size - 1)]
                            )
                            if (i < loads.take(3).size - 1) Spacer(Modifier.size(8.dp))
                        }
                    }
                }
                StatRow("NET") {
                    Text("▼ ${Format.speed(inSpeed)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = t.green)
                    Spacer(Modifier.weight(1f))
                    Text("▲ ${Format.speed(outSpeed)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = t.blue)
                }
                if (rxMonthly != null || txMonthly != null || (trafficLimitGb ?: 0.0) > 0) {
                    StatRow("TRF") {
                        Text("▼ ${Format.bytes(rxMonthly)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = t.green)
                        Spacer(Modifier.weight(1f))
                        val limit = trafficLimitGb
                        if (limit != null && limit > 0) {
                            Text(
                                "▲ ${Format.bytes(txMonthly)}  📦 ${Format.bytes(limit * 1024.0 * 1024.0 * 1024.0, 0)}",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = t.blue
                            )
                        } else {
                            Text("▲ ${Format.bytes(txMonthly)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = t.blue)
                        }
                    }
                }
                if (usePercent != null) StatBarRow("USE", usePercent)
            }
        }
    }
}

private fun cycleLabel(c: String): String = when (c) {
    "month" -> "月"; "year" -> "年"; "quarter" -> "季"; "week" -> "周"; "day" -> "天"; "hour" -> "时"
    else -> ""
}

/** 到期剩余：返回 (剩余天数, 文案)，无效时为 null */
private fun expireRemaining(expire: String?): Pair<Int, String>? {
    if (expire.isNullOrBlank() || expire.startsWith("0000")) return null
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.isLenient = false
        val d = fmt.parse(expire.take(10)) ?: return null
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val days = ((d.time - today.time) / 86400000L).toInt()
        days to if (days < 0) "已过期" else "剩 $days 天"
    } catch (_: Exception) {
        null
    }
}

/** 用量色阶：>=95 红 / >=80 黄 / >=50 蓝 / 其余绿（对齐原项目 getUsageColor） */
private fun usageColor(t: TermPalette, percent: Double?): Color = when {
    percent == null -> t.textSec
    percent >= 95 -> t.red
    percent >= 80 -> t.yellow
    percent >= 50 -> t.blue
    else -> t.green
}

@Composable
private fun StatBarRow(key: String, percent: Double?) {
    val t = term()
    val animated by animateFloatAsState(
        targetValue = ((percent ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat(),
        animationSpec = tween(500),
        label = "usage"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            key, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = t.textSec, fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .background(t.border, RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(4.dp)
                    .background(usageColor(t, percent), RoundedCornerShape(2.dp))
            )
        }
        Text(
            percent?.let { String.format(Locale.US, "%.2f%%", it) } ?: "-",
            fontSize = 10.sp, color = t.text, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End, modifier = Modifier.width(64.dp)
        )
    }
}

@Composable
private fun StatRow(key: String, content: @Composable RowScope.() -> Unit) {
    val t = term()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            key, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = t.textSec, fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp)
        )
        Spacer(Modifier.size(2.dp))
        content()
    }
}

/** 状态标签（描边小胶囊） */
@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
