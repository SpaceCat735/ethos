package com.naechamon.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CareBookTest {

    private val today = LocalDate.of(2026, 11, 7)
    private fun stamps(n: Int) = List(n) { CareStamp(Consumable.ENGINE_OIL, today.minusDays(n.toLong() - it)) }

    @Test
    fun grade_boundaries() {
        assertEquals(CareGrade.NEW, CareGradeRules.gradeOf(0))
        assertEquals(CareGrade.STARTED, CareGradeRules.gradeOf(1))
        assertEquals(CareGrade.STARTED, CareGradeRules.gradeOf(2))
        assertEquals(CareGrade.REGULAR, CareGradeRules.gradeOf(3))
        assertEquals(CareGrade.REGULAR, CareGradeRules.gradeOf(5))
        assertEquals(CareGrade.THOROUGH, CareGradeRules.gradeOf(6))
        assertEquals(CareGrade.THOROUGH, CareGradeRules.gradeOf(40))
    }

    @Test
    fun stampsToNext_countsDownAndStopsAtTop() {
        assertEquals(1, CareGradeRules.stampsToNext(0))
        assertEquals(2, CareGradeRules.stampsToNext(1))
        assertEquals(1, CareGradeRules.stampsToNext(5))
        assertNull(CareGradeRules.stampsToNext(6))
    }

    @Test
    fun gradeAfterOneMore_onlyWhenGradeChanges() {
        assertEquals(CareGrade.REGULAR, CareBook(stamps(2)).gradeAfterOneMore())
        assertNull(CareBook(stamps(3)).gradeAfterOneMore())
        assertNull(CareBook(stamps(9)).gradeAfterOneMore())
    }

    @Test
    fun newestFirst_datedByDateThenUndatedLast() {
        val old = CareStamp(Consumable.WIPER, null)
        val a = CareStamp(Consumable.TIRE, today.minusDays(3))
        val b = CareStamp(Consumable.ENGINE_OIL, today)
        assertEquals(listOf(b, a, old), CareBook(listOf(old, a, b)).newestFirst)
    }

    @Test
    fun finishedTrip_addedOnlyWhenParkedAndLongEnough() {
        val trip = TripSummary(distanceKm = 5.0, events = emptyList(), mood = 0, drivingMillis = 10 * 60_000L)
        val at = LocalDateTime.of(today, LocalTime.of(15, 0))
        assertEquals(0, TripHistoryRules.withFinishedTrip(emptyList(), trip, null).size)
        assertEquals(0, TripHistoryRules.withFinishedTrip(emptyList(), trip.copy(distanceKm = 0.01), at).size)
        val added = TripHistoryRules.withFinishedTrip(emptyList(), trip, at).single()
        assertEquals(LocalDateTime.of(today, LocalTime.of(14, 50)), added.startedAt)
        assertEquals(5.0, added.distanceKm, 0.0)
    }

    @Test
    fun egg_hatchesWhenTodaysFinishedTripCompletesTwoWeeks() {
        // 14일 전 0시 30분부터 어제까지 하루 두 번 짧은 주행 (13일치) → 아직 알
        val history = buildList {
            var day = today.minusDays(14)
            while (day < today) {
                add(TripRecord(LocalDateTime.of(day, LocalTime.of(0, 30)).let { if (day == today.minusDays(14)) it else LocalDateTime.of(day, LocalTime.of(10, 30)) }, 4.2, 14 * 60_000L, 1))
                add(TripRecord(LocalDateTime.of(day, LocalTime.of(15, 0)), 5.1, 18 * 60_000L, 0))
                day = day.plusDays(1)
            }
        }
        val profile = VehicleProfile(odometerKm = 42_300, serviceRecords = emptyMap(), tripHistory = history)
        val trip = TripSummary(distanceKm = 4.5, events = emptyList(), mood = 0, drivingMillis = 12 * 60_000L)
        assertNull(ParkedReportBuilder.build(trip, profile, today).carBti)
        // 오늘 주행이 끝나면 2주가 채워져 부화
        assertNotNull(ParkedReportBuilder.build(trip, profile, today, LocalDateTime.of(today, LocalTime.of(9, 0))).carBti)
        // 자정 직후에 주행을 마쳐도 부화한다 (0시 5분: 첫 기록 0시 30분보다 이른 시각)
        assertNotNull(ParkedReportBuilder.build(trip, profile, today, LocalDateTime.of(today, LocalTime.of(0, 5))).carBti)
        // 어제 밤에 출발해 자정을 넘겨 마친 주행도 오늘 마친 주행으로 센다 (0시 1분 도착, 12분 주행)
        assertNotNull(ParkedReportBuilder.build(trip, profile, today, LocalDateTime.of(today, LocalTime.of(0, 1))).carBti)
        // 하루를 마칠 무렵에도 부화한다
        assertNotNull(ParkedReportBuilder.build(trip, profile, today, LocalDateTime.of(today, LocalTime.of(23, 59))).carBti)
    }
}
