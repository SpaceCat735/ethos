package com.naechamon.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp

// 앱 창 모양. Pleos Connect는 앱 1개면 가로로 넓은 창, 앱 2개를 나란히 띄우면 세로로 긴 반쪽 창을 준다.
// 두 모양 모두 같은 기능·버튼을 보여 줘야 한다 (Pleos 심사: 화면 전환 시 기능 동등).
data class AppWindow(
    // 세로로 긴 창이면 위아래로 쌓는 배치를 쓴다
    val isPortrait: Boolean,
    // 글자·여백·캐릭터 크기의 기준 길이. 가로 창은 창 높이, 세로 창은 창 너비 (짧은 변)
    val unit: Dp,
)

// 판단은 여기 한 곳에서만 한다
fun BoxWithConstraintsScope.appWindow(): AppWindow =
    AppWindow(isPortrait = maxWidth < maxHeight, unit = minOf(maxWidth, maxHeight))
