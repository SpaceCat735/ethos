package com.naechamon.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naechamon.R
import com.naechamon.domain.CarBtiType
import com.naechamon.domain.ConditionLevel
import com.naechamon.domain.Consumable
import com.naechamon.domain.ConsumableStatus
import com.naechamon.domain.DrivingEvent
import com.naechamon.domain.DrivingEventType
import com.naechamon.domain.ParkedReport
import com.naechamon.domain.PraiseLevel
import com.naechamon.domain.ServiceInterval
import com.naechamon.domain.SevereCriterion
import java.util.Locale

// 소모품 상세: 플레오스 설정 오른쪽 칸처럼 1px 선으로 나눈 행 목록. 정차 중에만 보인다.
// 소모품 행(이름 / 상태 두 줄 / 상태 글자 / 정비 완료 기록) → 주행 행(오늘 주행·위험운전 시각) → 운전 습관 분류와 정비 기준.
// 화면에서 계산하지 않는다: 모든 값은 두뇌 층의 ParkedReport에서 온다.
@Composable
fun ParkedReportPanel(
    report: ParkedReport,
    h: Dp,
    onServiceDone: (Consumable) -> Unit,
    modifier: Modifier = Modifier,
    // 세로 창: 이름 칸을 줄이고 상태 글자 칸을 뺀다 (상태는 가운데 굵은 줄에 이미 있다)
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = h * 0.04f, vertical = h * 0.01f),
    ) {
        report.consumables.forEach { status -> ConsumableRow(status, h, compact, onServiceDone) }
        DrivingRows(report, h, compact)
        Footer(report, h)
    }
}

// 행 아래 1px 구분선
private fun Modifier.bottomLine(color: Color): Modifier = drawBehind {
    val y = size.height - 0.5f
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

@Composable
private fun ConsumableRow(status: ConsumableStatus, h: Dp, compact: Boolean, onServiceDone: (Consumable) -> Unit) {
    ListRow(h, name = stringResource(status.consumable.labelRes()), compact = compact) {
        Column(modifier = Modifier.weight(1f)) {
            RowMain(glanceValue(status), h)
            // 취급설명서에 주기가 없는 소모품은 "일반 권장값"이라고 밝힌다
            val cycleRes = if (status.fromManual) R.string.row_cycle else R.string.row_cycle_default
            RowSub(stringResource(cycleRes, intervalText(status.interval)), h)
        }
        if (!compact) {
            Text(
                text = stringResource(status.level.labelRes()),
                color = status.level.color(),
                fontSize = (h.value * 0.024f).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(h * 0.144f),
            )
        }
        // 정비 완료 기록: 각진 테두리 버튼. 큰 터치 영역, 정차 중에만 보이는 화면
        Text(
            text = stringResource(R.string.report_service_done),
            color = ethos.ink,
            fontSize = (h.value * 0.022f).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .border(maxOf(1.dp, h * 0.0017f), ethos.ink)
                .clickable { onServiceDone(status.consumable) }
                .padding(horizontal = h * 0.024f, vertical = h * 0.016f),
        )
    }
}

// 주행 섹션: 오늘 주행(거리·연비)과 위험운전 시각 목록
@Composable
private fun DrivingRows(report: ParkedReport, h: Dp, compact: Boolean) {
    ListRow(h, name = stringResource(R.string.tile_today), compact = compact) {
        Column(modifier = Modifier.weight(1f)) {
            RowMain(stringResource(R.string.tile_today_value_short, formatOne(report.distanceKm)), h)
            RowSub(efficiencyText(report), h)
        }
    }
    ListRow(h, name = stringResource(R.string.report_driving_detail_events), compact = compact) {
        Column(modifier = Modifier.weight(1f)) {
            RowMain(stringResource(R.string.glance_brake_value, report.hardBrakeCount, report.hardAccelCount), h)
            if (report.events.isEmpty()) {
                RowSub(stringResource(R.string.report_driving_detail_none), h)
            } else {
                report.events.forEach { event -> RowSub("${stringResource(event.type.labelRes())} · ${eventTime(event)}", h) }
            }
        }
    }
}

// 맨 아래: 운전 습관 분류와 정비 기준(가혹/일반)
@Composable
private fun Footer(report: ParkedReport, h: Dp) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = h * 0.03f), verticalArrangement = Arrangement.spacedBy(h * 0.008f)) {
        Text(
            text = stringResource(R.string.carbti_class, stringResource(report.carBti.labelRes())),
            color = ethos.ink,
            fontSize = (h.value * 0.022f).sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = basisFootText(report),
            color = ethos.inkSoft,
            fontSize = (h.value * 0.022f).sp,
            lineHeight = (h.value * 0.022f * LINE_HEIGHT).sp,
            style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph),
        )
    }
}

