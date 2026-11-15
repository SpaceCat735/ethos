package com.naechamon.data

import com.naechamon.DrivingSession
import com.naechamon.data.model.Gear
import com.naechamon.data.scenario.Keyframe
import com.naechamon.data.scenario.Scenario
import com.naechamon.data.scenario.ScenarioParser
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 실제 시나리오 데이터 소스 → DrivingSession 경로를 가짜 시계로 끝까지 재생한다 (#46·#47)
class ScenarioSamplingTest {

    private val today = LocalDate.of(2026, 11, 4)

    private fun load(name: String) = ScenarioParser.parse(File("src/main/assets/scenarios/$name.json").readText())

    // jitterEvery > 0이면 그 횟수마다 기다리기가 2ms 늦어진다 (기기에서 실행이 밀리는 상황)
    private fun replay(scenario: Scenario, speed: Float, jitterEvery: Int = 0): DrivingSession = runBlocking {
        val clock = TestTimeSource()
        val origin = clock.markNow()
        var waits = 0
        val wait: suspend (Duration) -> Unit = { d ->
            waits++
            clock += if (jitterEvery > 0 && waits % jitterEvery == 0) d + 2.milliseconds else d
        }
        val source = ScenarioVehicleDataSource(scenario, speed, clock, wait)
        val session = DrivingSession(source, speed, elapsedMillis = { origin.elapsedNow().inWholeMilliseconds }, today = { today })
        session.record(this).join()
        session
    }

    @Test
    fun constantSpeed_accumulatesDistance() {
        // #46: 0초부터 72km/h 등속, 마지막 키프레임도 72km/h → 1초에 0.02km
        val scenario = Scenario("flat", listOf(Keyframe(0.0, Gear.DRIVE, 72f), Keyframe(1.0, speedKmh = 72f)))
        assertEquals(0.02, replay(scenario, 1f).summary().distanceKm, 0.001)
    }

    @Test
    fun longConstantSection_isCountedToTheEnd() {
        // 10분 동안 60km/h = 10km
        val scenario = Scenario("long", listOf(Keyframe(0.0, Gear.DRIVE, 60f), Keyframe(600.0, speedKmh = 60f)))
        assertEquals(10.0, replay(scenario, 5f).summary().distanceKm, 0.01)
    }

    @Test
    fun commute_hardBrakesKeptAtEverySpeed() {
        // #47: 배속만 바꿨을 때 급정거 수가 달라지면 안 된다
        val commute = load("commute")
        for (speed in listOf(1f, 5f, 10f, 60f)) {
            val events = replay(commute, speed).summary().events
            assertEquals("${speed}배속", 3, events.size)
            assertTrue("${speed}배속", events.all { it.type.isBraking })
        }
    }

    @Test
    fun commute_noFalseEventsWithTimingJitter() {
        val commute = load("commute")
        val events = replay(commute, 60f, jitterEvery = 3).summary().events
        assertEquals(3, events.size)
    }

    @Test
    fun demo_hardBrakesAndWarningKeptAtEverySpeed() {
        val demo = load("demo")
        for (speed in listOf(1f, 5f, 10f, 60f)) {
            assertEquals("${speed}배속", 3, replay(demo, speed).summary().events.size)
            val clock = TestTimeSource()
            val states = runBlocking { ScenarioVehicleDataSource(demo, speed, clock, { clock += it }).vehicleState.toList() }
            assertTrue("${speed}배속 경고 전환", states.any { it.warnings.isNotEmpty() })
            assertEquals("${speed}배속 끝은 주차", Gear.PARK, states.last().gear)
        }
    }
}
