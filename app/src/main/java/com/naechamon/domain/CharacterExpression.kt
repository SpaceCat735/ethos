package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning

// 캐릭터 표정 8종 (기획안 7-4). 주행 중에는 그림이 바뀌기만 하고 움직이지 않는다
enum class CharacterExpression { NORMAL, HAPPY, SAD, HURT, SLEEPY, HUNGRY, BORED, PROUD }

// 우선순위: 아픔 > 시무룩(위험운전 직후 10초, 순간 반응이라 먼저) > 졸림 > 답답(정체) > 뿌듯(부드러운 운전)
// > 배고픔 > 기분(기쁨/기본). 지금 주행 상황에 대한 반응을 소모품 상태(배고픔)보다 먼저 보여 준다.
// 배고픔은 시동 인사·주차 리포트에서 따로 알려 준다.
// 긴급 경고등은 화면 모드(DisplayModeDecider)에서 일반 경고 화면으로 바뀌므로 여기서는 보지 않는다.
object ExpressionDecider {

    fun decide(
        warnings: Set<VehicleWarning>,
        continuousDrivingMillis: Long,
        consumableLevels: Collection<ConditionLevel>,
        lastEventTimeMillis: Long?,
        nowMillis: Long,
        mood: Int,
        // 정체 속도로 이어서 기어간 시간 (LiveDrivingTracker.slowDrivingMillis)
        slowDrivingMillis: Long = 0L,
    ): CharacterExpression = with(CharacterRules) {
        when {
            warnings.any { it !in SafetyRules.emergencyWarnings } -> CharacterExpression.HURT
            lastEventTimeMillis != null && nowMillis - lastEventTimeMillis < SAD_AFTER_EVENT_MILLIS -> CharacterExpression.SAD
            continuousDrivingMillis >= SLEEPY_CONTINUOUS_DRIVING_MILLIS -> CharacterExpression.SLEEPY
            slowDrivingMillis >= BORED_SLOW_DRIVING_MILLIS -> CharacterExpression.BORED
            continuousDrivingMillis >= PROUD_SMOOTH_DRIVING_MILLIS &&
                (lastEventTimeMillis == null || nowMillis - lastEventTimeMillis >= PROUD_SMOOTH_DRIVING_MILLIS) -> CharacterExpression.PROUD
            ConditionLevel.CAUTION in consumableLevels -> CharacterExpression.HUNGRY
            mood >= HAPPY_MOOD_MIN -> CharacterExpression.HAPPY
            else -> CharacterExpression.NORMAL
        }
    }
}