// 행 하나: 이름(폭 고정) + 내용. 아래 1px 선
@Composable
private fun ListRow(h: Dp, name: String, compact: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = h * 0.127f)
            .bottomLine(ethos.line)
            .padding(vertical = h * 0.02f),
        horizontalArrangement = Arrangement.spacedBy(h * 0.024f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = ethos.ink,
            fontSize = (h.value * 0.027f).sp,
            lineHeight = (h.value * 0.027f * LINE_HEIGHT).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(h * if (compact) 0.19f else 0.253f),
        )
        content()
    }
}

@Composable
private fun RowMain(text: String, h: Dp) {
    Text(
        text = text,
        color = ethos.ink,
        fontSize = (h.value * 0.025f).sp,
        lineHeight = (h.value * 0.025f * LINE_HEIGHT).sp,
        fontWeight = FontWeight.Medium,
        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
    )
}

@Composable
private fun RowSub(text: String, h: Dp) {
    Text(
        text = text,
        color = ethos.inkSoft,
        fontSize = (h.value * 0.024f).sp,
        lineHeight = (h.value * 0.024f * LINE_HEIGHT).sp,
        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
    )
}

// 연비 한 줄: 공인 연비가 있으면 비교까지, 데이터 연결 전이면 안내만
@Composable
private fun efficiencyText(report: ParkedReport): String {
    val efficiency = report.tripEfficiency
    val rated = report.ratedEfficiency
    return if (efficiency != null && rated != null && rated > 0) {
        stringResource(R.string.report_driving_detail_efficiency, formatOne(efficiency), ((efficiency / rated - 1) * 100).toInt())
    } else {
        stringResource(R.string.report_driving_detail_efficiency_unknown)
    }
}

@Composable
private fun eventTime(event: DrivingEvent): String {
    val totalSec = event.timeMillis / 1000
    return stringResource(R.string.report_event_at, (totalSec / 60).toInt(), (totalSec % 60).toInt())
}

// 정비 기준 한 줄 (소모품 상세 맨 아래)
@Composable
private fun basisFootText(report: ParkedReport): String = when {
    !report.severe.enoughData -> stringResource(R.string.report_consumable_basis_egg)
    report.severe.isSevere -> stringResource(R.string.basis_foot_severe, severeLabels(report))
    else -> stringResource(R.string.basis_foot_normal)
}

@Composable
private fun severeLabels(report: ParkedReport): String =
    report.severe.matched.map { stringResource(it.labelRes()) }.joinToString(", ")

// 교체 주기: "7,500 km", "12개월", "50,000 km 또는 48개월"
@Composable
private fun intervalText(interval: ServiceInterval): String {
    val km = interval.km
    val months = interval.months
    return when {
        km != null && months != null -> stringResource(R.string.report_remaining_both, formatInt(km), stringResource(R.string.report_remaining_months, months))
        km != null -> stringResource(R.string.report_remaining_km, formatInt(km))
        months != null -> stringResource(R.string.report_remaining_months, months)
        else -> ""
    }
}

@StringRes
private fun SevereCriterion.labelRes(): Int = when (this) {
    SevereCriterion.SHORT_TRIPS -> R.string.severe_short_trips
    SevereCriterion.STOP_AND_GO -> R.string.severe_stop_and_go
    SevereCriterion.HARD_EVENTS -> R.string.severe_hard_events
}

@StringRes
private fun CarBtiType?.labelRes(): Int = when (this) {
    null -> R.string.carbti_egg
    CarBtiType.SQUIRREL -> R.string.carbti_squirrel
    CarBtiType.TURTLE -> R.string.carbti_turtle
    CarBtiType.MIGRATORY_BIRD -> R.string.carbti_migratory_bird
    CarBtiType.WEEKEND_TRAVELER -> R.string.carbti_weekend_traveler
}

