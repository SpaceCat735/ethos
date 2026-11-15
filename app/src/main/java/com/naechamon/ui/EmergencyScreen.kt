package com.naechamon.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.naechamon.R
import com.naechamon.domain.EmergencyType

// 짙은 빨강 배경 + 흰 글씨
private val EmergencyBackground = Color(0xFFB71C1C)

// 긴급 상황 일반 경고 화면. 캐릭터 연출 없이 큰 글씨로 안내만 한다. 조작 버튼 없음.
// 글씨 크기는 앱 창의 짧은 변에 비례한다 (주행 중 한눈에 읽혀야 함)
@Composable
fun EmergencyScreen(types: Set<EmergencyType>) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = EmergencyBackground,
        contentColor = Color.White,
    ) {
        BoxWithConstraints {
            val h = appWindow().unit
            // 운전 중에 읽는 문구라 어절 단위로 줄바꿈한다 ("점검 / 을"처럼 끊기지 않게)
            val wordWrap = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = h * 0.08f, vertical = h * 0.06f),
                verticalArrangement = Arrangement.spacedBy(h * 0.04f, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.emergency_title),
                    fontSize = (h.value * 0.09f).sp,
                    fontWeight = FontWeight.Bold,
                    style = wordWrap,
                )
                types.forEach { type ->
                    Text(
                        text = stringResource(type.messageRes()),
                        fontSize = (h.value * 0.06f).sp,
                        lineHeight = (h.value * 0.085f).sp,
                        style = wordWrap,
                    )
                }
            }
        }
    }
}

@StringRes
private fun EmergencyType.messageRes(): Int = when (this) {
    EmergencyType.ENGINE -> R.string.emergency_engine
    EmergencyType.BRAKE -> R.string.emergency_brake
    EmergencyType.STEERING -> R.string.emergency_steering
    EmergencyType.AIRBAG -> R.string.emergency_airbag
    EmergencyType.COOLANT_OVERHEAT -> R.string.emergency_coolant_overheat
    EmergencyType.TIRE_PRESSURE -> R.string.emergency_tire_pressure
}

@Preview(widthDp = 1340, heightDp = 950)
@Composable
private fun EmergencyScreenPreview() {
    MaterialTheme {
        EmergencyScreen(setOf(EmergencyType.BRAKE, EmergencyType.TIRE_PRESSURE))
    }
}
