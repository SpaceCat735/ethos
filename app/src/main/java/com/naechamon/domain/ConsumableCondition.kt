package com.naechamon.domain

import java.time.LocalDate

enum class Consumable { ENGINE_OIL, TIRE, BRAKE_PAD, AC_FILTER, WIPER }

// 정상 → 예고(교체 직전) → 주의(교체 시기 도달). 선언 순서가 곧 심각도 순서
enum class ConditionLevel { NORMAL, NOTICE, CAUTION }

// 마지막 정비 시점의 누적 주행거리와 날짜
data class ServiceRecord(val odometerKm: Int, val date: LocalDate)

// 거리 기준과 기간 기준 중 먼저 도래하는 쪽(더 나쁜 단계)을 따른다
object ConsumableCondition {

    fun evaluate(
        consumable: Consumable,
        record: ServiceRecord,
        currentOdometerKm: Int,
        today: LocalDate,
        severe: Boolean = false,
    ): ConditionLevel {
        val interval = MaintenanceRules.intervalOf(consumable, severe)
        val byKm = interval.km?.let { levelByKm(it, record, currentOdometerKm) } ?: ConditionLevel.NORMAL
        val byMonths = interval.months?.let { levelByMonths(it, record, today) } ?: ConditionLevel.NORMAL
        return maxOf(byKm, byMonths)
    }

    // 단계와 함께 남은 거리·기간도 돌려준다 (리포트 상세용)
    fun status(
        consumable: Consumable,
        record: ServiceRecord,
        currentOdometerKm: Int,
        today: LocalDate,
        severe: Boolean = false,
    ): ConsumableStatus {
        val interval = MaintenanceRules.intervalOf(consumable, severe)
        val remainingKm = interval.km?.let { it - (currentOdometerKm - record.odometerKm).coerceAtLeast(0) }
        val remainingDays = interval.months?.let {
            val dueDate = record.date.plusMonths(it.toLong())
            java.time.temporal.ChronoUnit.DAYS.between(maxOf(today, record.date), dueDate)
        }
        return ConsumableStatus(
            consumable = consumable,
            level = evaluate(consumable, record, currentOdometerKm, today, severe),
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            interval = interval,
        )
    }

    // 정비 완료 입력 시 지금 시점으로 기록을 새로 시작한다
    fun reset(currentOdometerKm: Int, today: LocalDate): ServiceRecord =
        ServiceRecord(odometerKm = currentOdometerKm, date = today)

    private fun levelByKm(intervalKm: Int, record: ServiceRecord, currentOdometerKm: Int): ConditionLevel {
        // 주행거리가 기록보다 작으면(잘못된 입력) 경과 0으로 본다
        val drivenKm = (currentOdometerKm - record.odometerKm).coerceAtLeast(0)
        val remainingKm = intervalKm - drivenKm
        return when {
            remainingKm <= 0 -> ConditionLevel.CAUTION
            remainingKm <= MaintenanceRules.NOTICE_MARGIN_KM -> ConditionLevel.NOTICE
            else -> ConditionLevel.NORMAL
        }
    }

    private fun levelByMonths(intervalMonths: Int, record: ServiceRecord, today: LocalDate): ConditionLevel {
        // 오늘이 정비일보다 앞이면(잘못된 입력) 경과 0으로 본다
        val effectiveToday = maxOf(today, record.date)
        val dueDate = record.date.plusMonths(intervalMonths.toLong())
        return when {
            !effectiveToday.isBefore(dueDate) -> ConditionLevel.CAUTION
            !effectiveToday.isBefore(dueDate.minusMonths(MaintenanceRules.NOTICE_MARGIN_MONTHS)) -> ConditionLevel.NOTICE
            else -> ConditionLevel.NORMAL
        }
    }
}