private val ConsumableStatus.isDue: Boolean
    get() = (remainingKm != null && remainingKm <= 0) || (remainingDays != null && remainingDays <= 0)

// 한 달 미만이면 '일', 그 이상은 '개월'로
@Composable
private fun periodText(days: Long): String =
    if (days < 31) stringResource(R.string.report_remaining_days, days) else stringResource(R.string.report_remaining_months, days / 30)

@Composable
private fun remainingText(status: ConsumableStatus): String {
    val km = status.remainingKm
    val days = status.remainingDays
    return when {
        status.isDue -> stringResource(R.string.report_remaining_due)
        km != null && days != null -> stringResource(R.string.report_remaining_both, formatInt(km), periodText(days))
        km != null -> stringResource(R.string.report_remaining_km, formatInt(km))
        days != null -> periodText(days)
        else -> ""
    }
}

private fun formatOne(value: Double) = String.format(Locale.KOREA, "%.1f", value)
// 천 단위 쉼표 (예: 7,500)
private fun formatInt(value: Int) = String.format(Locale.KOREA, "%,d", value)

@StringRes
private fun DrivingEventType.labelRes(): Int = when (this) {
    DrivingEventType.HARD_STOP -> R.string.report_event_hard_stop
    DrivingEventType.HARD_DECELERATION -> R.string.report_event_hard_decel
    DrivingEventType.HARD_ACCELERATION -> R.string.report_event_hard_accel
    DrivingEventType.HARD_START -> R.string.report_event_hard_start
}

// 소모품 이름 (리포트·캐릭터 대사가 같이 쓴다)
@StringRes
internal fun Consumable.labelRes(): Int = when (this) {
    Consumable.ENGINE_OIL -> R.string.consumable_engine_oil
    Consumable.TIRE -> R.string.consumable_tire
    Consumable.BRAKE_PAD -> R.string.consumable_brake_pad
    Consumable.AC_FILTER -> R.string.consumable_ac_filter
    Consumable.WIPER -> R.string.consumable_wiper
}

@StringRes
private fun ConditionLevel.labelRes(): Int = when (this) {
    ConditionLevel.NORMAL -> R.string.level_normal
    ConditionLevel.NOTICE -> R.string.level_notice
    ConditionLevel.CAUTION -> R.string.level_caution
}

// 상태 글자 색: 정상 회색, 주의 주황, 교체 권장 빨강
@Composable
private fun ConditionLevel.color(): Color = when (this) {
    ConditionLevel.NORMAL -> ethos.inkSoft
    ConditionLevel.NOTICE -> ethos.warn
    ConditionLevel.CAUTION -> ethos.alert
}

// ---------- 차량 상태: 타일 격자 2×2 + 정비 기준 한 줄 ----------

// 엔진오일 / 타이어 / 제동 / 오늘 주행 타일과 아래 가로 긴 '정비 기준' 줄. 1px 선으로 나눈다. 누르면 소모품 상세로.
// 화면에서 계산하지 않는다: 소모품 상태·급제동 수는 두뇌 층의 ParkedReport 값 그대로
@Composable
fun StatusTiles(report: ParkedReport, todayDistanceKm: Double, h: Dp, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val oil = report.consumables.firstOrNull { it.consumable == Consumable.ENGINE_OIL }
    val tire = report.consumables.firstOrNull { it.consumable == Consumable.TIRE }
    val brakeLevel = if (report.praise == PraiseLevel.GREAT) ConditionLevel.NORMAL else ConditionLevel.NOTICE
    val efficiency = report.tripEfficiency
    val todayValue = if (efficiency != null) {
        stringResource(R.string.tile_today_value, formatOne(todayDistanceKm), formatOne(efficiency))
    } else {
        stringResource(R.string.tile_today_value_short, formatOne(todayDistanceKm))
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Tile(IconKind.DROP, stringResource(R.string.glance_engine_oil), tileValue(oil), (oil?.level ?: ConditionLevel.NORMAL).color(), h, onOpen, Modifier.weight(1f))
            Tile(IconKind.TIRE, stringResource(R.string.glance_tire), tileValue(tire), (tire?.level ?: ConditionLevel.NORMAL).color(), h, onOpen, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Tile(IconKind.BRAKE, stringResource(R.string.glance_brake), stringResource(R.string.glance_brake_value, report.hardBrakeCount, report.hardAccelCount), brakeLevel.color(), h, onOpen, Modifier.weight(1f))
            Tile(IconKind.ROUTE, stringResource(R.string.tile_today), todayValue, ethos.inkSoft, h, onOpen, Modifier.weight(1f))
        }
        BasisRow(report, oil, h, onOpen)
    }
}

