package com.naechamon.data.scenario

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState
import com.naechamon.data.model.VehicleWarning

// 시나리오에서 "N초 시점의 차 상태"를 계산한다. 시간 흐름과 분리해 두어 테스트하기 쉽게 한다.
class ScenarioPlayer(private val scenario: Scenario) {

    private val frames = scenario.keyframes
    private val speedFrames = frames.filter { it.speedKmh != null }

    fun stateAt(timeSec: Double): VehicleState {
        val t = timeSec.coerceIn(0.0, scenario.durationSec)
        val passed = frames.takeWhile { it.atSec <= t }

        val gear = passed.lastNotNullOf(Keyframe::gear) ?: Gear.UNKNOWN
        val warnings = passed.lastNotNullOf(Keyframe::warnings) ?: emptySet<VehicleWarning>()
        val speedKmh = speedAt(t)
        val uxRestricted = passed.lastNotNullOf(Keyframe::uxRestricted)
            ?: !(gear == Gear.PARK && speedKmh < STOPPED_SPEED_KMH)

        return VehicleState(
            gear = gear,
            speedKmh = speedKmh,
            isUxRestricted = uxRestricted,
            warnings = warnings,
        )
    }

    private fun speedAt(t: Double): Float {
        val prev = speedFrames.last { it.atSec <= t }
        val next = speedFrames.firstOrNull { it.atSec > t } ?: return prev.speedKmh!!
        val ratio = ((t - prev.atSec) / (next.atSec - prev.atSec)).toFloat()
        return prev.speedKmh!! + (next.speedKmh!! - prev.speedKmh) * ratio
    }

    private fun <T> List<Keyframe>.lastNotNullOf(selector: (Keyframe) -> T?): T? =
        asReversed().firstNotNullOfOrNull(selector)

    companion object {
        // 이 속도 미만이면 정지로 본다
        const val STOPPED_SPEED_KMH = 0.5f
    }
}
