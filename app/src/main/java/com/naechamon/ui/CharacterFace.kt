package com.naechamon.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.naechamon.R
import com.naechamon.domain.CharacterAppearance
import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.CharacterMark
import com.naechamon.domain.CharacterStage

// 캐릭터. 같은 1024×1024 캔버스의 PNG를 몸 → 표정 → 상태 표시 순서로 겹친다 (docs/character/README.md).
// 애니메이션 없음(운전 중 표시). 알 상태에서는 표정·상태 표시를 그리지 않는다.
@Composable
fun CharacterFace(appearance: CharacterAppearance, modifier: Modifier = Modifier) {
    val description = stringResource(appearance.expression.descriptionRes())
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val myCar = LocalMyCar.current[appearance.expression]
        if (appearance.stage != CharacterStage.EGG && myCar != null) {
            // 꾸미기에서 저장한 내 차 정지 그림 (표정까지 들어 있음). 3D가 아니라 정지 그림이라 주행 중에도 움직임 없음
            Image(bitmap = myCar, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Layer(bodyOf(appearance.stage))
        }
        if (appearance.stage != CharacterStage.EGG) {
            // 겹 순서가 바뀌지 않게 선언 순서대로 그린다
            val marks = CharacterMark.entries.filter { it in appearance.marks }
            if (myCar == null) {
                Layer(faceOf(appearance.expression))
                marks.forEach { Layer(markOf(it)) }
            } else if (marks.isNotEmpty()) {
                // 상태 표시 그림은 기본 몸(body_car) 위치에 맞춰 그려져 있어 꾸민 차 위에 겹치면 엉뚱한 곳에 놓인다 (#50).
                // 그래서 꾸민 차일 때는 캐릭터 상자 오른쪽 위에 작은 둥근 배지로 따로 보여 준다
                MarkBadges(marks)
            }
        }
    }
}

// 배지 한 개 지름 = 캐릭터 상자 한 변의 20%. 기본 몸 그림은 1024 캔버스에서 y223부터 시작해(약 22%)
// 위쪽 20% 띠는 비어 있으므로, 배지를 오른쪽 위 한 줄에 두면 차 그림(바퀴·얼굴)을 가리지 않는다.
// 배지가 최대 3개여도 3×20% + 간격 2×2% = 64%라 상자 안에 들어간다
private const val BADGE_FRACTION = 0.20f
private const val BADGE_GAP_FRACTION = 0.02f

// 꾸민 차일 때 보여 주는 상태 표시 배지 줄. 정지 그림만 그린다 (움직임 없음, 운전 중 표시 규칙)
@Composable
private fun MarkBadges(marks: List<CharacterMark>) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val badge = maxWidth * BADGE_FRACTION
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(maxWidth * BADGE_GAP_FRACTION),
        ) {
            marks.forEach { MarkBadge(it, badge) }
        }
    }
}

@Composable
private fun MarkBadge(mark: CharacterMark, size: Dp) {
    // 닳은 타이어는 배지 전용 그림(#56). 나머지는 캐릭터용 PNG에서 그려진 부분만 잘라 쓴다
    val painter = if (mark == CharacterMark.WORN_SHOES) {
        painterResource(R.drawable.mark_worn_shoes_badge)
    } else {
        val (offset, cropSize) = markCropOf(mark)
        val bitmap = ImageBitmap.imageResource(markOf(mark))
        remember(bitmap, offset, cropSize) { BitmapPainter(bitmap, offset, cropSize) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(size * 0.04f, Color(0xFF9E9E9E), CircleShape)
            .padding(size * 0.14f),
        contentAlignment = Alignment.Center,
    ) {
        // 설명은 바깥 Box에 한 번만 단다
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// 상태 표시 그림(1024×1024)에서 실제로 그려진 부분(불투명 픽셀의 경계 상자)만 잘라 배지에 넣는다.
// 숫자는 각 PNG의 알파 채널에서 잰 경계 상자다 (#50 이슈 측정값, 양 끝 픽셀 포함).
//   mark_sweat      x212–325 y232–467
//   mark_foot_cast  x231–448 y681–898
//   mark_worn_shoes 는 연한 점선이라 잘라 써도 안 보여서 배지 전용 벡터 그림(mark_worn_shoes_badge)을 따로 둔다
// 그림 파일을 다시 그리면 이 숫자도 다시 재야 한다
private val SWEAT_CROP = IntOffset(212, 232) to IntSize(325 - 212 + 1, 467 - 232 + 1)
private val FOOT_CAST_CROP = IntOffset(231, 681) to IntSize(448 - 231 + 1, 898 - 681 + 1)

private fun markCropOf(mark: CharacterMark): Pair<IntOffset, IntSize> = when (mark) {
    CharacterMark.SWEAT -> SWEAT_CROP
    CharacterMark.FOOT_CAST -> FOOT_CAST_CROP
    CharacterMark.WORN_SHOES -> error("닳은 타이어 배지는 전용 그림을 쓴다")
}

// 꾸미기에서 저장한 표정별 내 차 그림. 없으면(아직 안 꾸밈) 기본 그림을 겹쳐 그린다
val LocalMyCar = compositionLocalOf<Map<CharacterExpression, ImageBitmap>> { emptyMap() }

// 3D 차고와 주고받는 표정 이름 (docs/character/layers.js의 face_* 와 같다)
fun CharacterExpression.faceKey(): String = "face_" + name.lowercase()

@Composable
private fun Layer(@DrawableRes res: Int) {
    // 설명은 바깥 Box에 한 번만 단다
    Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.fillMaxSize())
}

// 단계 → 몸 그림. 종족이 늘면 여기에 추가한다
@DrawableRes
private fun bodyOf(stage: CharacterStage): Int = when (stage) {
    CharacterStage.EGG -> R.drawable.body_egg
    CharacterStage.CAR -> R.drawable.body_car
}

// 표정 → 그림 연결은 여기 한 곳에만 둔다
@DrawableRes
private fun faceOf(expression: CharacterExpression): Int = when (expression) {
    CharacterExpression.NORMAL -> R.drawable.face_normal
    CharacterExpression.HAPPY -> R.drawable.face_happy
    CharacterExpression.SAD -> R.drawable.face_sad
    CharacterExpression.HURT -> R.drawable.face_hurt
    CharacterExpression.SLEEPY -> R.drawable.face_sleepy
    CharacterExpression.HUNGRY -> R.drawable.face_hungry
    CharacterExpression.BORED -> R.drawable.face_bored
    CharacterExpression.PROUD -> R.drawable.face_proud
}

@DrawableRes
private fun markOf(mark: CharacterMark): Int = when (mark) {
    CharacterMark.SWEAT -> R.drawable.mark_sweat
    CharacterMark.FOOT_CAST -> R.drawable.mark_foot_cast
    CharacterMark.WORN_SHOES -> R.drawable.mark_worn_shoes
}

@StringRes
private fun CharacterExpression.descriptionRes(): Int = when (this) {
    CharacterExpression.NORMAL -> R.string.expression_normal
    CharacterExpression.HAPPY -> R.string.expression_happy
    CharacterExpression.SAD -> R.string.expression_sad
    CharacterExpression.HURT -> R.string.expression_hurt
    CharacterExpression.SLEEPY -> R.string.expression_sleepy
    CharacterExpression.HUNGRY -> R.string.expression_hungry
    CharacterExpression.BORED -> R.string.expression_bored
    CharacterExpression.PROUD -> R.string.expression_proud
}
