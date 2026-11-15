package com.naechamon.data

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosMappingTest {

    @Test
    fun gearNamesMapToOurGear() {
        assertEquals(Gear.PARK, PleosMapping.gearFromSdkName("GEAR_PARK"))
        assertEquals(Gear.REVERSE, PleosMapping.gearFromSdkName("GEAR_REVERSE"))
        assertEquals(Gear.NEUTRAL, PleosMapping.gearFromSdkName("GEAR_NEUTRAL"))
        assertEquals(Gear.DRIVE, PleosMapping.gearFromSdkName("GEAR_DRIVE"))
    }

    @Test
    fun manualGearsCountAsDrive() {
        (1..9).forEach { assertEquals(Gear.DRIVE, PleosMapping.gearFromSdkName("GEAR_$it")) }
    }

    @Test
    fun unknownGearStaysUnknown() {
        assertEquals(Gear.UNKNOWN, PleosMapping.gearFromSdkName("GEAR_UNKNOWN"))
        assertEquals(Gear.UNKNOWN, PleosMapping.gearFromSdkName(null))
        assertEquals(Gear.UNKNOWN, PleosMapping.gearFromSdkName("GEAR_10"))
        assertEquals(Gear.UNKNOWN, PleosMapping.gearFromSdkName(""))
    }

    @Test
    fun onlyReadableAccessTypesAreSupported() {
        assertTrue(PleosMapping.isReadable("READ"))
        assertTrue(PleosMapping.isReadable("READ_WRITE"))
        assertFalse(PleosMapping.isReadable("WRITE"))
        assertFalse(PleosMapping.isReadable("NONE"))
        assertFalse(PleosMapping.isReadable(null))
    }

    @Test
    fun speedConvertsMetersPerSecondToKmh() {
        assertEquals(36f, PleosMapping.speedKmhFromMetersPerSecond(10f), 0.001f)
        assertEquals(0f, PleosMapping.speedKmhFromMetersPerSecond(0f), 0.001f)
        // 후진은 음수로 올 수 있다
        assertEquals(18f, PleosMapping.speedKmhFromMetersPerSecond(-5f), 0.001f)
    }

    @Test
    fun unknownSpeedIsZero() {
        assertEquals(0f, PleosMapping.speedKmhFromMetersPerSecond(null), 0f)
        assertEquals(0f, PleosMapping.speedKmhFromMetersPerSecond(Float.NaN), 0f)
        assertEquals(0f, PleosMapping.speedKmhFromMetersPerSecond(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun unknownUxRestrictionMeansRestricted() {
        assertTrue(PleosMapping.isUxRestricted(null))
        assertTrue(PleosMapping.isUxRestricted(true))
        assertFalse(PleosMapping.isUxRestricted(false))
    }

    @Test
    fun odometerRejectsInvalidValues() {
        assertEquals(12345.6f, PleosMapping.odometerKm(12345.6f)!!, 0.001f)
        assertEquals(0f, PleosMapping.odometerKm(0f)!!, 0f)
        assertNull(PleosMapping.odometerKm(null))
        assertNull(PleosMapping.odometerKm(-1f))
        assertNull(PleosMapping.odometerKm(Float.NaN))
    }

    @Test
    fun initialStateIsSafeDefault() {
        val state = PleosUpdate.INITIAL_STATE
        assertEquals(Gear.UNKNOWN, state.gear)
        assertEquals(0f, state.speedKmh, 0f)
        assertTrue(state.isUxRestricted)
        assertTrue(state.warnings.isEmpty())
        assertNull(state.odometerKm)
    }

    @Test
    fun updatesChangeOnlyTheirOwnField() {
        val start = PleosUpdate.INITIAL_STATE
        val parked = PleosUpdate.GearChanged(Gear.PARK).applyTo(start)
        assertEquals(start.copy(gear = Gear.PARK), parked)

        val moving = PleosUpdate.SpeedChanged(50f).applyTo(parked)
        assertEquals(parked.copy(speedKmh = 50f), moving)

        val unrestricted = PleosUpdate.UxRestrictionChanged(false).applyTo(moving)
        assertEquals(moving.copy(isUxRestricted = false), unrestricted)

        val withOdometer = PleosUpdate.OdometerChanged(1000f).applyTo(unrestricted)
        assertEquals(unrestricted.copy(odometerKm = 1000f), withOdometer)
    }

    @Test
    fun carServiceLostFallsBackToRestrictedAndStopped() {
        val state = VehicleState(gear = Gear.PARK, speedKmh = 30f, isUxRestricted = false, odometerKm = 10f)
        val lost = PleosUpdate.CarServiceLost.applyTo(state)
        assertEquals(VehicleState(gear = Gear.PARK, speedKmh = 0f, isUxRestricted = true, odometerKm = 10f), lost)
    }
}
