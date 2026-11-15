package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceDeciderTest {

    private fun decide(
        warnings: Set<VehicleWarning> = emptySet(),
        tireLevel: ConditionLevel? = null,
        hatched: Boolean = true,
        mood: Int = DrivingRules.MOOD_START,
    ) = AppearanceDecider.decide(
        warnings = warnings,
        continuousDrivingMillis = 0L,
        consumableLevels = emptyList(),
        tireLevel = tireLevel,
        lastEventTimeMillis = null,
        nowMillis = 0L,
        mood = mood,
        hatched = hatched,
    )

    @Test
    fun default_isCarWithNormalFaceAndNoMarks() {
        assertEquals(CharacterAppearance(CharacterStage.CAR, CharacterExpression.NORMAL), decide())
    }

    @Test
    fun notHatched_isEgg() {
        assertEquals(CharacterStage.EGG, decide(hatched = false).stage)
    }

    @Test
    fun expressionAndMarks_areCombined() {
        // 배터리 경고(긴급 아님) → 아픈 표정, 타이어 '주의' → 닳은 신발 표시가 같이 나온다
        val appearance = decide(warnings = setOf(VehicleWarning.LOW_VOLT_BATTERY), tireLevel = ConditionLevel.CAUTION)
        assertEquals(CharacterExpression.HURT, appearance.expression)
        assertEquals(setOf(CharacterMark.WORN_SHOES), appearance.marks)
    }

    @Test
    fun highMood_isHappy() {
        assertEquals(CharacterExpression.HAPPY, decide(mood = CharacterRules.HAPPY_MOOD_MIN).expression)
    }
}
