package com.naechamon.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// 끝난 주행 한 번의 기록. 차BTI·가혹 조건 판정의 재료
data class TripRecord(
    val startedAt: LocalDateTime,
    val distanceKm: Double,
    val durationMillis: Long,
    val hardEventCount: Int,
) {
    val averageSpeedKmh: Double get() = if (durationMillis <= 0) 0.0 else distanceKm / (durationMillis / 3_600_000.0)
    val isShort: Boolean get() = distanceKm < SevereConditionRules.SHORT_TRIP_MAX_KM
    val isSlow: Boolean get() = averageSpeedKmh < SevereConditionRules.SLOW_TRIP_MAX_AVG_KMH
    val isLong: Boolean get() = distanceKm >= SevereConditionRules.LONG_TRIP_MIN_KM
    val isWeekend: Boolean get() = startedAt.dayOfWeek == DayOfWeek.SATURDAY || startedAt.dayOfWeek == DayOfWeek.SUNDAY
    val isCommuteHour: Boolean get() = with(SevereConditionRules) {
        val h = startedAt.hour
        !isWeekend && (h in COMMUTE_MORNING_START_HOUR until COMMUTE_MORNING_END_HOUR || h in COMMUTE_EVENING_START_HOUR until COMMUTE_EVENING_END_HOUR)
    }
}

// 취급설명서 가혹 조건 중 주행 데이터로 판정하는 항목
enum class SevereCriterion { SHORT_TRIPS, STOP_AND_GO, HARD_EVENTS }

data class SevereConditionResult(
    // 데이터가 모자라면(트립 수·기간) false. 이때 matched는 비어 있고 isSevere는 false
    val enoughData: Boolean,
    val matched: Set<SevereCriterion>,
) {
    val isSevere: Boolean get() = enoughData && matched.size >= SevereConditionRules.CRITERIA_REQUIRED
}

object SevereConditionDecider {

    fun hasEnoughData(trips: List<TripRecord>): Boolean {
        if (trips.size < SevereConditionRules.MIN_TRIPS) return false
        // 기간은 달력 날짜로 센다: 첫 주행을 시작한 날부터 마지막 주행을 마친 날까지.
        // 시각까지 따져 24시간 단위로 세면, 자정 직후에 주행을 마쳤을 때 몇 분이 모자라 2주가 안 채워진다
        val firstDay = trips.minOf { it.startedAt }.toLocalDate()
        val lastDay = trips.maxOf { it.startedAt.plusNanos(it.durationMillis * 1_000_000) }.toLocalDate()
        return ChronoUnit.DAYS.between(firstDay, lastDay) >= SevereConditionRules.MIN_DAYS
    }

    fun decide(trips: List<TripRecord>): SevereConditionResult {
        if (!hasEnoughData(trips)) return SevereConditionResult(enoughData = false, matched = emptySet())
        val matched = buildSet {
            if (ratio(trips) { it.isShort } >= SevereConditionRules.SHORT_TRIP_RATIO) add(SevereCriterion.SHORT_TRIPS)
            if (ratio(trips) { it.isSlow } >= SevereConditionRules.SLOW_TRIP_RATIO) add(SevereCriterion.STOP_AND_GO)
            val totalKm = trips.sumOf { it.distanceKm }
            val events = trips.sumOf { it.hardEventCount }
            if (totalKm > 0 && events / totalKm * 100 >= SevereConditionRules.HARD_EVENTS_PER_100KM) add(SevereCriterion.HARD_EVENTS)
        }
        return SevereConditionResult(enoughData = true, matched = matched)
    }

    internal fun ratio(trips: List<TripRecord>, predicate: (TripRecord) -> Boolean): Double =
        if (trips.isEmpty()) 0.0 else trips.count(predicate).toDouble() / trips.size
}

// 차BTI 유형 (데모용 4종, 기획안 v2 3-1). 유형 수 확정은 PT 단계
enum class CarBtiType {
    // 짧은 트립 반복
    SQUIRREL,
    // 평일 출퇴근 시간대, 저속
    TURTLE,
    // 긴 트립, 고속
    MIGRATORY_BIRD,
    // 주말 장거리
    WEEKEND_TRAVELER,
}

// 데이터가 모자라면 null(알 상태). 판정 순서: 주말 나들이 → 철새 → 거북(출퇴근 저속) → 다람쥐(기본)
object CarBtiDecider {

    fun decide(trips: List<TripRecord>): CarBtiType? {
        if (!SevereConditionDecider.hasEnoughData(trips)) return null
        val r = SevereConditionRules
        val weekendLongRatio = SevereConditionDecider.ratio(trips) { it.isWeekend && it.isLong }
        val weekdayLongRatio = SevereConditionDecider.ratio(trips.filter { !it.isWeekend }) { it.isLong }
        val longRatio = SevereConditionDecider.ratio(trips) { it.isLong }
        val highwayRatio = SevereConditionDecider.ratio(trips) { it.averageSpeedKmh >= r.HIGHWAY_MIN_AVG_KMH }
        val commuteSlowRatio = SevereConditionDecider.ratio(trips) { it.isCommuteHour && it.isSlow }
        return when {
            // 주말에만 장거리 (평일까지 장거리면 철새형)
            weekendLongRatio >= r.WEEKEND_LONG_TRIP_RATIO && weekdayLongRatio < r.LONG_TRIP_RATIO -> CarBtiType.WEEKEND_TRAVELER
            longRatio >= r.LONG_TRIP_RATIO && highwayRatio >= r.LONG_TRIP_RATIO -> CarBtiType.MIGRATORY_BIRD
            commuteSlowRatio >= r.COMMUTE_TRIP_RATIO -> CarBtiType.TURTLE
            else -> CarBtiType.SQUIRREL
        }
    }
}
