package com.naechamon.data.scenario

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleWarning
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// 가짜 시나리오 파일 형식. 장면(keyframe)마다 바뀌는 값만 적고, 적지 않은 값은 이전 장면 값을 이어 받는다.
@Serializable
data class Scenario(
    val name: String,
    val keyframes: List<Keyframe>,
) {
    val durationSec: Double get() = keyframes.last().atSec
}

@Serializable
data class Keyframe(
    val atSec: Double,
    val gear: Gear? = null,
    // 장면 사이의 속도는 직선으로 이어진다
    val speedKmh: Float? = null,
    // 생략하면 P단 + 정지일 때 해제, 나머지는 제한 중으로 계산한다
    val uxRestricted: Boolean? = null,
    // 켜진 경고등 전체. []이면 모두 꺼짐
    val warnings: Set<VehicleWarning>? = null,
)

object ScenarioParser {

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(text: String): Scenario {
        val scenario = try {
            json.decodeFromString<Scenario>(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("시나리오 형식 오류: ${e.message}", e)
        }
        validate(scenario)
        return scenario
    }

    private fun validate(scenario: Scenario) {
        val frames = scenario.keyframes
        require(frames.isNotEmpty()) { "${scenario.name}: 장면이 하나도 없음" }
        require(frames.first().atSec == 0.0) { "${scenario.name}: 첫 장면은 0초여야 함" }
        require(frames.first().gear != null && frames.first().speedKmh != null) {
            "${scenario.name}: 첫 장면에 gear와 speedKmh가 있어야 함"
        }
        frames.zipWithNext().forEach { (prev, next) ->
            require(next.atSec > prev.atSec) { "${scenario.name}: 장면 시간이 늘어나는 순서가 아님 (${prev.atSec} → ${next.atSec})" }
        }
        frames.forEach { frame ->
            require(frame.speedKmh == null || frame.speedKmh >= 0f) { "${scenario.name}: 속도는 0 이상이어야 함 (${frame.atSec}초)" }
        }
    }
}
