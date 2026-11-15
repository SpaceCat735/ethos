package com.naechamon.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naechamon.R
import com.naechamon.domain.CarBtiType
import com.naechamon.domain.CareBook
import com.naechamon.domain.CareGrade
import com.naechamon.domain.CareStamp
import kotlinx.coroutines.delay

// 정비 수첩 (정차 중에만). 성장 = 관리 이력의 누적: 정비 완료 기록 하나가 도장 하나, 도장이 쌓이면 관리 등급이 오른다.
// 두뇌 층(CareBook)이 정한 등급·도장만 그린다.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CareBookPane(book: CareBook, h: Dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(h * 0.04f)) {
        Text(stringResource(R.string.carebook_title), color = ethos.inkSoft, fontSize = (h.value * 0.022f).sp)
        Text(
            stringResource(book.grade.labelRes()),
            color = ethos.ink,
            fontSize = (h.value * 0.045f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = h * 0.008f),
        )
        val next = book.stampsToNext
        Text(
            stringResource(R.string.carebook_count, book.stamps.size) + " · " +
                (if (next != null) stringResource(R.string.carebook_to_next, next) else stringResource(R.string.carebook_top)),
            color = ethos.inkSoft,
            fontSize = (h.value * 0.024f).sp,
            modifier = Modifier.padding(top = h * 0.006f, bottom = h * 0.03f),
        )
        if (book.stamps.isEmpty()) {
            Text(stringResource(R.string.carebook_empty), color = ethos.inkSoft, fontSize = (h.value * 0.024f).sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(h * 0.024f), verticalArrangement = Arrangement.spacedBy(h * 0.024f)) {
                book.newestFirst.forEach { StampMark(it, h * 0.16f) }
            }
        }
        Text(
            stringResource(R.string.carebook_note),
            color = ethos.inkSoft,
            fontSize = (h.value * 0.019f).sp,
            modifier = Modifier.padding(top = h * 0.04f),
        )
    }
}

// 도장 하나: 원 테두리 안에 소모품 이름과 날짜. 살짝 기울여 손으로 찍은 느낌
@Composable
private fun StampMark(stamp: CareStamp, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).rotate(-8f).border(3.dp, ethos.accent, CircleShape).padding(size * 0.08f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(stamp.consumable.labelRes()),
                color = ethos.accent,
                fontSize = (size.value * 0.15f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = (size.value * 0.17f).sp,
            )
            Text(
                stamp.date?.let { "%02d.%02d".format(it.monthValue, it.dayOfMonth) } ?: "—",
                color = ethos.accent,
                fontSize = (size.value * 0.12f).sp,
            )
        }
    }
}

// 정차 화면 캐릭터 아래 한 줄: 관리 등급
@Composable
internal fun CareGradeLine(grade: CareGrade, h: Dp, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.carebook_grade_line, stringResource(grade.labelRes())),
        color = ethos.ink,
        fontSize = (h.value * 0.021f).sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        modifier = modifier.padding(top = h * 0.01f),
    )
}

// 정비 완료 기록 직후 (정차 중에만) 도장이 한 번 찍히는 연출 + 등급이 오르면 한 줄. 약 1.8초 뒤 사라진다. 반복 없음
@Composable
internal fun StampToast(stamp: CareStamp, gradeUp: CareGrade?, h: Dp, onDone: () -> Unit) {
    val press = remember(stamp) { Animatable(1.8f) }
    LaunchedEffect(stamp) {
        press.animateTo(1f, tween(STAMP_PRESS_MILLIS, easing = FastOutSlowInEasing))
        delay(STAMP_HOLD_MILLIS)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(ethos.bg.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StampMark(stamp, h * 0.26f, Modifier.scale(press.value).alpha((2.2f - press.value).coerceIn(0f, 1f)))
            Text(
                if (gradeUp != null) stringResource(R.string.carebook_grade_up, stringResource(gradeUp.labelRes())) else stringResource(R.string.carebook_stamped),
                color = ethos.ink,
                fontSize = (h.value * 0.03f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = h * 0.03f),
            )
        }
    }
}

// 알에서 깨어난 순간 한 번 (정차 중에만). 확인을 누르면 닫힌다
@Composable
internal fun HatchCard(carBti: CarBtiType?, h: Dp, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ethos.bg.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = h * 1.1f).background(ethos.surface).border(1.dp, ethos.line).padding(h * 0.05f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.hatch_title), color = ethos.ink, fontSize = (h.value * 0.04f).sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.hatch_body, stringResource(carBti.btiLabelRes())),
                color = ethos.inkSoft,
                fontSize = (h.value * 0.025f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = h * 0.02f, bottom = h * 0.04f),
            )
            Text(
                stringResource(R.string.hatch_ok),
                color = ethos.surface,
                fontSize = (h.value * 0.026f).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(ethos.ink).clickable(onClick = onClose).padding(vertical = h * 0.025f),
            )
        }
    }
}

@StringRes
internal fun CareGrade.labelRes(): Int = when (this) {
    CareGrade.NEW -> R.string.care_grade_new
    CareGrade.STARTED -> R.string.care_grade_started
    CareGrade.REGULAR -> R.string.care_grade_regular
    CareGrade.THOROUGH -> R.string.care_grade_thorough
}

@StringRes
private fun CarBtiType?.btiLabelRes(): Int = when (this) {
    null -> R.string.carbti_egg
    CarBtiType.SQUIRREL -> R.string.carbti_squirrel
    CarBtiType.TURTLE -> R.string.carbti_turtle
    CarBtiType.MIGRATORY_BIRD -> R.string.carbti_migratory_bird
    CarBtiType.WEEKEND_TRAVELER -> R.string.carbti_weekend_traveler
}

private const val STAMP_PRESS_MILLIS = 380
private const val STAMP_HOLD_MILLIS = 1400L
