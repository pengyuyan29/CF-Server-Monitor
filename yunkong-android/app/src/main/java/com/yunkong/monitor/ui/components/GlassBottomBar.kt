package com.yunkong.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 底部导航 tab 定义（后续加功能直接往列表里加） */
data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/**
 * kudo 风格悬浮胶囊底部导航：居中悬浮、图标 + 小字标签、选中高亮。
 * 参考 kudo-android GlassNavigation（液态玻璃的离线简化版，无 backdrop 折射）。
 */
@Composable
fun GlassBottomBar(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = MaterialTheme.colorScheme
    val capsule = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(elevation = 10.dp, shape = capsule, spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(capsule)
            .background(s.surface.copy(alpha = 0.94f))
            .border(1.dp, s.outline.copy(alpha = 0.55f), capsule)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            GlassTab(selected = tab.route == selectedRoute, onClick = { onSelect(tab.route) }) {
                Icon(tab.icon, tab.label, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.takeIf { tab.route == selectedRoute } ?: s.onSurface.copy(alpha = 0.72f))
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (tab.route == selectedRoute) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (tab.route == selectedRoute) s.primary else s.onSurface.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun RowScope.GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
