package com.naechamon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingScoreTest {

    // 속도 목록을 intervalMillis 간격 샘플로 만든다
    private fun samples(vararg speeds: Float, intervalMillis: Long = 1000L) =
        speeds.mapIndexed { i, speed -> SpeedSample(i * intervalMillis, speed) }

    private fun eventTypes(vararg speeds: Float, intervalMillis: Long = 1000L) =
        DrivingScorer.detectEvents(samples(*speeds, intervalMillis = intervalMillis)).map { it.type }

    @Test
    fun decelThenStop_isOneBraking() {
        // 40 → 24 → 8 → 0: 급감속이 급정지로 이어지는 한 번의 제동
        assertEquals(listOf(DrivingEventType.HARD_DECELERATION), eventTypes(40f, 24f, 8f, 0f))
    }

    @Test
    fun startThenAccel_isOneAcceleration() {
        assertEquals(listOf(DrivingEventType.HARD_START), eventTypes(0f, 12f, 24f, 36f))
    }

    @Test
    fun brakeThenAccel_areTwoEvents() {
        assertEquals(listOf(DrivingEventType.HARD_DECELERATION, DrivingEventType.HARD_ACCELERATION), eventTypes(40f, 24f, 40f))
    }

    @Test
    fun smoothTrip_raisesMood() {
        val trip = TripInput(samples(0f, 5f, 10f, 15f, 20f, 15f, 10f, 5f, 0f), distanceKm = 3.0)
        val score = DrivingScorer.score(trip, currentMood = 50)
        assertTrue(score.events.isEmpty())
        assertEquals(5, score.moodChange)
        assertEquals(55, score.newMood)
    }

    @Test
    fun hardAcceleration_isDetected() {
        assertEquals(listOf(DrivingEventType.HARD_ACCELERATION), eventTypes(30f, 40f))
    }

    @Test
    fun hardStart_isDetected() {
        assertEquals(listOf(DrivingEventType.HARD_START), eventTypes(0f, 12f))
    }

    @Test
    fun hardDeceleration_isDetected() {
        assertEquals(listOf(DrivingEventType.HARD_DECELERATION), eventTypes(60f, 40f))
    }

    @Test
    fun hardStop_isDetected() {
        assertEquals(listOf(DrivingEventType.HARD_STOP), eventTypes(18f, 2f))
    }

    @Test
    fun hardStopAndDeceleration_overlap_countsOnlyStop() {
        // 20→5는 급감속 조건도 맞지만 급정지 하나로만 센다
        assertEquals(listOf(DrivingEventType.HARD_STOP), eventTypes(20f, 5f))
    }

    @Test
    fun thresholds_areInclusive() {
        // 기준값과 정확히 같으면 사건('이상', '이하')
        assertEquals(listOf(DrivingEventType.HARD_ACCELERATION), eventTypes(6f, 14f))
        assertEquals(listOf(DrivingEventType.HARD_START), eventTypes(5f, 15f))
        assertEquals(listOf(DrivingEventType.HARD_DECELERATION), eventTypes(60f, 46f))
        assertEquals(listOf(DrivingEventType.HARD_STOP), eventTypes(19f, 5f))
    }

    @Test
    fun justBelowThresholds_areNotEvents() {
        assertEquals(emptyList<DrivingEventType>(), eventTypes(6f, 13.9f))
        assertEquals(emptyList<DrivingEventType>(), eventTypes(0f, 9.9f))
        assertEquals(emptyList<DrivingEventType>(), eventTypes(60f, 46.1f))
        // 급가속은 6km/h 이상에서만
        assertEquals(emptyList<DrivingEventType>(), eventTypes(5.9f, 15f))
    }

    @Test
    fun twoSecondInterval_isConvertedToPerSecond() {
        // 2초에 16km/h = 초당 8km/h → 급가속
        assertEquals(listOf(DrivingEventType.HARD_ACCELERATION), eventTypes(10f, 26f, intervalMillis = 2000L))
        // 2초에 14km/h = 초당 7km/h → 사건 아님
        assertEquals(emptyList<DrivingEventType>(), eventTypes(10f, 24f, intervalMillis = 2000L))
    }

    @Test
    fun zeroOrNegativeInterval_isSkipped() {
        val list = listOf(SpeedSample(1000L, 10f), SpeedSample(1000L, 40f), SpeedSample(500L, 0f))
        assertTrue(DrivingScorer.detectEvents(list).isEmpty())
    }

    @Test
    fun continuousSameEvent_countsOnce() {
        // 3초 내내 급가속 → 한 번의 동작으로 1회
        val events = DrivingScorer.detectEvents(samples(20f, 30f, 40f, 50f))
        assertEquals(listOf(DrivingEventType.HARD_ACCELERATION), events.map { it.type })
        assertEquals(1000L, events.single().timeMillis)
    }

    @Test
    fun sameEventAfterBreak_countsAgain() {
        // 급가속 → 보통 가속 → 급가속은 2회
        assertEquals(
            listOf(DrivingEventType.HARD_ACCELERATION, DrivingEventType.HARD_ACCELERATION),
            eventTypes(20f, 30f, 32f, 42f),
        )
    }

    @Test
    fun eachEvent_lowersMoodBy3() {
        // 급출발 → (보통) → 급가속 → (보통) → 급감속. 같은 계열이 바로 이어지면 1회로 세므로 사이에 보통 구간을 둔다
        val trip = TripInput(samples(0f, 12f, 14f, 30f, 32f, 10f), distanceKm = 2.0)
        val score = DrivingScorer.score(trip, currentMood = 50)
        assertEquals(3, score.events.size) // 급출발, 급가속, 급감속
        assertEquals(-9, score.moodChange)
        assertEquals(41, score.newMood)
    }

    @Test
    fun tripUnder1Km_getsNoBonus() {
        val score = DrivingScorer.score(TripInput(samples(0f, 5f, 0f), distanceKm = 0.9), currentMood = 50)
        assertEquals(0, score.moodChange)
        assertEquals(50, score.newMood)
    }

    @Test
    fun tripOfExactly1Km_getsBonus() {
        assertEquals(5, DrivingScorer.score(TripInput(samples(0f, 5f, 0f), distanceKm = 1.0)).moodChange)
    }

    @Test
    fun mood_isClampedTo0And100() {
        val smooth = TripInput(samples(0f, 5f), distanceKm = 5.0)
        assertEquals(100, DrivingScorer.score(smooth, currentMood = 98).newMood)

        val rough = TripInput(samples(0f, 12f), distanceKm = 5.0)
        assertEquals(0, DrivingScorer.score(rough, currentMood = 2).newMood)
    }

    @Test
    fun startMood_is50() {
        assertEquals(55, DrivingScorer.score(TripInput(samples(0f, 5f), distanceKm = 1.0)).newMood)
    }

    @Test
    fun fuelMultiplier_isTripOverRated() {
        assertEquals(1.25, DrivingScorer.fuelMultiplier(15.0, 12.0), 1e-9)
    }

    @Test
    fun fuelMultiplier_isLimitedTo0_5And2_0() {
        assertEquals(2.0, DrivingScorer.fuelMultiplier(30.0, 10.0), 1e-9)
        assertEquals(0.5, DrivingScorer.fuelMultiplier(4.0, 10.0), 1e-9)
    }

    @Test
    fun unknownOrZeroRatedEfficiency_isMultiplier1() {
        assertEquals(1.0, DrivingScorer.fuelMultiplier(15.0, 0.0), 1e-9)
        assertEquals(1.0, DrivingScorer.fuelMultiplier(15.0, -3.0), 1e-9)
        assertEquals(1.0, DrivingScorer.fuelMultiplier(null, 12.0), 1e-9)
        assertEquals(1.0, DrivingScorer.fuelMultiplier(15.0, null), 1e-9)
    }

    @Test
    fun experience_isDistanceTimesMultiplier() {
        val trip = TripInput(samples(0f, 5f), distanceKm = 10.0, tripEfficiency = 15.0, ratedEfficiency = 12.0)
        assertEquals(12.5, DrivingScorer.score(trip).experience, 1e-9)

        val unknown = TripInput(samples(0f, 5f), distanceKm = 10.0)
        assertEquals(10.0, DrivingScorer.score(unknown).experience, 1e-9)
    }
}
