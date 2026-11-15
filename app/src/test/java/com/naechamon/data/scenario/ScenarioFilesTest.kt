package com.naechamon.data.scenario

import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.ConditionLevel
import com.naechamon.domain.DisplayMode
import com.naechamon.domain.ExpressionDecider
import com.naechamon.domain.LiveDrivingTracker
import com.naechamon.domain.DisplayModeDecider
import com.naechamon.domain.EmergencyType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 앱에 들어가는 샘플 시나리오가 의도한 장면을 실제로 만드는지 확인한다
class ScenarioFilesTest {

    private val dir = File("src/main/assets/scenarios")

    private fun load(name: String) = ScenarioParser.parse(File(dir, "$name.json").readText())

    // 0.5초 간격으로 재생하며 화면 모드가 바뀌는 순서를 모은다
    private fun modeSequence(scenario: Scenario): List<DisplayMode> {
        val player = ScenarioPlayer(scenario)
        val steps = (scenario.durationSec * 2).toInt()
        return (0..steps)
            .map { DisplayModeDecider.decide(player.stateAt(it / 2.0)) }
            .fold(mutableListOf()) { acc, mode -> if (acc.lastOrNull() != mode) acc.add(mode); acc }
    }

    @Test
    fun allScenarioFiles_parse() {
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertEquals(setOf("commute", "tire_warning", "long_drive", "demo", "traffic_jam", "parked"), files.map { it.nameWithoutExtension }.toSet())
        files.forEach { ScenarioParser.parse(it.readText()) }
    }

    // 내 차 꾸미기 확인용: 끝까지 정차만 (주행으로 바뀌면 꾸미기 화면이 닫힌다)
    @Test
    fun parked_staysParked() {
        assertEquals(listOf(DisplayMode.Parked), modeSequence(load("parked")))
    }

    @Test
    fun commute_drivesThenParks_withThreeHardBrakes() {
        val scenario = load("commute")
        assertEquals(listOf(DisplayMode.Parked, DisplayMode.Driving, DisplayMode.Parked), modeSequence(scenario))

        assertEquals(3, countHardBrakes(scenario))
    }

    // 급정거: 2초 안에 30km/h 이상 감속
    private fun countHardBrakes(scenario: Scenario): Int {
        val player = ScenarioPlayer(scenario)
        var hardBrakes = 0
        var t = 0.0
        while (t + 2 <= scenario.durationSec) {
            if (player.stateAt(t).speedKmh - player.stateAt(t + 2).speedKmh >= 30f) {
                hardBrakes++
                t += 2
            } else {
                t += 0.5
            }
        }
        return hardBrakes
    }

    @Test
    fun demo_coversStoryboardScenes2to7() {
        // 콘티: 주차(시동) → 주행(급정거 3회) → 주차 리포트 → 주행 → 긴급(공기압) → 정차 후 해제 → 주차(건강검진)
        val scenario = load("demo")
        val emergency = DisplayMode.Emergency(setOf(EmergencyType.TIRE_PRESSURE))
        assertEquals(
            listOf(DisplayMode.Parked, DisplayMode.Driving, DisplayMode.Parked, DisplayMode.Driving, emergency, DisplayMode.Parked),
            modeSequence(scenario),
        )
        assertEquals(3, countHardBrakes(scenario))
        // 첫 주차 리포트 구간이 10배속에서도 6초는 보이게 (60초 이상)
        val player = ScenarioPlayer(scenario)
        val parkedStart = (0..(scenario.durationSec).toInt()).first { DisplayModeDecider.decide(player.stateAt(it.toDouble())) == DisplayMode.Parked && it > 100 }
        val parkedEnd = (parkedStart..(scenario.durationSec).toInt()).first { DisplayModeDecider.decide(player.stateAt(it.toDouble())) != DisplayMode.Parked }
        assertTrue("첫 주차 구간 ${parkedEnd - parkedStart}초", parkedEnd - parkedStart >= 60)
    }

    @Test
    fun tireWarning_goesEmergencyThenRecovers() {
        val emergency = DisplayMode.Emergency(setOf(EmergencyType.TIRE_PRESSURE))
        assertEquals(
            listOf(DisplayMode.Parked, DisplayMode.Driving, emergency, DisplayMode.Parked),
            modeSequence(load("tire_warning")),
        )
    }

    @Test
    fun longDrive_endsInCoolantEmergency() {
        val sequence = modeSequence(load("long_drive"))
        assertTrue(sequence.contains(DisplayMode.Driving))
        assertEquals(DisplayMode.Emergency(setOf(EmergencyType.COOLANT_OVERHEAT)), sequence.last())
    }

    @Test
    fun trafficJam_showsBoredThenProud_evenWhenHungry() {
        val scenario = load("traffic_jam")
        assertEquals(listOf(DisplayMode.Parked, DisplayMode.Driving, DisplayMode.Parked), modeSequence(scenario))
        assertEquals(0, countHardBrakes(scenario))

        // 0.5초 간격으로 재생하며 주행 중 표정이 바뀌는 순서를 모은다 (엔진오일 '주의' = 배고픈 상태로)
        val player = ScenarioPlayer(scenario)
        val tracker = LiveDrivingTracker()
        val expressions = (0..(scenario.durationSec * 2).toInt()).map { step ->
            val now = step * 500L
            val state = player.stateAt(step / 2.0)
            tracker.update(state, now)
            ExpressionDecider.decide(
                warnings = state.warnings,
                continuousDrivingMillis = tracker.continuousDrivingMillis(now),
                consumableLevels = listOf(ConditionLevel.CAUTION),
                lastEventTimeMillis = tracker.lastEventTimeMillis,
                nowMillis = now,
                mood = tracker.mood,
                slowDrivingMillis = tracker.slowDrivingMillis(now),
            )
        }.fold(mutableListOf<CharacterExpression>()) { acc, e -> if (acc.lastOrNull() != e) acc.add(e); acc }

        assertEquals(
            listOf(CharacterExpression.HUNGRY, CharacterExpression.BORED, CharacterExpression.HUNGRY, CharacterExpression.PROUD, CharacterExpression.HUNGRY),
            expressions,
        )
    }
}
