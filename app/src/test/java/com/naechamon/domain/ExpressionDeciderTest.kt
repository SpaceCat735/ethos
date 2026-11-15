package com.naechamon.domain

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import com.naechamon.data.model.VehicleWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionDeciderTest {

    private val twoHours = CharacterRules.SLEEPY_CONTINUOUS_DRIVING_MILLIS

    // 기본값은 아무 일도 없는 상태(기분 50 → 기본 표정)
    private fun decide(
        warnings: Set<VehicleWarning> = emptySet(),
        continuousDrivingMillis: Long = 0L,
        consumableLevels: Collection<ConditionLevel> = emptyList(),
        lastEventTimeMillis: Long? = null,
        nowMillis: Long = 100_000L,
        mood: Int = 50,
        slowDrivingMillis: Long = 0L,
    ) = ExpressionDecider.decide(warnings, continuousDrivingMillis, consumableLevels, lastEventTimeMillis, nowMillis, mood, slowDrivingMillis)

    private val tenMinutes = CharacterRules.PROUD_SMOOTH_DRIVING_MILLIS
    private val threeMinutes = CharacterRules.BORED_SLOW_DRIVING_MILLIS

    @Test
    fun nothingHappened_isNormal() {
        assertEquals(CharacterExpression.NORMAL, decide())
    }

    @Test
    fun moodBoundary() {
        assertEquals(CharacterExpression.NORMAL, decide(mood = 69))
        assertEquals(CharacterExpression.HAPPY, decide(mood = 70))
        assertEquals(CharacterExpression.HAPPY, decide(mood = 100))
        assertEquals(CharacterExpression.NORMAL, decide(mood = 0))
    }

    @Test
    fun nonEmergencyWarning_isHurt() {
        assertEquals(CharacterExpression.HURT, decide(warnings = setOf(VehicleWarning.LOW_VOLT_BATTERY)))
    }

    @Test
    fun emergencyWarningAlone_isNotHurt() {
        // 긴급 경고등은 일반 경고 화면이 맡는다
        assertEquals(CharacterExpression.NORMAL, decide(warnings = setOf(VehicleWarning.ENGINE)))
    }

    @Test
    fun hurt_beatsEverything() {
        val expression = decide(
            warnings = setOf(VehicleWarning.LOW_VOLT_BATTERY),
            continuousDrivingMillis = twoHours,
            consumableLevels = listOf(ConditionLevel.CAUTION),
            lastEventTimeMillis = 99_000L,
            mood = 90,
        )
        assertEquals(CharacterExpression.HURT, expression)
    }

    @Test
    fun sleepy_beatsHungryHappy_butNotRecentSad() {
        val sleepyAndHungry = decide(continuousDrivingMillis = twoHours, consumableLevels = listOf(ConditionLevel.CAUTION), mood = 90)
        assertEquals(CharacterExpression.SLEEPY, sleepyAndHungry)
        // 급정거 직후 10초는 졸려도 시무룩
        val justBraked = decide(continuousDrivingMillis = twoHours, consumableLevels = listOf(ConditionLevel.CAUTION), lastEventTimeMillis = 99_000L, mood = 90)
        assertEquals(CharacterExpression.SAD, justBraked)
    }

    @Test
    fun sleepyBoundary() {
        // 20초 전에 급정거가 있었으면 뿌듯하지 않으므로 기본 표정
        assertEquals(CharacterExpression.NORMAL, decide(continuousDrivingMillis = twoHours - 1, lastEventTimeMillis = 80_000L))
        assertEquals(CharacterExpression.SLEEPY, decide(continuousDrivingMillis = twoHours, lastEventTimeMillis = 80_000L))
    }

    @Test
    fun hungry_beatsHappy_butNotRecentSad() {
        assertEquals(CharacterExpression.HUNGRY, decide(consumableLevels = listOf(ConditionLevel.NORMAL, ConditionLevel.CAUTION), mood = 90))
        // 급정거 직후 10초는 배고파도 시무룩, 지나면 다시 배고픔
        assertEquals(CharacterExpression.SAD, decide(consumableLevels = listOf(ConditionLevel.CAUTION), lastEventTimeMillis = 99_000L, mood = 90))
        assertEquals(CharacterExpression.HUNGRY, decide(consumableLevels = listOf(ConditionLevel.CAUTION), lastEventTimeMillis = 80_000L, mood = 90))
    }

    @Test
    fun noticeLevel_isNotHungry() {
        assertEquals(CharacterExpression.NORMAL, decide(consumableLevels = listOf(ConditionLevel.NOTICE)))
    }

    @Test
    fun sad_beatsHappy() {
        assertEquals(CharacterExpression.SAD, decide(lastEventTimeMillis = 99_000L, mood = 90))
    }

    @Test
    fun sadLastsUnder10Seconds() {
        assertEquals("직후", CharacterExpression.SAD, decide(lastEventTimeMillis = 100_000L, nowMillis = 100_000L))
        assertEquals("9.999초 뒤", CharacterExpression.SAD, decide(lastEventTimeMillis = 100_000L, nowMillis = 109_999L))
        assertEquals("10초 뒤", CharacterExpression.NORMAL, decide(lastEventTimeMillis = 100_000L, nowMillis = 110_000L))
    }

    @Test
    fun hardStop_isSadThenBackAfter10Seconds() {
        // 실시간 추적기 + 표정 판정을 함께 확인
        val tracker = LiveDrivingTracker()
        tracker.update(VehicleState(Gear.DRIVE, 40f), 0L)
        val event = tracker.update(VehicleState(Gear.DRIVE, 0f), 1_000L)
        assertEquals(DrivingEventType.HARD_STOP, event?.type)

        fun expressionAt(now: Long) = decide(
            continuousDrivingMillis = tracker.continuousDrivingMillis(now),
            lastEventTimeMillis = tracker.lastEventTimeMillis,
            nowMillis = now,
            mood = tracker.mood,
        )
        assertEquals(CharacterExpression.SAD, expressionAt(1_000L))
        assertEquals(CharacterExpression.SAD, expressionAt(10_999L))
        assertEquals(CharacterExpression.NORMAL, expressionAt(11_000L))
    }

    @Test
    fun boredBoundary_inTrafficJam() {
        assertEquals(CharacterExpression.NORMAL, decide(slowDrivingMillis = threeMinutes - 1))
        assertEquals(CharacterExpression.BORED, decide(slowDrivingMillis = threeMinutes))
    }

    @Test
    fun bored_beatsProudAndHungry_butNotSadOrSleepy() {
        val jam = threeMinutes
        assertEquals(CharacterExpression.BORED, decide(slowDrivingMillis = jam, continuousDrivingMillis = tenMinutes, consumableLevels = listOf(ConditionLevel.CAUTION)))
        assertEquals(CharacterExpression.SAD, decide(slowDrivingMillis = jam, lastEventTimeMillis = 99_000L))
        assertEquals(CharacterExpression.SLEEPY, decide(slowDrivingMillis = jam, continuousDrivingMillis = twoHours))
    }

    @Test
    fun proud_afterTenSmoothMinutes() {
        assertEquals(CharacterExpression.NORMAL, decide(continuousDrivingMillis = tenMinutes - 1))
        assertEquals(CharacterExpression.PROUD, decide(continuousDrivingMillis = tenMinutes))
        // 배고파도 지금 부드럽게 달리는 중이면 뿌듯이 먼저
        assertEquals(CharacterExpression.PROUD, decide(continuousDrivingMillis = tenMinutes, consumableLevels = listOf(ConditionLevel.CAUTION)))
    }

    @Test
    fun proud_needsTenMinutesSinceLastHardEvent() {
        val now = 2 * tenMinutes
        assertEquals(CharacterExpression.NORMAL, decide(continuousDrivingMillis = now, lastEventTimeMillis = now - tenMinutes + 1, nowMillis = now))
        assertEquals(CharacterExpression.PROUD, decide(continuousDrivingMillis = now, lastEventTimeMillis = now - tenMinutes, nowMillis = now))
    }
}
