package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterMarkDeciderTest {

    @Test
    fun nothingWrong_noMarks() {
        assertEquals(emptySet<CharacterMark>(), CharacterMarkDecider.decide(emptySet(), ConditionLevel.NORMAL))
    }

    @Test
    fun unknownTireLevel_noMarks() {
        assertEquals(emptySet<CharacterMark>(), CharacterMarkDecider.decide(emptySet(), null))
    }

    @Test
    fun coolantWarning_isSweat() {
        assertEquals(setOf(CharacterMark.SWEAT), CharacterMarkDecider.decide(setOf(VehicleWarning.ENGINE_TEMPERATURE), null))
    }

    @Test
    fun tpmsWarning_isFootCast() {
        assertEquals(setOf(CharacterMark.FOOT_CAST), CharacterMarkDecider.decide(setOf(VehicleWarning.TPMS), null))
    }

    @Test
    fun tireCaution_isWornShoes_butNoticeIsNot() {
        assertEquals(setOf(CharacterMark.WORN_SHOES), CharacterMarkDecider.decide(emptySet(), ConditionLevel.CAUTION))
        assertEquals(emptySet<CharacterMark>(), CharacterMarkDecider.decide(emptySet(), ConditionLevel.NOTICE))
    }

    @Test
    fun otherWarnings_noMarks() {
        assertEquals(emptySet<CharacterMark>(), CharacterMarkDecider.decide(setOf(VehicleWarning.ENGINE, VehicleWarning.LOW_VOLT_BATTERY), null))
    }

    @Test
    fun severalAtOnce_allMarks() {
        assertEquals(
            setOf(CharacterMark.SWEAT, CharacterMark.FOOT_CAST, CharacterMark.WORN_SHOES),
            CharacterMarkDecider.decide(setOf(VehicleWarning.ENGINE_TEMPERATURE, VehicleWarning.TPMS), ConditionLevel.CAUTION),
        )
    }
}
