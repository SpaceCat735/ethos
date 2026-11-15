package com.naechamon.data.scenario

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioPlayerTest {

    private fun player(json: String) = ScenarioPlayer(ScenarioParser.parse(json))

    private val basic = player(
        """
        {
          "name": "테스트",
          "keyframes": [
            { "atSec": 0, "gear": "PARK", "speedKmh": 0 },
            { "atSec": 10, "gear": "DRIVE", "speedKmh": 0 },
            { "atSec": 20, "speedKmh": 50 },
            { "atSec": 30, "warnings": ["TPMS"] },
            { "atSec": 40, "speedKmh": 0, "warnings": [] },
            { "atSec": 50, "gear": "PARK" }
          ]
        }
        """,
    )

    @Test
    fun omittedValues_carryOver() {
        // 30초 장면은 경고등만 바꿨으므로 기어는 10초의 DRIVE를 이어 받는다
        assertEquals(Gear.DRIVE, basic.stateAt(35.0).gear)
    }

    @Test
    fun speed_isInterpolatedBetweenFrames() {
        assertEquals(0f, basic.stateAt(0.0).speedKmh, 0.01f)
        assertEquals(0f, basic.stateAt(10.0).speedKmh, 0.01f)
        assertEquals(25f, basic.stateAt(15.0).speedKmh, 0.01f)
        assertEquals(50f, basic.stateAt(20.0).speedKmh, 0.01f)
        assertEquals(25f, basic.stateAt(30.0).speedKmh, 0.01f)
    }

    @Test
    fun warnings_turnOnAndOff() {
        assertTrue(basic.stateAt(29.9).warnings.isEmpty())
        assertEquals(setOf(VehicleWarning.TPMS), basic.stateAt(30.0).warnings)
        assertTrue(basic.stateAt(40.0).warnings.isEmpty())
    }

    @Test
    fun uxRestriction_isDerivedFromGearAndSpeed() {
        assertFalse("P단 + 정지 → 제한 해제", basic.stateAt(5.0).isUxRestricted)
        assertTrue("주행 중 → 제한", basic.stateAt(15.0).isUxRestricted)
        assertFalse("다시 P단 + 정지 → 해제", basic.stateAt(50.0).isUxRestricted)
    }

    @Test
    fun scenarioFiles_doNotMoveInPark() {
        // 샘플 시나리오에서 P단인데 움직이는 순간이 없어야 한다 (출발 장면에 speedKmh 0을 빠뜨리면 생기는 실수)
        java.io.File("src/main/assets/scenarios").listFiles { f -> f.extension == "json" }.orEmpty().forEach { file ->
            val scenario = ScenarioParser.parse(file.readText())
            val p = ScenarioPlayer(scenario)
            (0..(scenario.durationSec * 2).toInt()).map { p.stateAt(it / 2.0) }.forEach { state ->
                assertFalse("${file.name}: P단에서 ${state.speedKmh}km/h", state.gear == Gear.PARK && state.speedKmh >= ScenarioPlayer.STOPPED_SPEED_KMH)
            }
        }
    }

    @Test
    fun uxRestriction_parkButMoving_isRestricted() {
        val p = player(
            """{ "name": "t", "keyframes": [
                 { "atSec": 0, "gear": "PARK", "speedKmh": 10 },
                 { "atSec": 10, "speedKmh": 0 } ] }""",
        )
        assertTrue(p.stateAt(0.0).isUxRestricted)
        assertFalse(p.stateAt(10.0).isUxRestricted)
    }

    @Test
    fun uxRestriction_explicitValueWins() {
        val p = player(
            """{ "name": "t", "keyframes": [
                 { "atSec": 0, "gear": "PARK", "speedKmh": 0, "uxRestricted": true },
                 { "atSec": 10 } ] }""",
        )
        assertTrue(p.stateAt(5.0).isUxRestricted)
    }

    @Test
    fun outsideTimeline_isClamped() {
        assertEquals(basic.stateAt(0.0), basic.stateAt(-5.0))
        assertEquals(basic.stateAt(50.0), basic.stateAt(999.0))
        assertEquals(Gear.PARK, basic.stateAt(999.0).gear)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyKeyframes_isRejected() {
        ScenarioParser.parse("""{ "name": "t", "keyframes": [] }""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun firstFrameWithoutGear_isRejected() {
        ScenarioParser.parse("""{ "name": "t", "keyframes": [ { "atSec": 0, "speedKmh": 0 } ] }""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsortedFrames_isRejected() {
        ScenarioParser.parse(
            """{ "name": "t", "keyframes": [
                 { "atSec": 0, "gear": "PARK", "speedKmh": 0 },
                 { "atSec": 20 }, { "atSec": 10 } ] }""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownWarning_isRejected() {
        ScenarioParser.parse(
            """{ "name": "t", "keyframes": [
                 { "atSec": 0, "gear": "PARK", "speedKmh": 0, "warnings": ["NOT_A_WARNING"] } ] }""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun misspelledKey_isRejected() {
        // 오타 난 키를 조용히 무시하면 장면이 의도와 다르게 재생되므로 오류로 처리한다
        ScenarioParser.parse("""{ "name": "t", "keyframes": [ { "atSec": 0, "gear": "PARK", "speedKmh": 0, "speed": 3 } ] }""")
    }
}
