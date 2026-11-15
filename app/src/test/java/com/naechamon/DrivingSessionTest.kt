package com.naechamon

import com.naechamon.data.VehicleDataSource
import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import com.naechamon.domain.Consumable
import com.naechamon.domain.DisplayMode
import java.time.LocalDate
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSessionTest {

    // 시계를 손으로 돌리는 가짜 데이터 소스: 상태를 보낼 때마다 시각을 1초 넘긴다
    private class FakeSource(private val states: List<VehicleState>, private val clock: LongArray) : VehicleDataSource {
        override val vehicleState: Flow<VehicleState> = flow {
            states.forEach {
                clock[0] += 1_000
                emit(it)
            }
        }
    }

    private val today = LocalDate.of(2026, 11, 2)

    private fun drive(speed: Float) = VehicleState(Gear.DRIVE, speed, isUxRestricted = true)

    @Test(timeout = 10_000)
    fun `화면이 보고 있지 않아도 주행 기록이 쌓인다`() = runBlocking {
        val clock = longArrayOf(0)
        // 시속 72km로 10초 = 200m
        val states = List(11) { drive(72f) }
        val session = DrivingSession(FakeSource(states, clock), playbackSpeed = 1f, elapsedMillis = { clock[0] }, today = { today })

        // screens()는 부르지 않는다 = 화면이 내비에 가려진 상태
        session.record(this).join()

        assertEquals(0.2, session.summary().distanceKm, 0.001)
    }

    @Test(timeout = 10_000)
    fun `급정거도 화면 없이 센다`() = runBlocking {
        val clock = longArrayOf(0)
        // 1초 만에 시속 60에서 20으로 = 급감속
        val states = listOf(drive(60f), drive(60f), drive(20f), drive(20f))
        val session = DrivingSession(FakeSource(states, clock), playbackSpeed = 1f, elapsedMillis = { clock[0] }, today = { today })

        session.record(this).join()

        assertEquals(1, session.summary().events.size)
    }

    @Test(timeout = 10_000)
    fun `화면을 다시 열면 그동안의 기록을 이어서 보여 준다`() = runBlocking {
        val clock = longArrayOf(0)
        val parked = VehicleState(Gear.PARK, 0f, isUxRestricted = false)
        val states = List(11) { drive(72f) } + parked
        val session = DrivingSession(FakeSource(states, clock), playbackSpeed = 1f, elapsedMillis = { clock[0] }, today = { today })
        session.record(this).join()

        // 기록이 끝난 뒤에 화면이 처음 붙는다 (내비를 끄고 에토스를 연 상황)
        val screen = session.screens(this).first { it.mode == DisplayMode.Parked }

        // 200m + 마지막 1초 감속(평균 시속 36km) 10m
        assertEquals(0.21, screen.todayDistanceKm, 0.001)
        coroutineContext.cancelChildren()
    }

    @Test(timeout = 10_000)
    fun `정비 완료를 누르면 그 소모품 기록이 새로 시작된다`() = runBlocking {
        val clock = longArrayOf(0)
        val gate = MutableStateFlow(VehicleState(Gear.PARK, 0f, isUxRestricted = false))
        val source = object : VehicleDataSource { override val vehicleState: Flow<VehicleState> = gate }
        val session = DrivingSession(source, playbackSpeed = 1f, elapsedMillis = { clock[0] }, today = { today })
        val recording = session.record(this)
        val screens = session.screens(this)
        val before = screens.first { it.mode == DisplayMode.Parked }.report.consumables.first { it.consumable == Consumable.ENGINE_OIL }

        session.markServiced(Consumable.ENGINE_OIL, screens.value.report.odometerKm)
        val after = screens.first { s -> s.report.consumables.first { it.consumable == Consumable.ENGINE_OIL } != before }
            .report.consumables.first { it.consumable == Consumable.ENGINE_OIL }

        assertTrue(after.remainingKm!! > before.remainingKm!!)
        recording.cancel()
        coroutineContext.cancelChildren()
    }
}
