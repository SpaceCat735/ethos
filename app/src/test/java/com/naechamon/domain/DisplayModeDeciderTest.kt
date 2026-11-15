package com.naechamon.domain

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import com.naechamon.data.model.VehicleWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayModeDeciderTest {

    private fun state(
        gear: Gear = Gear.PARK,
        isUxRestricted: Boolean = false,
        warnings: Set<VehicleWarning> = emptySet(),
    ) = VehicleState(gear = gear, speedKmh = 0f, isUxRestricted = isUxRestricted, warnings = warnings)

    @Test
    fun parkAndNotRestricted_isParked() {
        assertEquals(DisplayMode.Parked, DisplayModeDecider.decide(state()))
    }

    @Test
    fun parkButRestricted_isDriving() {
        assertEquals(DisplayMode.Driving, DisplayModeDecider.decide(state(isUxRestricted = true)))
    }

    @Test
    fun notParkGears_areDriving() {
        listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN).forEach { gear ->
            assertEquals("기어 $gear", DisplayMode.Driving, DisplayModeDecider.decide(state(gear = gear)))
        }
    }

    @Test
    fun defaultUxRestriction_isDriving() {
        // 조작 제한 값을 모르면(기본값) 주차로 보지 않는다
        assertEquals(DisplayMode.Driving, DisplayModeDecider.decide(VehicleState(gear = Gear.PARK, speedKmh = 0f)))
    }

    @Test
    fun emergencyWarning_whileParked_isEmergency() {
        val mode = DisplayModeDecider.decide(state(warnings = setOf(VehicleWarning.AIRBAG)))
        assertEquals(DisplayMode.Emergency(setOf(EmergencyType.AIRBAG)), mode)
    }

    @Test
    fun emergencyWarning_whileDriving_isEmergency() {
        val mode = DisplayModeDecider.decide(state(gear = Gear.DRIVE, isUxRestricted = true, warnings = setOf(VehicleWarning.TPMS)))
        assertEquals(DisplayMode.Emergency(setOf(EmergencyType.TIRE_PRESSURE)), mode)
    }

    @Test
    fun brakeWarnings_mergeIntoOneType() {
        val mode = DisplayModeDecider.decide(
            state(warnings = setOf(VehicleWarning.ABS, VehicleWarning.ELECTRONIC_PARKING_BRAKE, VehicleWarning.ENGINE)),
        )
        assertEquals(DisplayMode.Emergency(setOf(EmergencyType.ENGINE, EmergencyType.BRAKE)), mode)
    }

    @Test
    fun nonEmergencyWarning_keepsNormalMode() {
        val mode = DisplayModeDecider.decide(state(warnings = setOf(VehicleWarning.LOW_VOLT_BATTERY)))
        assertEquals(DisplayMode.Parked, mode)
    }

    @Test
    fun warningCleared_returnsToNormalMode() {
        val withWarning = state(warnings = setOf(VehicleWarning.ENGINE))
        assertEquals(DisplayMode.Emergency(setOf(EmergencyType.ENGINE)), DisplayModeDecider.decide(withWarning))
        assertEquals(DisplayMode.Parked, DisplayModeDecider.decide(withWarning.copy(warnings = emptySet())))
    }

    @Test
    fun everyEmergencyType_hasAWarning() {
        // 안전 규칙의 긴급 상황이 하나도 빠지지 않았는지
        assertEquals(EmergencyType.entries.toSet(), SafetyRules.emergencyWarnings.values.toSet())
    }
}
