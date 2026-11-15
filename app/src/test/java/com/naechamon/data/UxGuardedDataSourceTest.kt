package com.naechamon.data

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import com.naechamon.domain.DisplayMode
import com.naechamon.domain.DisplayModeDecider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 시나리오가 주차 구간이어도 실제 차가 주행 제한 중이면 주행 화면이 되는지
class UxGuardedDataSourceTest {

    private val parkedInScenario = VehicleState(gear = Gear.PARK, speedKmh = 0f, isUxRestricted = false)

    private fun source(real: Flow<Boolean>) = UxGuardedDataSource(
        object : VehicleDataSource {
            override val vehicleState = flowOf(parkedInScenario)
        },
        real,
    )

    @Test
    fun realRestricted_forcesDriving_evenIfScenarioIsParked() = runBlocking {
        val state = source(flowOf(true)).vehicleState.last()
        assertTrue(state.isUxRestricted)
        assertEquals(DisplayMode.Driving, DisplayModeDecider.decide(state))
    }

    @Test
    fun realNotRestricted_keepsScenarioValue() = runBlocking {
        val state = source(flowOf(false)).vehicleState.last()
        assertFalse(state.isUxRestricted)
        assertEquals(DisplayMode.Parked, DisplayModeDecider.decide(state))
    }

    @Test
    fun noCarService_keepsScenarioValue() = runBlocking {
        // 차량 서비스가 없어 아무 값도 오지 않는 기기에서도 시나리오가 재생된다
        val state = source(emptyFlow()).vehicleState.last()
        assertEquals(parkedInScenario, state)
    }
}
