package com.naechamon.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naechamon.R
import com.naechamon.data.model.VehicleWarning
import com.naechamon.domain.BodyPart
import com.naechamon.domain.CharacterAppearance
import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.CharacterStage
import com.naechamon.domain.CheckupItem
import com.naechamon.domain.CheckupLevel
import com.naechamon.domain.CheckupMood
import com.naechamon.domain.ConditionLevel
import com.naechamon.domain.Consumable
import com.naechamon.domain.ConsumableStatus
import com.naechamon.domain.HealthCheckup
import com.naechamon.domain.HealthCheckupBuilder
import com.naechamon.domain.ServiceInterval
import java.time.YearMonth

// 월간 점검표(건강검진 결과지). 정차 중에만 열린다 (MainScreen이 Parked 모드에서만 그린다).
// 플레오스 기본 앱 틀: 제목 바("‹ 월간 점검표") 아래 왼쪽 캐릭터 한 칸 + 오른쪽 부위별 정상/관찰 필요/점검 권장 행 목록.
// 가로 창은 좌우, 세로 창(내비 옆 반쪽)은 위아래로 배치한다. 색은 Theme.kt 토큰만 쓴다.
@Composable
fun HealthCheckupScreen(checkup: HealthCheckup, appearance: CharacterAppearance, todayDistanceKm: Double, onClose: () -> Unit) {
    // 기기 뒤로 가기도 화면 안 "‹"와 같이 점검표를 닫는다
    BackHandler(onBack = onClose)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ethos.bg)) {
        val window = appWindow()
        val h = window.unit
        val windowWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize()) {
            // 뒤로: 정차 중 화면이라 조작 허용. 큰 터치 영역
            EthosTitleBar(todayDistanceKm, h, title = stringResource(R.string.nav_checkup), onBack = onClose)
            if (window.isPortrait) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = h * 0.04f, vertical = h * 0.03f),
                    horizontalArrangement = Arrangement.spacedBy(h * 0.04f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterFace(appearance, Modifier.size(h * 0.3f))
                    Column(modifier = Modifier.weight(1f)) {
                        CheckupSay(checkup, h, TextAlign.Start, Modifier)
                        CheckupBadge(checkup, h)
                    }
                }
                HorizontalDivider(color = ethos.line, thickness = 1.dp)
                CheckupSheet(checkup, h, columns = 1, modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxHeight().width(windowWidth * 0.33f).padding(h * 0.034f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CharacterFace(appearance, Modifier.size(h * 0.37f))
                        CheckupSay(checkup, h, TextAlign.Center, Modifier.padding(top = h * 0.008f))
                        CheckupBadge(checkup, h)
                    }
                    VerticalDivider(color = ethos.line, thickness = 1.dp)
                    CheckupSheet(checkup, h, columns = 2, modifier = Modifier.fillMaxHeight().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CheckupSay(checkup: HealthCheckup, h: Dp, align: TextAlign, modifier: Modifier) {
    Text(
        text = stringResource(checkup.mood.sayRes()),
        color = ethos.ink,
        fontSize = (h.value * 0.025f).sp,
        lineHeight = (h.value * 0.025f * LINE_HEIGHT).sp,
        textAlign = align,
        // 어절 단위로 줄바꿈 (단어 중간에서 끊기지 않게)
        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

// 관리 양호 표시: 각진 연회색 칩
@Composable
private fun CheckupBadge(checkup: HealthCheckup, h: Dp) {
    if (!checkup.wellMaintained) return
    SquareChip(stringResource(R.string.checkup_badge), ethos.ink, h, Modifier.padding(top = h * 0.02f))
}

@Composable
private fun SquareChip(text: String, color: Color, h: Dp, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = (h.value * 0.02f).sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(ethos.surfaceAlt)
            .padding(horizontal = h * 0.016f, vertical = h * 0.007f),
    )
}

// 결과지: 제목 줄 + 부위별 행 목록(1px 선) + 아래 안내
@Composable
private fun CheckupSheet(checkup: HealthCheckup, h: Dp, columns: Int, modifier: Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = h * 0.04f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(h * 0.1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checkup_title, checkup.month.monthValue),
                color = ethos.ink,
                fontSize = (h.value * 0.029f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = ethos.line, thickness = 1.dp)
        // 가로 창은 두 줄씩 짝지어 2열, 세로 창은 1열
        checkup.items.chunked(columns).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(h * 0.04f)) {
                pair.forEach { item -> CheckupRow(item, h, Modifier.weight(1f)) }
                // 홀수 개면 빈 칸으로 줄을 맞춘다
                repeat(columns - pair.size) { Column(modifier = Modifier.weight(1f)) {} }
            }
        }
        Text(
            text = stringResource(R.string.checkup_footer),
            color = ethos.inkSoft,
            fontSize = (h.value * 0.022f).sp,
            lineHeight = (h.value * 0.022f * LINE_HEIGHT).sp,
            style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph),
            modifier = Modifier.padding(vertical = h * 0.03f),
        )
    }
}

