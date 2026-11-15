package com.naechamon.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkedReportBuilderTest {

    private val today = LocalDate.of(2026, 11, 1)

    private val profile = VehicleProfile(
        odometerKm = 42_300,
        serviceRecords = mapOf(
            // 엔진오일 10,000km 주기: 33,200에서 교체 → 42,300 + 오늘 거리에서 남은 km 계산
            Consumable.ENGINE_OIL to ServiceRecord(33_200, LocalDate.of(2026, 5, 2)),
            // 공조 필터 12개월 주기: 2025-11-10 → 2026-11-10 만기, 오늘은 1개월 안 → 예고
            Consumable.AC_FILTER to ServiceRecord(28_000, LocalDate.of(2025, 11, 10)),
        ),
        ratedEfficiency = 12.5,
        totalExperience = 310.0,
    )

    private fun event(type: DrivingEventType, sec: Long) = DrivingEvent(type, sec * 1000)

    private fun build(events: List<DrivingEvent> = emptyList(), distanceKm: Double = 12.0, efficiency: Double? = null) =
        ParkedReportBuilder.build(TripSummary(distanceKm, events, mood = 55, tripEfficiency = efficiency), profile, today)

    @Test
    fun noEvents_isGreatPraise() {
        val report = build()
        assertEquals(PraiseLevel.GREAT, report.praise)
        assertEquals(0, report.eventCount)
    }

    @Test
    fun todayPraise_isNullBeforeTrip_andFollowsPraiseAfter() {
        assertNull(build(distanceKm = 0.0).todayPraise)
        assertEquals(PraiseLevel.GREAT, build().todayPraise)
        val rough = build(listOf(event(DrivingEventType.HARD_STOP, 60), event(DrivingEventType.HARD_ACCELERATION, 120)))
        assertEquals(PraiseLevel.ROUGH, rough.todayPraise)
    }

    @Test
    fun oneHardStop_isStillGreat_twoIsRough() {
        assertEquals(PraiseLevel.GREAT, build(listOf(event(DrivingEventType.HARD_STOP, 60))).praise)
        val rough = build(listOf(event(DrivingEventType.HARD_STOP, 60), event(DrivingEventType.HARD_ACCELERATION, 120)))
        assertEquals(PraiseLevel.ROUGH, rough.praise)
        assertEquals(1, rough.hardBrakeCount)
        assertEquals(1, rough.hardAccelCount)
    }

    @Test
    fun decelAndStop_bothCountAsBrake() {
        val report = build(listOf(event(DrivingEventType.HARD_DECELERATION, 10), event(DrivingEventType.HARD_STOP, 20)))
        assertEquals(2, report.hardBrakeCount)
        assertEquals(0, report.hardAccelCount)
    }

    @Test
    fun experience_isDistanceTimesFuelMultiplier() {
        // 연비를 모르면 배수 1.0
        assertEquals(12.0, build().experience, 1e-9)
        assertEquals(322.0, build().totalExperience, 1e-9)
        // 공인 12.5에 실제 15.0이면 배수 1.2
        assertEquals(14.4, build(efficiency = 15.0).experience, 1e-9)
    }

    @Test
    fun consumables_useOdometerPlusTodayDistance() {
        val report = build(distanceKm = 12.0)
        assertEquals(42_312, report.odometerKm)
        val oil = report.consumables.first { it.consumable == Consumable.ENGINE_OIL }
        // 10,000 - (42,312 - 33,200) = 888km 남음 → 예고
        assertEquals(888, oil.remainingKm)
        assertEquals(ConditionLevel.NOTICE, oil.level)
        val filter = report.consumables.first { it.consumable == Consumable.AC_FILTER }
        assertEquals(ConditionLevel.NOTICE, filter.level)
        assertEquals(9L, filter.remainingDays) // 11-01 → 11-10
    }

    @Test
    fun worstConsumable_isMostUrgent() {
        val report = build()
        assertEquals(Consumable.ENGINE_OIL, report.worstConsumable?.consumable)
        // 기록 없는 소모품은 목록에 안 들어간다
        assertNull(report.consumables.firstOrNull { it.consumable == Consumable.WIPER })
    }

    @Test
    fun noTripHistory_isEggAndNormalInterval() {
        val report = build()
        assertNull(report.carBti)
        assertEquals(false, report.severe.enoughData)
        assertEquals(10_000, report.consumables.first { it.consumable == Consumable.ENGINE_OIL }.interval.km)
    }

    @Test
    fun severeHistory_usesShortIntervalAndHatches() {
        // 3주간 낮 시간 짧은 저속 트립 → 가혹 조건, 동네 다람쥐형 (출퇴근 시간대면 거북형)
        val history = (0 until 21).map { d ->
            TripRecord(java.time.LocalDateTime.of(2026, 10, 5, 14, 0).plusDays(d.toLong()), 3.0, 12 * 60_000L, 0)
        }
        val report = ParkedReportBuilder.build(TripSummary(12.0, emptyList(), 55), profile.copy(tripHistory = history), today)
        assertEquals(true, report.severe.isSevere)
        assertEquals(CarBtiType.SQUIRREL, report.carBti)
        val oil = report.consumables.first { it.consumable == Consumable.ENGINE_OIL }
        assertEquals(7_500, oil.interval.km)
        // 7,500 - (42,312 - 33,200) < 0 → 주의
        assertEquals(ConditionLevel.CAUTION, oil.level)
    }

    @Test
    fun allNormal_hasNoWorst() {
        val fresh = profile.copy(serviceRecords = mapOf(Consumable.ENGINE_OIL to ServiceRecord(42_000, today)))
        val report = ParkedReportBuilder.build(TripSummary(5.0, emptyList(), 50), fresh, today)
        assertNull(report.worstConsumable)
    }
}