// 기록이 없는 소모품은 값을 지어내지 않고 "기록 없음"
@Composable
private fun tileValue(status: ConsumableStatus?): String =
    if (status == null) stringResource(R.string.row_no_record) else glanceValue(status)

// "정상 · 잔여 12,680 km", "교체 권장 · 잔여 0 km"
@Composable
private fun glanceValue(status: ConsumableStatus): String {
    val km = status.remainingKm
    return when {
        status.isDue && km != null && km <= 0 -> stringResource(R.string.glance_due)
        status.isDue -> stringResource(R.string.glance_due_time)
        else -> stringResource(R.string.glance_remaining, stringResource(status.level.labelRes()), remainingText(status))
    }
}

// 타일: 왼쪽 위 선 아이콘, 아래쪽에 제목(굵게)과 상태 값. 오른쪽·아래 1px 선
@Composable
private fun Tile(icon: IconKind, title: String, value: String, valueColor: Color, h: Dp, onClick: () -> Unit, modifier: Modifier) {
    val line = ethos.line
    Column(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(line, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), w)
                drawLine(line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), w)
            }
            .clickable(onClick = onClick)
            .padding(horizontal = h * 0.034f, vertical = h * 0.03f),
    ) {
        LineIcon(icon, h * 0.044f, ethos.ink, strokeRatio = 0.05f)
        Spacer(Modifier.weight(1f))
        Text(text = title, color = ethos.ink, fontSize = (h.value * 0.029f).sp, lineHeight = (h.value * 0.029f * LINE_HEIGHT).sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            color = valueColor,
            fontSize = (h.value * 0.024f).sp,
            lineHeight = (h.value * 0.024f * LINE_HEIGHT).sp,
            style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
            modifier = Modifier.padding(top = h * 0.008f),
        )
    }
}

// 격자 아래 가로 긴 줄: 정비 기준(가혹/일반) + 설명 + → 화살표
@Composable
private fun BasisRow(report: ParkedReport, oil: ConsumableStatus?, h: Dp, onClick: () -> Unit) {
    val line = ethos.line
    val severe = report.severe.isSevere
    val oilKm = oil?.interval?.km
    val title = stringResource(if (severe) R.string.basis_title_severe else R.string.basis_title_normal)
    val desc = when {
        !report.severe.enoughData -> stringResource(R.string.report_consumable_basis_egg)
        severe && oilKm != null -> stringResource(R.string.basis_desc_severe, severeLabels(report), formatInt(oilKm))
        severe -> stringResource(R.string.report_consumable_basis_severe, severeLabels(report))
        else -> stringResource(R.string.report_consumable_basis_normal)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(h * 0.186f)
            .drawBehind { drawLine(line, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1.dp.toPx()) }
            .clickable(onClick = onClick)
            .padding(horizontal = h * 0.034f),
        horizontalArrangement = Arrangement.spacedBy(h * 0.027f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(IconKind.DOC, h * 0.044f, ethos.ink, strokeRatio = 0.05f)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = ethos.ink, fontSize = (h.value * 0.025f).sp, lineHeight = (h.value * 0.025f * LINE_HEIGHT).sp, fontWeight = FontWeight.Medium)
            Text(
                text = desc,
                color = ethos.inkSoft,
                fontSize = (h.value * 0.022f).sp,
                lineHeight = (h.value * 0.022f * LINE_HEIGHT).sp,
                style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
                modifier = Modifier.padding(top = h * 0.005f),
            )
        }
        LineIcon(IconKind.ARROW, h * 0.044f, ethos.ink, strokeRatio = 0.05f)
    }
}
