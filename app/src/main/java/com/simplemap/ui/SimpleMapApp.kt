package com.simplemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.amap.AmapMapView
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState
import com.simplemap.ui.theme.SimpleMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SearchIcon = ImageVector.Builder(
    name = "Search",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color(0xFF1E2927)), strokeLineWidth = 2f) {
        moveTo(10.5f, 4f)
        curveTo(6.9f, 4f, 4f, 6.9f, 4f, 10.5f)
        curveTo(4f, 14.1f, 6.9f, 17f, 10.5f, 17f)
        curveTo(14.1f, 17f, 17f, 14.1f, 17f, 10.5f)
        curveTo(17f, 6.9f, 14.1f, 4f, 10.5f, 4f)
        close()
        moveTo(15.2f, 15.2f)
        lineTo(21f, 21f)
    }
}.build()

private val CompassIcon = ImageVector.Builder(
    name = "Compass",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF187A63))) {
        moveTo(18.8f, 5.2f)
        lineTo(14.8f, 14.8f)
        lineTo(5.2f, 18.8f)
        lineTo(9.2f, 9.2f)
        close()
    }
}.build()

@Composable
fun SimpleMapRoot(
    controller: MapAccessController,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state: MapAccessState by remember { mutableStateOf(MapAccessState.Loading) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        state = withContext(Dispatchers.IO) { controller.load() }
    }

    when (val currentState = state) {
        MapAccessState.Loading -> LoadingScreen(modifier)
        MapAccessState.ConsentRequired -> PrivacyConsentScreen(
            onAccept = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.accept() }
                }
            },
            onDecline = onDecline,
            modifier = modifier,
        )
        MapAccessState.MissingApiKey -> MissingApiKeyScreen(modifier)
        MapAccessState.Ready -> SimpleMapApp(modifier = modifier)
        is MapAccessState.Failed -> FailureScreen(
            message = currentState.message,
            onRetry = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.load() }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
fun SimpleMapApp(
    modifier: Modifier = Modifier,
    showLiveMap: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showLiveMap) {
            AmapMapView(modifier = Modifier.fillMaxSize())
        } else {
            MapBackdrop()
        }
        SearchBar(modifier = Modifier.align(Alignment.TopCenter))
        MapControls(modifier = Modifier.align(Alignment.CenterEnd))
        FloatingNavigation(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F4)),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在准备地图", color = Color(0xFF43504D))
    }
}

@Composable
private fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF4F7F4)) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "欢迎使用 SimpleMap",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17211F),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "为提供地图展示、地点搜索、路线规划和实时导航，应用会在你同意后使用高德地图服务，并在获得系统授权后处理位置信息。不同意时不会初始化地图服务，也不会访问位置。",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4F5B58),
                lineHeight = 25.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "你可以稍后在设置中管理定位权限和数据选项。继续即表示你已阅读并同意隐私说明。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6E7976),
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
            ) {
                Text("同意并继续", modifier = Modifier.padding(vertical = 5.dp))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("暂不同意", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun MissingApiKeyScreen(modifier: Modifier = Modifier) {
    StatusScreen(
        title = "地图服务尚未配置",
        message = "请在 local.properties 中添加与 com.simplemap 绑定的 AMAP_API_KEY，然后重新构建应用。",
        modifier = modifier,
    )
}

@Composable
private fun FailureScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusScreen(
        title = "地图服务暂不可用",
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
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
            .background(Color(0xFFF4F7F4))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFF17211F))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color(0xFF5F6B68), lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

@Composable
private fun MapBackdrop() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EEE9))
            .semantics { contentDescription = "地图区域" },
    ) {
        val road = Path().apply {
            moveTo(-40f, size.height * 0.78f)
            cubicTo(size.width * 0.2f, size.height * 0.6f, size.width * 0.45f, size.height * 0.84f, size.width + 40f, size.height * 0.46f)
        }
        drawPath(road, color = Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round))
        drawPath(road, color = Color(0xFFC8D1CB), style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFA8D8C2), radius = 92f, center = Offset(size.width * 0.18f, size.height * 0.28f))
        drawCircle(Color(0xFFDAE6D9), radius = 135f, center = Offset(size.width * 0.82f, size.height * 0.22f))
    }
}

@Composable
private fun SearchBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SearchIcon, contentDescription = null, tint = Color.Unspecified)
            Text(
                text = "搜索地点、公交或路线",
                modifier = Modifier.padding(start = 12.dp),
                color = Color(0xFF6E7976),
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFFE8F5EF), shape = CircleShape) {
                Text(
                    text = "A",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFF126B56),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MapControls(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = Color.White, shadowElevation = 6.dp) {
            IconButton(onClick = {}) {
                Icon(CompassIcon, contentDescription = "回到当前位置", tint = Color.Unspecified)
            }
        }
    }
}

@Composable
private fun FloatingNavigation(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .fillMaxWidth(),
        color = Color(0xF7FFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavigationItem("地图", true)
            NavigationItem("路线", false)
            NavigationItem("行程", false)
            NavigationItem("我的", false)
        }
    }
}

@Composable
private fun NavigationItem(label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (selected) Color(0xFF13745C) else Color.Transparent, CircleShape),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (selected) Color(0xFF126B56) else Color(0xFF78827F),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SimpleMapPreview() {
    SimpleMapTheme {
        SimpleMapApp(showLiveMap = false)
    }
}