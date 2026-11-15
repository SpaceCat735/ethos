package com.naechamon.data

import com.naechamon.data.model.VehicleState
import com.naechamon.data.scenario.Scenario
import com.naechamon.data.scenario.ScenarioPlayer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// 가짜 시나리오 모드: 시나리오를 실제 시간에 맞춰 재생한다. playbackSpeed = 10이면 10배속.
// 시나리오가 끝나면 마지막 상태에서 멈춘다.
// 같은 상태도 거르지 않고 보낸다: 등속 구간도 시간이 흐른 만큼 거리를 쌓아야 한다 (#46).
// 화면은 StateFlow가 같은 값을 걸러 주므로 다시 그리지 않는다.
class ScenarioVehicleDataSource(
    scenario: Scenario,
    private val playbackSpeed: Float = 1f,
    // 테스트에서 시계와 기다리기를 바꿔 끼운다
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) : VehicleDataSource {

    private val player = ScenarioPlayer(scenario)
    private val durationSec = scenario.durationSec

    // 샘플 간격(실제 시간). 배속이 높아도 시나리오 시간으로 SAMPLE_SCENARIO 간격을 지켜
    // 2초짜리 급정거가 샘플 사이에 묻히지 않게 한다 (#47). 5배속 이하는 예전처럼 TICK
    private val tick: Duration = minOf(TICK, SAMPLE_SCENARIO / playbackSpeed.coerceAtLeast(1f).toDouble())

    override val vehicleState: Flow<VehicleState> = flow {
        val start = timeSource.markNow()
        while (true) {
            val elapsedSec = start.elapsedNow().inWholeMilliseconds / 1000.0 * playbackSpeed
            emit(player.stateAt(elapsedSec))
            if (elapsedSec >= durationSec) break
            wait(tick)
        }
    }

    private companion object {
        val TICK = 100.milliseconds
        val SAMPLE_SCENARIO = 500.milliseconds
    }
}
