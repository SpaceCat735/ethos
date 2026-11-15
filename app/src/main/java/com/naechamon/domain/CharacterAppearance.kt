package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning

// 캐릭터 몸의 단계. 종족·진화는 여기에 값을 늘린다 (기획안 3-1)
enum class CharacterStage {
    // 부화 전 2주 (주행 데이터 모으는 중)
    EGG,
    // 운전자의 차 마스코트 (데모용 1종)
    CAR,
}

// 화면이 캐릭터를 그릴 때 받는 유일한 결과. 겹 순서: 몸 → 표정 → 상태 표시 (→ 꾸미기, 추후)
data class CharacterAppearance(
    val stage: CharacterStage,
    val expression: CharacterExpression,
    val marks: Set<CharacterMark> = emptySet(),
)

// 표정 판정과 상태 표시 판정을 한 번에 묶는다. 화면 층은 이 결과만 받는다.
object AppearanceDecider {

    fun decide(
        warnings: Set<VehicleWarning>,
        continuousDrivingMillis: Long,
        consumableLevels: Collection<ConditionLevel>,
        tireLevel: ConditionLevel?,
        lastEventTimeMillis: Long?,
        nowMillis: Long,
        mood: Int,
        slowDrivingMillis: Long = 0L,
        // 부화 여부. 차BTI 판정(#20)이 생기면 거기서 정한다. 지금은 항상 부화 상태
        hatched: Boolean = true,
    ): CharacterAppearance = CharacterAppearance(
        stage = if (hatched) CharacterStage.CAR else CharacterStage.EGG,
        expression = ExpressionDecider.decide(
            warnings = warnings,
            continuousDrivingMillis = continuousDrivingMillis,
            consumableLevels = consumableLevels,
            lastEventTimeMillis = lastEventTimeMillis,
            nowMillis = nowMillis,
            mood = mood,
            slowDrivingMillis = slowDrivingMillis,
        ),
        marks = CharacterMarkDecider.decide(warnings, tireLevel),
    )
}
