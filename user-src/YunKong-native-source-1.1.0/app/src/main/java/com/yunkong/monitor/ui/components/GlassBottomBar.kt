package com.yunkong.monitor.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/** Backdrop renderer used by Kudo; OS-specific optical effects with a compatibility material. */
@Composable
fun GlassBottomBar(tabs: List<BottomTab>, selectedRoute: String?, onSelect: (String) -> Unit,
                   backdrop: Backdrop, compatible: Boolean, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val blurEnabled = !compatible && Build.VERSION.SDK_INT >= 31
    val refract = !compatible && Build.VERSION.SDK_INT >= 33
    val selection = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val position by animateFloatAsState(selection.toFloat(), spring(dampingRatio = .82f, stiffness = 420f), label = "glass-selection")
    BoxWithConstraints(modifier.height(64.dp).selectableGroup()) {
        val tabWidth = (maxWidth - 8.dp) / tabs.size
        val glass = if (blurEnabled) Modifier.drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = { vibrancy(); blur(6.dp.toPx()); if (refract) lens(24.dp.toPx(), 24.dp.toPx()) },
            onDrawSurface = { drawRect(s.surface.copy(alpha = .26f)) }
        ) else Modifier.shadow(10.dp, shape).clip(shape).background(s.surface.copy(alpha = .96f))
        Box(Modifier.fillMaxSize().then(glass).border(1.dp, s.outline.copy(alpha = .45f), shape))
        Box(Modifier.padding(4.dp).offset(x = tabWidth * position).width(tabWidth).fillMaxHeight().clip(shape)
            .background(s.primary.copy(alpha = .13f)).border(1.dp, s.primary.copy(alpha = .12f), shape))
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            tabs.forEach { tab ->
                val selected = tab.route == selectedRoute
                Column(Modifier.weight(1f).fillMaxHeight().clip(shape).selectable(selected = selected, onClick = { onSelect(tab.route) }, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    val color = if (selected) s.primary else s.onSurface.copy(alpha = .8f)
                    Icon(tab.icon, null, Modifier.size(22.dp), tint = color)
                    Text(tab.label, color = color, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
