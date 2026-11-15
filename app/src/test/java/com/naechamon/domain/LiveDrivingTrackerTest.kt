package com.naechamon.domain

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveDrivingTrackerTest {

    private fun drive(speed: Float) = VehicleState(Gear.DRIVE, speed)

    // 속도 목록을 1초 간격으로 넣고 감지된 사건 종류를 돌려준다
    private fun LiveDrivingTracker.feed(vararg speeds: Float, startMillis: Long = 0L) =
        speeds.mapIndexed { i, speed -> update(drive(speed), startMillis + i * 1000L) }.mapNotNull { it?.type }

    @Test
    fun startsAtMoodStart() {
        val tracker = LiveDrivingTracker()
        assertEquals(DrivingRules.MOOD_START, tracker.mood)
        assertNull(tracker.lastEventTimeMillis)
    }

    @Test
    fun events_areKeptForReport() {
        val tracker = LiveDrivingTracker()
        val types = tracker.feed(0f, 12f, 30f, 32f, 10f, 20f, 2f)
        assertEquals(types, tracker.events.map { it.type })
        assertEquals(types, tracker.summary().events.map { it.type })
        assertEquals(tracker.mood, tracker.summary().mood)
    }

    @Test
    fun distance_isIntegratedFromSpeed() {
        // 36km/h로 10초 달리면 0.1km. 첫 샘플은 기준점이라 거리에 안 들어간다
        val tracker = LiveDrivingTracker()
        tracker.update(drive(36f), 0L)
        tracker.update(drive(36f), 10_000L)
        assertEquals(0.1, tracker.distanceKm, 1e-6)
        // 정지 상태에서는 거리가 늘지 않는다
        tracker.update(VehicleState(Gear.PARK, 0f), 20_000L)
        tracker.update(VehicleState(Gear.PARK, 0f), 30_000L)
        assertEquals(0.15, tracker.distanceKm, 1e-6)
    }

    @Test
    fun smoothDriving_hasNoEvent() {
        val tracker = LiveDrivingTracker()
        assertEquals(emptyList<DrivingEventType>(), tracker.feed(0f, 5f, 10f, 15f, 10f, 5f))
        assertEquals(50, tracker.mood)
    }

    @Test
    fun sameRulesAsDrivingScorer() {
        val speeds = floatArrayOf(0f, 12f, 30f, 32f, 10f, 20f, 2f)
        val expected = DrivingScorer.detectEvents(speeds.mapIndexed { i, s -> SpeedSample(i * 1000L, s) }).map { it.type }
        assertEquals(expected, LiveDrivingTracker().feed(*speeds))
    }

    @Test
    fun eachEvent_lowersMoodBy3() {
        val tracker = LiveDrivingTracker()
        tracker.feed(0f, 12f, 14f, 30f, 32f, 10f) // 급출발, (보통), 급가속, (보통), 급감속
        assertEquals(41, tracker.mood)
        assertEquals(5_000L, tracker.lastEventTimeMillis)
    }

    @Test
    fun continuousSameEvent_countsOnce() {
        val tracker = LiveDrivingTracker()
        assertEquals(listOf(DrivingEventType.HARD_ACCELERATION), tracker.feed(20f, 30f, 40f, 50f))
        assertEquals(47, tracker.mood)
    }

    @Test
    fun mood_isClampedAt0() {
        val tracker = LiveDrivingTracker(initialMood = 2)
        tracker.feed(0f, 12f)
        assertEquals(0, tracker.mood)
    }

    @Test
    fun repeatedOrOlderTime_isIgnored() {
        val tracker = LiveDrivingTracker()
        tracker.update(drive(10f), 1_000L)
        assertNull(tracker.update(drive(60f), 1_000L))
        assertNull(tracker.update(drive(60f), 500L))
        assertEquals(50, tracker.mood)
    }

    @Test
    fun continuousDriving_growsWhileDriving() {
        val tracker = LiveDrivingTracker()
        tracker.update(drive(0f), 1_000L)
        tracker.update(drive(50f), 60_000L)
        assertEquals(0L, tracker.continuousDrivingMillis(1_000L))
        // 상태가 안 바뀌어도 지금 시각 기준으로 늘어난다
        assertEquals(2 * 60 * 60 * 1000L, tracker.continuousDrivingMillis(1_000L + 2 * 60 * 60 * 1000L))
    }

    @Test
    fun stopInDrive_keepsCounting() {
        // 신호 대기(D단 정지)는 연속 운전으로 본다
        val tracker = LiveDrivingTracker()
        tracker.update(drive(30f), 0L)
        tracker.update(drive(0f), 30_000L)
        assertEquals(60_000L, tracker.continuousDrivingMillis(60_000L))
    }

    @Test
    fun parkAndStop_resetsContinuousDriving() {
        val tracker = LiveDrivingTracker()
        tracker.update(drive(30f), 0L)
        tracker.update(VehicleState(Gear.PARK, 0f), 3_600_000L)
        assertEquals(0L, tracker.continuousDrivingMillis(3_700_000L))

        // 다시 출발하면 0부터
        tracker.update(drive(0f), 4_000_000L)
        assertEquals(10_000L, tracker.continuousDrivingMillis(4_010_000L))
    }

    @Test
    fun parkWhileMoving_doesNotReset() {
        // P단 값이 들어와도 정지 전이면 초기화하지 않는다
        val tracker = LiveDrivingTracker()
        tracker.update(drive(30f), 0L)
        tracker.update(VehicleState(Gear.PARK, 3f), 10_000L)
        assertEquals(20_000L, tracker.continuousDrivingMillis(20_000L))
    }

    @Test
    fun slowDriving_countsOnlyWhileUnderJamSpeed() {
        val tracker = LiveDrivingTracker()
        tracker.update(drive(15f), 0L)
        tracker.update(drive(10f), 60_000L)
        assertEquals(120_000L, tracker.slowDrivingMillis(120_000L))
        // 속도가 오르면 처음부터
        tracker.update(drive(30f), 130_000L)
        assertEquals(0L, tracker.slowDrivingMillis(140_000L))
        tracker.update(drive(12f), 150_000L)
        assertEquals(10_000L, tracker.slowDrivingMillis(160_000L))
        // 주차하면 초기화
        tracker.update(VehicleState(Gear.PARK, 0f), 170_000L)
        assertEquals(0L, tracker.slowDrivingMillis(200_000L))
    }

    // 1초 간격: 0→36km/h 10초 부드럽게 가속, 36km/h 유지, 0까지 10초 감속, P단 정지. 끝난 시각(ms)을 돌려준다
    private fun LiveDrivingTracker.smoothTrip(startMillis: Long, cruiseSec: Int): Long {
        var t = startMillis
        val speeds = (0..10).map { it * 3.6f } + List(cruiseSec) { 36f } + (10 downTo 0).map { it * 3.6f }
        speeds.forEach { update(drive(it), t); t += 1000L }
        update(VehicleState(Gear.PARK, 0f), t)
        return t
    }

    @Test
    fun smoothTripOver1km_getsBonusOnPark() {
        // #49: 위험운전 없이 1km 이상 달리고 주차하면 기분 +5
        val tracker = LiveDrivingTracker()
        tracker.smoothTrip(0L, cruiseSec = 110)
        assertEquals(0, tracker.events.size)
        assertEquals(DrivingRules.MOOD_START + DrivingRules.SMOOTH_TRIP_MOOD_BONUS, tracker.mood)
    }

    @Test
    fun shortTrip_getsNoBonus() {
        val tracker = LiveDrivingTracker()
        tracker.smoothTrip(0L, cruiseSec = 30) // 약 0.4km
        assertEquals(DrivingRules.MOOD_START, tracker.mood)
    }

    @Test
    fun tripWithEvent_getsNoBonus() {
        val tracker = LiveDrivingTracker()
        val t = tracker.smoothTrip(0L, cruiseSec = 110)
        // 두 번째 주행: 1km 넘게 달리다 급감속 1회
        val types = tracker.feed(*((0..10).map { it * 3.6f } + List(110) { 36f } + listOf(10f, 10f)).toFloatArray(), startMillis = t + 1000L)
        tracker.update(VehicleState(Gear.PARK, 0f), t + 200_000L)
        assertEquals(1, types.size)
        assertEquals(DrivingRules.MOOD_START + DrivingRules.SMOOTH_TRIP_MOOD_BONUS - DrivingRules.MOOD_PENALTY_PER_EVENT, tracker.mood)
    }

    @Test
    fun repeatedParkState_givesBonusOnce() {
        val tracker = LiveDrivingTracker()
        val t = tracker.smoothTrip(0L, cruiseSec = 110)
        repeat(5) { tracker.update(VehicleState(Gear.PARK, 0f), t + (it + 1) * 1000L) }
        assertEquals(DrivingRules.MOOD_START + DrivingRules.SMOOTH_TRIP_MOOD_BONUS, tracker.mood)
    }

    @Test
    fun nextTrip_isJudgedWithoutPreviousEvents() {
        val tracker = LiveDrivingTracker()
        // 첫 주행: 급감속이 있어 보너스 없음
        tracker.feed(0f, 3.6f, 7.2f, 10.8f, 14.4f, 18f, 21.6f, 25.2f, 28.8f, 32.4f, 36f, 10f, 10f)
        tracker.update(VehicleState(Gear.PARK, 0f), 20_000L)
        val afterFirst = tracker.mood
        // 두 번째 주행은 부드러우면 보너스
        tracker.smoothTrip(30_000L, cruiseSec = 110)
        assertEquals(afterFirst + DrivingRules.SMOOTH_TRIP_MOOD_BONUS, tracker.mood)
    }

    @Test
    fun bonus_isClampedAt100() {
        val tracker = LiveDrivingTracker(initialMood = 98)
        tracker.smoothTrip(0L, cruiseSec = 110)
        assertEquals(DrivingRules.MOOD_MAX, tracker.mood)
    }
}
