package com.simplemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在准备地图", color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .widthIn(max = 560.dp),
            ) {
                Text(
                    text = "欢迎使用 SimpleMap",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "为提供地图展示、地点搜索、路线规划和实时导航，应用会在你同意后使用高德地图服务，并在获得系统授权后处理位置信息。不同意时不会初始化地图服务，也不会访问位置。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 25.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "你可以稍后在设置中管理定位权限和数据选项。继续即表示你已阅读并同意隐私说明。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PanelShapeSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("同意并继续", modifier = Modifier.padding(vertical = 5.dp))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PanelShapeSmall,
                ) {
                    Text("暂不同意", modifier = Modifier.padding(vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
internal fun MissingApiKeyScreen(modifier: Modifier = Modifier) {
    StatusScreen(
        title = "地图服务尚未配置",
        message = "请在 local.properties 中添加与 com.simplemap 绑定的 AMAP_API_KEY，然后重新构建应用。",
        modifier = modifier,
    )
}

@Composable
internal fun FailureScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusScreen(
        title = "地图服务暂不可用",
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry, shape = PanelShapeSmall) {
                Text("重试")
            }
        },
    )
}

@Composable
private fun StatusScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        action()
    }
}
