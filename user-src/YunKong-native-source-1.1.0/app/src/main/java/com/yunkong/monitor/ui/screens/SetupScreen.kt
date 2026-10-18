package com.yunkong.monitor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunkong.monitor.R
import com.yunkong.monitor.data.MonitorViewModel
import com.yunkong.monitor.ui.theme.BrandBlue

@Composable
fun SetupScreen(vm: MonitorViewModel) {
    val s = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("https://11.202991001.xyz") }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by androidx.compose.runtime.remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(s.primary.copy(alpha = 0.10f), Color.Transparent, s.primary.copy(alpha = 0.06f))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BrandBlue),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("云控", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = s.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "连接你的服务器监控面板",
                fontSize = 14.sp,
                color = s.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("面板地址") },
                placeholder = { Text("https://monitor.example.com") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "私有站点登录（可选）",
                    fontSize = 12.sp,
                    color = s.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showLogin = !showLogin }) {
                    Icon(
                        if (showLogin) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = s.onSurfaceVariant
                    )
                }
            }

            if (showLogin) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("管理员账号") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("管理员密码") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ui.error?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(err, color = s.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (showLogin) vm.connect(url, username, password)
                    else vm.connect(url)
                },
                enabled = !ui.loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = s.onPrimary
                    )
                } else {
                    Text("连 接", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "支持 CF-Server-Monitor 面板\n公开站点无需登录，私有站点需填写管理账号",
                fontSize = 12.sp,
                color = s.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 120.dp)
            )
        }
    }
}
