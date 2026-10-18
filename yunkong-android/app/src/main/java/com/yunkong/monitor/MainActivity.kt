package com.yunkong.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.ui.components.BottomTab
import com.yunkong.monitor.ui.components.GlassBottomBar
import com.yunkong.monitor.ui.screens.DashboardScreen
import com.yunkong.monitor.ui.screens.DetailScreen
import com.yunkong.monitor.ui.screens.SettingsScreen
import com.yunkong.monitor.ui.screens.SetupScreen
import com.yunkong.monitor.ui.screens.TurnstileScreen
import com.yunkong.monitor.ui.theme.YunKongTheme

/** 顶层 tab（后续加功能直接往这里加） */
private val mainTabs = listOf(
    BottomTab("dashboard", "仪表盘", Icons.Rounded.Home),
    BottomTab("mine", "我的", Icons.Rounded.Person)
)

class MainActivity : ComponentActivity() {

    private val vm: MonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(vm)
        }
    }
}

@Composable
private fun App(vm: MonitorViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    YunKongTheme(themeMode) {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val backStackEntry by nav.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (ui.configured) "dashboard" else "setup"
            ) {
                composable("setup") {
                    SetupScreen(vm)
                }
                composable("dashboard") {
                    DashboardScreen(
                        vm,
                        onOpenDetail = { id -> nav.navigate("detail/$id") }
                    )
                }
                composable("detail/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: ""
                    DetailScreen(vm, id) { nav.popBackStack() }
                }
                composable("mine") {
                    SettingsScreen(vm, onBack = null)
                }
                composable("turnstile") {
                    TurnstileScreen(vm) { nav.popBackStack() }
                }
            }

            // kudo 风格悬浮底部导航（仅顶层 tab 页显示）
            if (currentRoute in mainTabs.map { it.route }) {
                GlassBottomBar(
                    tabs = mainTabs,
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        if (route != currentRoute) {
                            nav.navigate(route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .widthIn(max = 530.dp)
                        .fillMaxWidth()
                )
            }
        }

        // 需要人机验证时自动进入验证页
        LaunchedEffect(ui.needTurnstile) {
            if (ui.needTurnstile && nav.currentDestination?.route != "turnstile") {
                nav.navigate("turnstile") { launchSingleTop = true }
            }
        }

        // 首次配置成功后进入仪表盘
        LaunchedEffect(ui.configured) {
            if (ui.configured && nav.currentDestination?.route == "setup") {
                nav.navigate("dashboard") {
                    popUpTo("setup") { inclusive = true }
                }
            }
        }
    }
}
