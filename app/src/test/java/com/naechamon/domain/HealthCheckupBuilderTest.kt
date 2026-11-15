package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckupBuilderTest {

    private val month = YearMonth.of(2026, 10)
    private fun status(consumable: Consumable, level: ConditionLevel) =
        ConsumableStatus(consumable, level, remainingKm = null, remainingDays = null, interval = ServiceInterval(null, null))

    private fun build(warnings: Set<VehicleWarning> = emptySet(), consumables: List<ConsumableStatus> = emptyList()) =
        HealthCheckupBuilder.build(warnings, consumables, month)

    private fun HealthCheckup.levelOf(part: BodyPart) = items.first { it.part == part }.level

    @Test
    fun allClear_isNormalEverywhere_withBadge() {
        val checkup = build()
        assertEquals(BodyPart.entries.size, checkup.items.size)
        assertTrue(checkup.items.all { it.level == CheckupLevel.NORMAL })
        assertTrue(checkup.wellMaintained)
    }

    @Test
    fun warning_mapsToPart_asCheck() {
        val checkup = build(warnings = setOf(VehicleWarning.TPMS, VehicleWarning.LOW_VOLT_BATTERY))
        assertEquals(CheckupLevel.CHECK, checkup.levelOf(BodyPart.FEET))
        assertEquals(CheckupLevel.CHECK, checkup.levelOf(BodyPart.ENERGY))
        assertEquals(CheckupLevel.NORMAL, checkup.levelOf(BodyPart.HEART))
        assertFalse(checkup.wellMaintained)
    }

    @Test
    fun consumableNotice_isWatch_cautionIsCheck() {
        val checkup = build(consumables = listOf(status(Consumable.ENGINE_OIL, ConditionLevel.NOTICE), status(Consumable.WIPER, ConditionLevel.CAUTION)))
        assertEquals(CheckupLevel.WATCH, checkup.levelOf(BodyPart.STOMACH))
        assertEquals(CheckupLevel.CHECK, checkup.levelOf(BodyPart.EYES))
    }

    @Test
    fun worseLevelWins_onSamePart() {
        // 타이어 소모품 예고(관찰) + 공기압 경고등(점검) → 발은 점검 권장
        val checkup = build(warnings = setOf(VehicleWarning.TPMS), consumables = listOf(status(Consumable.TIRE, ConditionLevel.NOTICE)))
        assertEquals(CheckupLevel.CHECK, checkup.levelOf(BodyPart.FEET))
    }

    @Test
    fun badge_allowsOneWatch_only() {
        assertTrue(build(consumables = listOf(status(Consumable.ENGINE_OIL, ConditionLevel.NOTICE))).wellMaintained)
        val two = build(consumables = listOf(status(Consumable.ENGINE_OIL, ConditionLevel.NOTICE), status(Consumable.WIPER, ConditionLevel.NOTICE)))
        assertFalse(two.wellMaintained)
    }

    @Test
    fun pastWarning_isWatch_currentIsCheck() {
        val checkup = HealthCheckupBuilder.build(
            warnings = setOf(VehicleWarning.ENGINE),
            consumables = emptyList(),
            month = month,
            pastWarnings = setOf(VehicleWarning.TPMS, VehicleWarning.ENGINE),
        )
        assertEquals(CheckupLevel.WATCH, checkup.levelOf(BodyPart.FEET))
        assertEquals(CheckupLevel.CHECK, checkup.levelOf(BodyPart.HEART))
    }

    @Test
    fun mood_followsFlaggedParts() {
        assertEquals(CheckupMood.GOOD, build().mood)
        assertEquals(CheckupMood.HUNGRY, build(consumables = listOf(status(Consumable.ENGINE_OIL, ConditionLevel.CAUTION))).mood)
        assertEquals(CheckupMood.TIRED, build(consumables = listOf(status(Consumable.WIPER, ConditionLevel.NOTICE))).mood)
        assertEquals(CheckupMood.SICK, build(warnings = setOf(VehicleWarning.ABS)).mood)
        // 배 점검 권장 + 발 관찰 필요(지난 공기압 경고) → 여전히 배고픔 (데모 마지막 장면)
        val hungryWithWatch = HealthCheckupBuilder.build(emptySet(), listOf(status(Consumable.ENGINE_OIL, ConditionLevel.CAUTION)), month, pastWarnings = setOf(VehicleWarning.TPMS))
        assertEquals(CheckupMood.HUNGRY, hungryWithWatch.mood)
    }

    @Test
    fun consumableWithoutPart_isIgnored() {
        val checkup = build(consumables = listOf(status(Consumable.AC_FILTER, ConditionLevel.CAUTION)))
        assertTrue(checkup.items.all { it.level == CheckupLevel.NORMAL })
    }
}
