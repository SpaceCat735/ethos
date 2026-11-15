package com.naechamon.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumableConditionTest {

    // 엔진오일 기준: 10,000km 또는 12개월 → 교체 예정 40,000km, 2027-01-15
    private val baseKm = 30_000
    private val baseDate = LocalDate.of(2026, 1, 15)
    private val record = ServiceRecord(odometerKm = baseKm, date = baseDate)

    private fun oil(drivenKm: Int, today: LocalDate) =
        ConsumableCondition.evaluate(Consumable.ENGINE_OIL, record, baseKm + drivenKm, today)

    @Test
    fun withinInterval_isNormal() {
        assertEquals(ConditionLevel.NORMAL, oil(drivenKm = 5_000, today = baseDate.plusMonths(3)))
    }

    @Test
    fun nearKmLimit_isNotice() {
        assertEquals(ConditionLevel.NOTICE, oil(drivenKm = 9_500, today = baseDate.plusMonths(1)))
    }

    @Test
    fun nearDueDate_isNotice() {
        assertEquals(ConditionLevel.NOTICE, oil(drivenKm = 100, today = LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun kmLimitPassed_isCaution() {
        assertEquals(ConditionLevel.CAUTION, oil(drivenKm = 10_500, today = baseDate.plusMonths(1)))
    }

    @Test
    fun dueDatePassed_isCaution() {
        assertEquals(ConditionLevel.CAUTION, oil(drivenKm = 100, today = LocalDate.of(2027, 2, 1)))
    }

    @Test
    fun kmBoundaries() {
        val today = baseDate.plusMonths(1)
        assertEquals("1,001km 남음", ConditionLevel.NORMAL, oil(drivenKm = 8_999, today = today))
        assertEquals("정확히 1,000km 남음", ConditionLevel.NOTICE, oil(drivenKm = 9_000, today = today))
        assertEquals("1km 남음", ConditionLevel.NOTICE, oil(drivenKm = 9_999, today = today))
        assertEquals("정확히 주기 도달", ConditionLevel.CAUTION, oil(drivenKm = 10_000, today = today))
    }

    @Test
    fun dateBoundaries() {
        assertEquals("예고 하루 전", ConditionLevel.NORMAL, oil(drivenKm = 100, today = LocalDate.of(2026, 12, 14)))
        assertEquals("정확히 1개월 전", ConditionLevel.NOTICE, oil(drivenKm = 100, today = LocalDate.of(2026, 12, 15)))
        assertEquals("교체 예정일 하루 전", ConditionLevel.NOTICE, oil(drivenKm = 100, today = LocalDate.of(2027, 1, 14)))
        assertEquals("정확히 교체 예정일", ConditionLevel.CAUTION, oil(drivenKm = 100, today = LocalDate.of(2027, 1, 15)))
    }

    @Test
    fun worseAxisWins() {
        // 거리는 예고, 기간은 주의 → 주의
        assertEquals(ConditionLevel.CAUTION, oil(drivenKm = 9_500, today = LocalDate.of(2027, 2, 1)))
        // 거리는 주의, 기간은 정상 → 주의
        assertEquals(ConditionLevel.CAUTION, oil(drivenKm = 12_000, today = baseDate.plusMonths(1)))
    }

    @Test
    fun missingKmAxis_isIgnored() {
        // 와이퍼는 기간 주기만 있으므로 아무리 달려도 거리로는 바뀌지 않는다
        val level = ConsumableCondition.evaluate(Consumable.WIPER, record, baseKm + 1_000_000, baseDate.plusMonths(1))
        assertEquals(ConditionLevel.NORMAL, level)
    }

    @Test
    fun missingMonthsAxis_isIgnored() {
        // 타이어·브레이크 패드는 거리 주기만 있으므로 오래 지나도 기간으로는 바뀌지 않는다
        listOf(Consumable.TIRE, Consumable.BRAKE_PAD).forEach { consumable ->
            val level = ConsumableCondition.evaluate(consumable, record, baseKm + 100, baseDate.plusYears(10))
            assertEquals("소모품 $consumable", ConditionLevel.NORMAL, level)
        }
    }

    @Test
    fun serviceDone_returnsToNormal() {
        val currentKm = baseKm + 60_000
        val today = baseDate.plusYears(2)
        Consumable.entries.forEach { consumable ->
            assertEquals("정비 전 $consumable", ConditionLevel.CAUTION, ConsumableCondition.evaluate(consumable, record, currentKm, today))
            val newRecord = ConsumableCondition.reset(currentKm, today)
            assertEquals(ServiceRecord(currentKm, today), newRecord)
            assertEquals("정비 후 $consumable", ConditionLevel.NORMAL, ConsumableCondition.evaluate(consumable, newRecord, currentKm, today))
        }
    }

    @Test
    fun negativeElapsed_isTreatedAsZero() {
        assertEquals("주행거리가 기록보다 작음", ConditionLevel.NORMAL, oil(drivenKm = -5_000, today = baseDate))
        assertEquals("오늘이 정비일보다 앞", ConditionLevel.NORMAL, oil(drivenKm = 0, today = baseDate.minusMonths(3)))
    }

    @Test
    fun everyConsumable_hasAtLeastOneAxis() {
        Consumable.entries.forEach { consumable ->
            val interval = MaintenanceRules.intervalOf(consumable)
            assertTrue("소모품 $consumable", interval.km != null || interval.months != null)
        }
    }

    @Test
    fun onlyEngineOilAndAcFilter_areFromManual() {
        // 타이어·브레이크 패드·와이퍼 주기는 앱 기본값이라 "취급설명서 기준"으로 보여 주면 안 된다
        val fromManual = Consumable.entries.filter { MaintenanceRules.isFromManual(it) }.toSet()
        assertEquals(setOf(Consumable.ENGINE_OIL, Consumable.AC_FILTER), fromManual)
    }
}
