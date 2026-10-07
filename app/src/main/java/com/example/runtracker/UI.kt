package com.example.runtracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ---- 색상 (HTML 시안과 동일) ----
private val Bg = Color(0xFF0F1115)
private val CardBg = Color(0xFF1F232C)
private val TextMain = Color(0xFFF4F5F7)
private val Sub = Color(0xFF9AA1AD)
private val Accent = Color(0xFFFF6B2C)
private val Accent2 = Color(0xFFFFB02C)
private val Green = Color(0xFF2ECC71)
private val Red = Color(0xFFFF4D4F)
private val Blue = Color(0xFF3B82F6)
private val AccentBrush = Brush.linearGradient(listOf(Accent, Accent2))

@Composable
fun RunApp(onStartClicked: () -> Unit) {
    val context = LocalContext.current
    val state by RunTracker.state.collectAsStateWithLifecycle()

    // 달리는 동안 화면이 꺼지지 않게 유지
    val view = LocalView.current
    DisposableEffect(state.phase) {
        view.keepScreenOn = state.phase == Phase.COUNTDOWN ||
            state.phase == Phase.RUNNING ||
            state.phase == Phase.PAUSED
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            when (state.phase) {
                Phase.HOME -> HomeScreen(onStart = onStartClicked)
                Phase.COUNTDOWN -> CountdownScreen(
                    count = state.countdown,
                    onCancel = { RunTracker.cancelCountdown(context) },
                )
                Phase.RUNNING, Phase.PAUSED -> RunScreen(
                    state = state,
                    onPause = { RunTracker.pause() },
                    onResume = { RunTracker.resume() },
                    onStop = { RunTracker.finish(context) },
                )
                Phase.SUMMARY -> SummaryScreen(state = state, onHome = { RunTracker.reset() })
            }
        }
    }
}

// =============================================================== 1. 첫 화면

@Composable
private fun HomeScreen(onStart: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "runner")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    val shadowScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shadow",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A1D17), Bg), radius = 900f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RUN TRACKER", color = Sub, fontSize = 15.sp, letterSpacing = 2.sp)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🏃", fontSize = 150.sp, modifier = Modifier.offset(y = bounce.dp))
            Box(
                Modifier
                    .size(width = 90.dp, height = 12.dp)
                    .graphicsLayer(scaleX = shadowScale, scaleY = shadowScale)
                    .background(Color(0x80000000), RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(18.dp))
            Text("오늘도 가볍게 달려볼까요?", color = Sub, fontSize = 16.sp)
        }

        ActionButton(
            text = "시작",
            onClick = onStart,
            background = AccentBrush,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth(),
            height = 64.dp,
            fontSize = 22.sp,
        )
    }
}

// =============================================================== 2. 카운트다운

@Composable
private fun CountdownScreen(count: Int, onCancel: () -> Unit) {
    val scale = remember { Animatable(1.6f) }
    LaunchedEffect(count) {
        scale.snapTo(1.6f)
        scale.animateTo(1f, tween(600))
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("곧 시작합니다", color = Sub, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "$count",
            style = TextStyle(brush = AccentBrush, fontSize = 200.sp, fontWeight = FontWeight.Black),
            modifier = Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value),
        )
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .border(1.dp, Color(0xFF3A3F4B), CircleShape)
                .clickable(onClick = onCancel)
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text("취소", color = Sub, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================== 3. 달리는 중 / 일시정지

@Composable
private fun RunScreen(
    state: RunState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val paused = state.phase == Phase.PAUSED

    val blink = rememberInfiniteTransition(label = "dot")
    val dotAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dotAlpha",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
    ) {
        // 상태 표시
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(if (paused) 1f else dotAlpha)
                    .background(if (paused) Accent2 else Green, CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (paused) "일시정지" else "달리는 중",
                color = Sub, fontSize = 14.sp, letterSpacing = 1.sp,
            )
        }

        // 달린 시간
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("달린 시간", color = Sub, fontSize = 14.sp)
            Text(
                formatTime(state.elapsedSec),
                color = if (paused) Accent2 else TextMain,
                fontSize = 76.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }

        // 거리 / 페이스 / 속도
        StatCard(
            label = "거리",
            value = formatKm(state.distanceKm),
            unit = "km",
            valueSize = 40.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "현재 페이스",
                value = formatPace(state.speedKmh),
                unit = "/km",
                valueSize = 32.sp,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "속도",
                value = formatSpeed(state.speedKmh),
                unit = "km/h",
                valueSize = 32.sp,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.weight(1f))

        // 하단 버튼
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!paused) {
                ActionButton(
                    text = "⏸ 일시정지", onClick = onPause,
                    background = SolidColor(Accent2), contentColor = Color(0xFF1A1300),
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "⏹ 멈춤", onClick = onStop,
                    background = SolidColor(Red), contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ActionButton(
                    text = "▶ 계속", onClick = onResume,
                    background = SolidColor(Green), contentColor = Color(0xFF052313),
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "■ 종료", onClick = onStop,
                    background = SolidColor(Red), contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    valueSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(label, color = Sub, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextMain, fontSize = valueSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                unit, color = Sub, fontSize = 15.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                maxLines = 1,
            )
        }
    }
}

// =============================================================== 4. 결과 (경로 지도 + 요약)

@Composable
private fun SummaryScreen(state: RunState, onHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    ) {
        // 화면 최상단: 이동 경로 지도
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBg),
        ) {
            if (state.route.any { it.isNotEmpty() }) {
                RouteMap(route = state.route, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    "기록된 경로가 없어요",
                    color = Sub,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "🎉 수고하셨어요!",
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow("총 시간", formatTime(state.elapsedSec), "")
            SummaryRow("총 거리", formatKm(state.distanceKm), "km")
            SummaryRow("평균 속도", formatSpeed(state.avgSpeedKmh), "km/h")
        }

        Spacer(Modifier.height(16.dp))

        ActionButton(
            text = "처음으로",
            onClick = onHome,
            background = SolidColor(Blue),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth(),
            height = 60.dp,
            fontSize = 19.sp,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, unit: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Sub, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(unit, color = Sub, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
    }
}

// =============================================================== 공용 버튼

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    background: Brush,
    contentColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 68.dp,
    fontSize: TextUnit = 18.sp,
) {
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}
