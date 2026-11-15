package com.naechamon.domain

// 시동 인사 종류. 문구는 strings.xml에서 고른다 (기획안 v2 시나리오 A 1번)
enum class GreetingType {
    // 아직 알 (운전 습관 모으는 중)
    EGG,
    // 소모품 '주의'·'예고'가 있을 때
    HUNGRY,
    MORNING,
    DAY,
    EVENING,
}

// 시동 인사 기준값 (앱 기본값)
object GreetingRules {
    const val MORNING_START_HOUR = 5
    const val MORNING_END_HOUR = 11
    const val EVENING_START_HOUR = 18
}

// 우선순위: 알 > 배고픔 > 시간대
object GreetingDecider {

    fun decide(hour: Int, hatched: Boolean, worstConsumable: ConditionLevel?): GreetingType = when {
        !hatched -> GreetingType.EGG
        worstConsumable != null && worstConsumable != ConditionLevel.NORMAL -> GreetingType.HUNGRY
        hour in GreetingRules.MORNING_START_HOUR until GreetingRules.MORNING_END_HOUR -> GreetingType.MORNING
        hour >= GreetingRules.EVENING_START_HOUR -> GreetingType.EVENING
        else -> GreetingType.DAY
    }
}
