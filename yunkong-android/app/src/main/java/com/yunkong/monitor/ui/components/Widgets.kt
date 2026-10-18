package com.yunkong.monitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunkong.monitor.util.Format

/** 圆角玻璃质感卡片容器 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val s = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = s.surface.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, s.outline.copy(alpha = 0.45f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box { content() }
    }
}

/** 在线状态圆点 */
@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(10.dp)
            .height(10.dp)
            .background(if (online) Color(0xFF22C55E) else Color(0xFF94A3B8), CircleShape)
    )
}

/** 指标进度条：标签 + 百分比 + 详情 */
@Composable
fun MetricBar(
    label: String,
    percent: Double?,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = ((percent ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat(),
        animationSpec = tween(500),
        label = "bar"
    )
    val s = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 11.sp, color = s.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (percent == null) "-" else Format.percent(percent),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = s.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(s.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(detail, fontSize = 10.sp, color = s.onSurfaceVariant)
        }
    }
}

/** 小徽标 */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** 信息条目（详情页用） */
@Composable
fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = s.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = s.onSurface, maxLines = 1
        )
    }
}

data class ChartPoint(val t: Long, val y: Double?)

data class ChartSeries(val points: List<ChartPoint>, val color: Color, val label: String)

/** 折线图（多序列，支持数据空洞），带渐变填充与极值标注 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yMax: Double? = null,
    yFormat: (Double) -> String = { "${it.toInt()}%" },
    height: Int = 110
) {
    val s = MaterialTheme.colorScheme
    val gridColor = s.outline.copy(alpha = 0.5f)
    val labelColor = s.onSurfaceVariant
    val allPoints = series.flatMap { it.points }
    val allYs = allPoints.mapNotNull { it.y }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (allPoints.size < 2) return@Canvas
                val t0 = allPoints.minOf { it.t }
                val t1 = allPoints.maxOf { it.t }
                val span = (t1 - t0).coerceAtLeast(1L).toFloat()

                val ys = allPoints.mapNotNull { it.y }
                val dataMax = ys.maxOrNull() ?: 0.0
                val dataMin = ys.minOrNull() ?: 0.0
                val top = yMax ?: (dataMax * 1.15).coerceAtLeast(dataMax + 0.5)
                val bottom = if (dataMin < 0) dataMin else 0.0
                val range = (top - bottom).coerceAtLeast(1e-9)

                fun px(t: Long): Float = (t - t0).toFloat() / span * size.width
                fun py(v: Double): Float =
                    size.height - ((v - bottom) / range).toFloat() * size.height

                // 网格
                for (i in 1..2) {
                    val gy = size.height * i / 3f
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                }

                // 逐序列、按非空段绘制
                for (sr in series) {
                    var run: MutableList<ChartPoint> = mutableListOf()
                    fun flush() {
                        if (run.size >= 2) {
                            val path = Path()
                            run.forEachIndexed { i, p ->
                                val x = px(p.t)
                                val y = py(p.y!!)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, sr.color, style = Stroke(width = 4f))
                            val fill = Path().apply {
                                addPath(path)
                                lineTo(px(run.last().t), size.height)
                                lineTo(px(run.first().t), size.height)
                                close()
                            }
                            drawPath(
                                fill,
                                Brush.verticalGradient(
                                    colors = listOf(sr.color.copy(alpha = 0.20f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                        }
                        run = mutableListOf()
                    }
                    for (p in sr.points) {
                        if (p.y != null) run.add(p) else flush()
                    }
                    flush()
                }
            }
            if (allYs.isEmpty() && allPoints.size < 2) {
                Text(
                    "暂无数据",
                    fontSize = 12.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                if (allYs.isNotEmpty()) {
                    val maxV = yMax ?: allYs.maxOrNull() ?: 0.0
                    Text(
                        yFormat(maxV),
                        fontSize = 10.sp,
                        color = labelColor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }
        }
        if (allPoints.size >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Format.timeHM(allPoints.minOf { it.t }),
                    fontSize = 10.sp,
                    color = labelColor
                )
                Spacer(Modifier.weight(1f))
                for (sr in series) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(sr.color, CircleShape)
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(sr.label, fontSize = 10.sp, color = labelColor)
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    Format.timeHM(allPoints.maxOf { it.t }),
                    fontSize = 10.sp,
                    color = labelColor
                )
            }
        }
    }
}
