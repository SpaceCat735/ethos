package com.naechamon.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SevereConditionDeciderTest {

    private val base = LocalDateTime.of(2026, 10, 5, 8, 0) // 월요일 08:00

    // 하루에 하나씩, 평일 아침 출발 트립을 days일 만든다
    private fun trips(days: Int, distanceKm: Double, avgKmh: Double, hardEvents: Int = 0, hour: Int = 8, weekendOnly: Boolean = false): List<TripRecord> =
        (0 until days).map { d ->
            var start = base.plusDays(d.toLong()).withHour(hour)
            if (weekendOnly) {
                while (start.dayOfWeek.value < 6) start = start.plusDays(1)
            }
            TripRecord(start, distanceKm, (distanceKm / avgKmh * 3_600_000).toLong(), hardEvents)
        }

    @Test
    fun tooFewTrips_isNotEnoughData() {
        val result = SevereConditionDecider.decide(trips(days = 5, distanceKm = 3.0, avgKmh = 15.0))
        assertFalse(result.enoughData)
        assertFalse(result.isSevere)
        assertNull(CarBtiDecider.decide(trips(days = 5, distanceKm = 3.0, avgKmh = 15.0)))
    }

    @Test
    fun tooShortPeriod_isNotEnoughData() {
        // 12개 트립이지만 하루 안에 몰려 있음
        val sameDay = (0 until 12).map { TripRecord(base.plusHours(it.toLong()), 3.0, 600_000, 0) }
        assertFalse(SevereConditionDecider.hasEnoughData(sameDay))
    }

    @Test
    fun shortAndSlowTrips_isSevere() {
        // 3km, 평균 15km/h → 짧은 거리 + 정체 두 항목
        val result = SevereConditionDecider.decide(trips(days = 20, distanceKm = 3.0, avgKmh = 15.0))
        assertTrue(result.enoughData)
        assertEquals(setOf(SevereCriterion.SHORT_TRIPS, SevereCriterion.STOP_AND_GO), result.matched)
        assertTrue(result.isSevere)
    }

    @Test
    fun onlyOneCriterion_isNotSevere() {
        // 짧지만 빠름 → 한 항목만
        val result = SevereConditionDecider.decide(trips(days = 20, distanceKm = 5.0, avgKmh = 40.0))
        assertEquals(setOf(SevereCriterion.SHORT_TRIPS), result.matched)
        assertFalse(result.isSevere)
    }

    @Test
    fun hardEvents_countPer100km() {
        // 20km × 20회 = 400km, 트립당 급가감속 3회 = 60회 → 15회/100km
        val result = SevereConditionDecider.decide(trips(days = 20, distanceKm = 20.0, avgKmh = 50.0, hardEvents = 3))
        assertTrue(SevereCriterion.HARD_EVENTS in result.matched)
    }

    @Test
    fun longHighwayTrips_isNotSevere() {
        val result = SevereConditionDecider.decide(trips(days = 20, distanceKm = 80.0, avgKmh = 90.0))
        assertTrue(result.matched.isEmpty())
        assertFalse(result.isSevere)
    }

    @Test
    fun carBti_squirrel_forShortTrips() {
        assertEquals(CarBtiType.SQUIRREL, CarBtiDecider.decide(trips(days = 20, distanceKm = 3.0, avgKmh = 30.0, hour = 14)))
    }

    @Test
    fun carBti_turtle_forSlowCommute() {
        assertEquals(CarBtiType.TURTLE, CarBtiDecider.decide(trips(days = 20, distanceKm = 12.0, avgKmh = 15.0, hour = 8)))
    }

    @Test
    fun carBti_migratoryBird_forLongHighway() {
        assertEquals(CarBtiType.MIGRATORY_BIRD, CarBtiDecider.decide(trips(days = 20, distanceKm = 80.0, avgKmh = 90.0, hour = 10)))
    }

    @Test
    fun carBti_weekendTraveler_forWeekendLongTrips() {
        // 평일 짧은 트립 16개 + 주말 장거리 6개 (주말 장거리 비율 27%)
        val weekday = trips(days = 16, distanceKm = 4.0, avgKmh = 25.0, hour = 14)
        val weekend = (0 until 6).map { TripRecord(base.plusDays(5 + it * 7L), 120.0, 5_400_000, 0) }
        assertEquals(CarBtiType.WEEKEND_TRAVELER, CarBtiDecider.decide(weekday + weekend))
    }

    @Test
    fun severe_usesShorterEngineOilInterval() {
        assertEquals(7_500, MaintenanceRules.intervalOf(Consumable.ENGINE_OIL, severe = true).km)
        assertEquals(10_000, MaintenanceRules.intervalOf(Consumable.ENGINE_OIL, severe = false).km)
        // 가혹 조건 주기가 확인되지 않은 항목은 일반 주기 그대로
        assertEquals(MaintenanceRules.intervalOf(Consumable.TIRE), MaintenanceRules.intervalOf(Consumable.TIRE, severe = true))
    }
}
