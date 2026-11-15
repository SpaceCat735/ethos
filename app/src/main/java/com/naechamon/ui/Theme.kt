package com.naechamon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

// 앱 색은 여기서만 정한다. 값은 플레오스 커넥트 디자인 가이드의 낮/밤 토큰을 그대로 쓴다
// (기본 앱처럼 보이게: 흰 바탕·1px 선·글자색 반전 선택 줄. 민트·베이지 같은 앱만의 색은 쓰지 않는다).
// 차의 낮/밤 설정(Pleos 가이드 "Night/Day Mode Support")을 따라 두 벌 중 하나를 쓴다.
data class EthosColors(
    val bg: Color,          // 화면 배경
    val surface: Color,     // 표면 (밤에는 한 단계 밝게)
    val surfaceAlt: Color,  // 연회색 채움 (알약 버튼·칩)
    val line: Color,        // 1px 구분선
    val ink: Color,         // 본문 글자. 선택된 메뉴 줄의 배경
    val inkSoft: Color,     // 보조 글자 (ink의 64%)
    val accent: Color,      // 정상
    val warn: Color,        // 주의
    val alert: Color,       // 교체 권장·경고
)

val LightColors = EthosColors(
    bg = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDEEF2),
    line = Color(0xFFE0E1E6),
    ink = Color(0xFF131417),
    inkSoft = Color(0xA3131417),
    accent = Color(0xFF02C265),
    // 상태 글자 색: 가이드 주황 #FF8A00·빨강 #F62E24는 흰 바탕 글자 대비가 2.2·3.7:1이라 기준(4.5:1) 미달.
    // 같은 색 계열을 진하게 해 흰·회색 바탕·회색 칩 위에서 모두 4.5:1 이상 (#64)
    warn = Color(0xFFA65300),
    alert = Color(0xFFC62A0E),
)

val DarkColors = EthosColors(
    bg = Color(0xFF1D1E21),
    surface = Color(0xFF313236),
    surfaceAlt = Color(0xFF44464E),
    line = Color(0xFF44464E),
    ink = Color(0xFFFFFFFF),
    inkSoft = Color(0xA3FFFFFF),
    accent = Color(0xFF00E074),
    // 밤: 진한 회색 칩(#44464E) 위에서도 4.5:1 이상이 되도록 밝힌 주황·빨강
    warn = Color(0xFFFFB23D),
    alert = Color(0xFFFFA191),
)

val LocalEthosColors = compositionLocalOf { LightColors }

// 지금 테마의 색. 화면 코드는 색 값을 직접 쓰지 않고 이것만 쓴다
val ethos: EthosColors
    @Composable @ReadOnlyComposable get() = LocalEthosColors.current

@Composable
fun EthosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEthosColors provides if (darkTheme) DarkColors else LightColors) {
        MaterialTheme(content = content)
    }
}

// 연회색 채움의 둥근(알약) 버튼. 테두리 없음. 색을 칠한 버튼 대신 이것 하나로 통일한다 (Pleos 가이드: 과도한 색 지양)
@Composable
fun OutlineButton(text: String, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ethos
    Text(
        text = text,
        color = c.ink,
        fontSize = (height.value * 0.4f).sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(c.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = height * 0.5f, vertical = height * 0.15f),
    )
}
