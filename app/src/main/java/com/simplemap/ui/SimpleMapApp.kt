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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.simplemap.ui.theme.SimpleMapTheme

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
fun SimpleMapApp(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MapBackdrop()
        SearchBar(modifier = Modifier.align(Alignment.TopCenter))
        MapControls(modifier = Modifier.align(Alignment.CenterEnd))
        FloatingNavigation(modifier = Modifier.align(Alignment.BottomCenter))
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
        SimpleMapApp()
    }
}