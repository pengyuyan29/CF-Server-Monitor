package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.ui.components.GlassCard
import com.yunkong.monitor.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    var loginUser by rememberSaveable { mutableStateOf("") }
    var loginPass by rememberSaveable { mutableStateOf("") }

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
            title = { Text("设置", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = s.onSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 连接
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("连接")
                    Spacer(Modifier.height(8.dp))
                    Text("当前地址", fontSize = 11.sp, color = s.onSurfaceVariant)
                    Text(
                        ui.baseUrl.ifBlank { "未配置" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = s.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("更换面板地址") },
                        placeholder = { Text("https://monitor.example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (ui.error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(ui.error ?: "", color = s.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (url.isNotBlank()) vm.connect(url)
                            else vm.refresh()
                        },
                        enabled = !ui.loading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (url.isBlank()) "重新连接" else "连接新地址")
                    }
                }
            }

            // 账号（私有站点）
            if (ui.privateSite) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("账号")
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (ui.loggedIn) "已登录（管理员）" else "未登录",
                                fontSize = 13.sp,
                                color = if (ui.loggedIn) Green else s.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (ui.loggedIn) {
                                TextButton(onClick = { vm.logout() }) { Text("退出登录") }
                            } else {
                                TextButton(onClick = { showLoginDialog = true }) { Text("登录") }
                            }
                        }
                    }
                }
            }

            // 外观
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("外观")
                    Spacer(Modifier.height(4.dp))
                    for ((mode, label) in listOf(0 to "跟随系统", 1 to "浅色模式", 2 to "深色模式")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode) }
                            )
                            Text(label, fontSize = 14.sp, color = s.onSurface)
                        }
                    }
                }
            }

            // 关于
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("关于")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("版本", fontSize = 13.sp, color = s.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = s.onSurface)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("适配面板", fontSize = 13.sp, color = s.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("CF-Server-Monitor", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = s.onSurface)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "数据来源：你配置的监控面板 · 实时更新通过 WebSocket 推送",
                        fontSize = 11.sp,
                        color = s.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("登录私有站点") },
            text = {
                Column {
                    OutlinedTextField(
                        value = loginUser,
                        onValueChange = { loginUser = it },
                        label = { Text("管理员账号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = loginPass,
                        onValueChange = { loginPass = it },
                        label = { Text("管理员密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ui.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = s.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLoginDialog = false
                        vm.login(loginUser, loginPass)
                    },
                    enabled = loginUser.isNotBlank() && loginPass.isNotBlank()
                ) { Text("登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}