// 부위 이름을 앞에, 실제 시스템 이름을 작게 뒤에. 오른쪽에 각진 칩(정상 회색·관찰 필요 주황·점검 권장 빨강)
@Composable
private fun CheckupRow(item: CheckupItem, h: Dp, modifier: Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(h * 0.085f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(h * 0.01f)) {
                Text(text = stringResource(item.part.nameRes()), color = ethos.ink, fontSize = (h.value * 0.027f).sp, fontWeight = FontWeight.Medium)
                Text(text = stringResource(item.part.systemRes()), color = ethos.inkSoft, fontSize = (h.value * 0.02f).sp)
            }
            val ink = when (item.level) {
                CheckupLevel.NORMAL -> ethos.inkSoft
                CheckupLevel.WATCH -> ethos.warn
                CheckupLevel.CHECK -> ethos.alert
            }
            SquareChip(stringResource(item.level.labelRes()), ink, h)
        }
        HorizontalDivider(color = ethos.line, thickness = 1.dp)
    }
}

@StringRes
private fun BodyPart.nameRes(): Int = when (this) {
    BodyPart.HEART -> R.string.part_heart
    BodyPart.LEGS -> R.string.part_legs
    BodyPart.ARMS -> R.string.part_arms
    BodyPart.FEET -> R.string.part_feet
    BodyPart.ENERGY -> R.string.part_energy
    BodyPart.ARMOR -> R.string.part_armor
    BodyPart.STOMACH -> R.string.part_stomach
    BodyPart.EYES -> R.string.part_eyes
}

@StringRes
private fun BodyPart.systemRes(): Int = when (this) {
    BodyPart.HEART -> R.string.part_heart_system
    BodyPart.LEGS -> R.string.part_legs_system
    BodyPart.ARMS -> R.string.part_arms_system
    BodyPart.FEET -> R.string.part_feet_system
    BodyPart.ENERGY -> R.string.part_energy_system
    BodyPart.ARMOR -> R.string.part_armor_system
    BodyPart.STOMACH -> R.string.part_stomach_system
    BodyPart.EYES -> R.string.part_eyes_system
}

@StringRes
private fun CheckupLevel.labelRes(): Int = when (this) {
    CheckupLevel.NORMAL -> R.string.checkup_level_normal
    CheckupLevel.WATCH -> R.string.checkup_level_watch
    CheckupLevel.CHECK -> R.string.checkup_level_check
}

@StringRes
private fun CheckupMood.sayRes(): Int = when (this) {
    CheckupMood.GOOD -> R.string.checkup_say_good
    CheckupMood.TIRED -> R.string.checkup_say_tired
    CheckupMood.HUNGRY -> R.string.checkup_say_hungry
    CheckupMood.SICK -> R.string.checkup_say_sick
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun HealthCheckupPreview() {
    val status = { c: Consumable, l: ConditionLevel -> ConsumableStatus(c, l, null, null, ServiceInterval(null, null)) }
    val checkup = HealthCheckupBuilder.build(
        warnings = setOf(VehicleWarning.TPMS),
        consumables = listOf(status(Consumable.ENGINE_OIL, ConditionLevel.NOTICE)),
        month = YearMonth.of(2026, 10),
    )
    EthosTheme {
        HealthCheckupScreen(checkup, CharacterAppearance(CharacterStage.CAR, CharacterExpression.NORMAL), 12.4, onClose = {})
    }
}
