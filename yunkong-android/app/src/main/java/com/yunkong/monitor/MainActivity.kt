package com.yunkong.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.ui.screens.DashboardScreen
import com.yunkong.monitor.ui.screens.DetailScreen
import com.yunkong.monitor.ui.screens.SettingsScreen
import com.yunkong.monitor.ui.screens.SetupScreen
import com.yunkong.monitor.ui.screens.TurnstileScreen
import com.yunkong.monitor.ui.theme.YunKongTheme

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
                    onOpenDetail = { id -> nav.navigate("detail/$id") },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                DetailScreen(vm, id) { nav.popBackStack() }
            }
            composable("settings") {
                SettingsScreen(vm) { nav.popBackStack() }
            }
            composable("turnstile") {
                TurnstileScreen(vm) { nav.popBackStack() }
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
