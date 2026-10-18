package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import com.yunkong.monitor.data.Conn
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.data.ServerState
import com.yunkong.monitor.data.bool
import com.yunkong.monitor.data.isOnline
import com.yunkong.monitor.data.long
import com.yunkong.monitor.data.num
import com.yunkong.monitor.data.str
import com.yunkong.monitor.ui.components.Badge
import com.yunkong.monitor.ui.components.GlassCard
import com.yunkong.monitor.ui.components.MetricBar
import com.yunkong.monitor.ui.components.StatusDot
import com.yunkong.monitor.ui.theme.Amber
import com.yunkong.monitor.ui.theme.BrandBlue
import com.yunkong.monitor.ui.theme.Cyan
import com.yunkong.monitor.ui.theme.Green
import com.yunkong.monitor.ui.theme.Purple
import com.yunkong.monitor.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MonitorViewModel, onOpenDetail: (String) -> Unit, onOpenSettings: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()

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
                Column {
                    Text(ui.siteTitle.ifBlank { "云控" }, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dotColor, label) = when (ui.conn) {
                            Conn.LIVE -> Color(0xFF22C55E) to "实时连接"
                            Conn.POLLING -> Amber to "轮询模式"
                            Conn.CONNECTING -> Amber to "连接中…"
                            Conn.NONE -> s.onSurfaceVariant to "未连接"
                        }
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(label, fontSize = 11.sp, color = s.onSurfaceVariant)
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.refresh() }, enabled = !ui.loading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = s.onSurface)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = s.onSurface)
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
            StatChip("在线", "${ui.stats.online}", Green, Modifier.weight(1f))
            StatChip("离线", "${ui.stats.offline}", if (ui.stats.offline > 0) Color(0xFFEF4444) else s.onSurfaceVariant, Modifier.weight(1f))
            StatChip("↓ 下行", Format.speed(ui.stats.speedIn), BrandBlue, Modifier.weight(1.4f))
            StatChip("↑ 上行", Format.speed(ui.stats.speedOut), Purple, Modifier.weight(1.4f))
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
                    Modifier.fillMaxSize(),
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
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
    val s = MaterialTheme.colorScheme
    GlassCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 10.sp, color = s.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerCard(server: ServerState, onClick: (String) -> Unit) {
    val s = MaterialTheme.colorScheme
    val id = server.str("id") ?: return
    val name = server.str("name") ?: "未命名"
    val region = server.str("region")
    val os = server.str("os")
    val online = server.isOnline()

    val cpu = server.num("cpu")
    val ramPercent = run {
        val t = server.num("ram_total")
        val u = server.num("ram_used")
        if (t != null && t > 0 && u != null) u / t * 100.0 else null
    }
    val diskPercent = run {
        val t = server.num("disk_total")
        val u = server.num("disk_used")
        if (t != null && t > 0 && u != null) u / t * 100.0 else null
    }

    val inSpeed = server.num("net_in_speed")
    val outSpeed = server.num("net_out_speed")
    val rxMonthly = server.num("net_rx_monthly")
    val txMonthly = server.num("net_tx_monthly")
    val expire = server.str("expire_date")
    val uptimeMs = run {
        val boot = server.str("boot_time")?.toLongOrNull()
        if (boot != null && boot > 0) System.currentTimeMillis() - boot else null
    }

    GlassCard(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(id) }
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(online)
                Spacer(Modifier.size(8.dp))
                Text(
                    name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = s.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.size(8.dp))
                if (!region.isNullOrBlank() && region != "unknown") {
                    Badge(region.uppercase(), BrandBlue)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (online) "在线" else "离线",
                    fontSize = 11.sp,
                    color = if (online) Green else s.onSurfaceVariant
                )
            }
            if (!os.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(os, fontSize = 11.sp, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))

            // 指标条
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBar(
                    "CPU",
                    cpu,
                    server.str("cpu_info") ?: "",
                    BrandBlue
                )
                MetricBar(
                    "内存",
                    ramPercent,
                    run {
                        val u = server.num("ram_used")
                        val t = server.num("ram_total")
                        if (u != null && t != null) "${Format.mb(u)} / ${Format.mb(t)}" else ""
                    },
                    Cyan
                )
                MetricBar(
                    "磁盘",
                    diskPercent,
                    run {
                        val u = server.num("disk_used")
                        val t = server.num("disk_total")
                        if (u != null && t != null) "${Format.mb(u)} / ${Format.mb(t)}" else ""
                    },
                    Purple
                )
            }

            Spacer(Modifier.height(14.dp))

            // 底部信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "↓ ${Format.speed(inSpeed)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandBlue
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "↑ ${Format.speed(outSpeed)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Purple
                )
                Spacer(Modifier.weight(1f))
                if (rxMonthly != null || txMonthly != null) {
                    Text(
                        "月流量 ${Format.mb(rxMonthly ?: 0.0)} / ${Format.mb(txMonthly ?: 0.0)}",
                        fontSize = 10.sp,
                        color = s.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (!expire.isNullOrBlank() && expire != "0000-00-00") {
                    Badge("到期 $expire", if (online) Amber else s.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                }
                if (uptimeMs != null && uptimeMs > 0) {
                    Text("已运行 ${Format.duration(uptimeMs)}", fontSize = 10.sp, color = s.onSurfaceVariant)
                }
                val hidden = server.bool("is_hidden")
                if (hidden == true) {
                    Spacer(Modifier.weight(1f))
                    Badge("隐藏", s.onSurfaceVariant)
                }
            }
        }
    }
}
