package com.naechamon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.naechamon.domain.CharacterAppearance
import com.naechamon.domain.IdleLifeRules
import kotlinx.coroutines.delay

// 정차 화면의 캐릭터 움직임. 정차 화면(GreetingPane, StatusPane)에서만 쓴다.
// 주행 화면에는 절대 붙이지 않는다 (안전 규칙: 주행 중 애니메이션 금지).
// 주행으로 바뀌면 정차 화면이 통째로 사라지므로 움직임도 그 순간 멈춘다.
//
// - 처음 나타날 때: 깡총 두 번 (약 1초)
// - 그 뒤: 천천히 숨쉬기, 가끔 한 번 작게 깡총 (기준값은 IdleLifeRules)
@Composable
fun Modifier.parkedIdle(height: Dp): Modifier {
    val offset = remember { Animatable(0f) }
    val heightPx = with(LocalDensity.current) { height.toPx() }
    LaunchedEffect(Unit) {
        listOf(1f, 0.5f).forEach { scale ->
            offset.animateTo(-heightPx * scale, tween(HOP_UP_MILLIS, easing = FastOutSlowInEasing))
            offset.animateTo(0f, tween(HOP_DOWN_MILLIS, easing = FastOutSlowInEasing))
        }
        while (true) {
            delay(IdleLifeRules.HOP_EVERY_MILLIS)
            offset.animateTo(-heightPx * IdleLifeRules.HOP_SCALE, tween(HOP_UP_MILLIS, easing = FastOutSlowInEasing))
            offset.animateTo(0f, tween(HOP_DOWN_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1f + IdleLifeRules.BREATH_SCALE,
        animationSpec = infiniteRepeatable(tween(IdleLifeRules.BREATH_MILLIS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    return graphicsLayer {
        translationY = offset.value
        // 바닥에 붙은 채로 숨쉬게 아래쪽을 기준으로 키운다
        transformOrigin = TransformOrigin(0.5f, 0.85f)
        scaleX = breath
        scaleY = breath
    }
}

// 정차 화면에서 가끔 잠깐 다른 표정을 지었다가 돌아온다. 어떤 표정을 지을지는 두뇌 층(IdleLifeRules)이 정한다.
// 표정이 바뀌는 것이지 판정이 바뀌는 것이 아니다: 대사·리포트는 원래 표정 그대로 쓴다.
@Composable
fun idleGlance(appearance: CharacterAppearance): CharacterAppearance {
    val glance = IdleLifeRules.glanceOf(appearance.stage, appearance.expression) ?: return appearance
    var glancing by remember(appearance.expression) { mutableStateOf(false) }
    LaunchedEffect(appearance.expression) {
        while (true) {
            delay(IdleLifeRules.GLANCE_EVERY_MILLIS)
            glancing = true
            delay(IdleLifeRules.GLANCE_MILLIS)
            glancing = false
        }
    }
    return if (glancing) appearance.copy(expression = glance) else appearance
}

private const val HOP_UP_MILLIS = 220
private const val HOP_DOWN_MILLIS = 200
