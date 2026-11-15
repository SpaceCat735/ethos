package com.naechamon.data

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState

// 실차 값(Vehicle SDK·AAOS)을 우리 모델로 바꾸는 순수 Kotlin 함수 모음.
// SDK·안드로이드 클래스를 직접 받지 않고 이름(문자열)·숫자만 받아서 JVM 단위 테스트로 확인한다.
object PleosMapping {

    private const val MS_TO_KMH = 3.6f

    // Vehicle SDK VehicleGear 이름 → 우리 기어. 수동 단수(GEAR_1~9)는 전진 주행으로 본다
    fun gearFromSdkName(name: String?): Gear = when {
        name == null -> Gear.UNKNOWN
        name == "GEAR_PARK" -> Gear.PARK
        name == "GEAR_REVERSE" -> Gear.REVERSE
        name == "GEAR_NEUTRAL" -> Gear.NEUTRAL
        name == "GEAR_DRIVE" -> Gear.DRIVE
        name.matches(Regex("GEAR_[1-9]")) -> Gear.DRIVE
        else -> Gear.UNKNOWN
    }

    // Vehicle SDK AccessType 이름 → 읽을 수 있는지. NONE·WRITE·모르는 값이면 지원하지 않는 것으로 본다
    fun isReadable(accessTypeName: String?): Boolean = accessTypeName == "READ" || accessTypeName == "READ_WRITE"

    // AAOS PERF_VEHICLE_SPEED(m/s) → km/h. 후진이면 음수로 올 수 있어 크기만 쓴다. 값을 모르면 0
    fun speedKmhFromMetersPerSecond(metersPerSecond: Float?): Float {
        if (metersPerSecond == null || metersPerSecond.isNaN() || metersPerSecond.isInfinite()) return 0f
        return kotlin.math.abs(metersPerSecond) * MS_TO_KMH
    }

    // AAOS CarUxRestrictions.isRequiresDistractionOptimization → 조작 제한 여부. 모르면 제한 중으로 본다
    fun isUxRestricted(requiresDistractionOptimization: Boolean?): Boolean = requiresDistractionOptimization ?: true

    // Odometer(PERF_ODOMETER, km) 값 검사. 음수·NaN 같은 이상한 값은 버린다
    fun odometerKm(value: Float?): Float? =
        value?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0f }
}

// 실차 콜백 하나가 바꾸는 값. 콜백은 이것만 채널로 보내고, 상태 합치기는 한 곳(applyTo)에서 한다
sealed class PleosUpdate {
    abstract fun applyTo(state: VehicleState): VehicleState

    data class GearChanged(val gear: Gear) : PleosUpdate() {
        override fun applyTo(state: VehicleState) = state.copy(gear = gear)
    }

    data class SpeedChanged(val speedKmh: Float) : PleosUpdate() {
        override fun applyTo(state: VehicleState) = state.copy(speedKmh = speedKmh)
    }

    data class UxRestrictionChanged(val isUxRestricted: Boolean) : PleosUpdate() {
        override fun applyTo(state: VehicleState) = state.copy(isUxRestricted = isUxRestricted)
    }

    data class OdometerChanged(val odometerKm: Float?) : PleosUpdate() {
        override fun applyTo(state: VehicleState) = state.copy(odometerKm = odometerKm)
    }

    // 차 서비스 연결이 끊김: 속도는 모름(0), 조작 제한은 모름(제한 중)으로 되돌린다
    data object CarServiceLost : PleosUpdate() {
        override fun applyTo(state: VehicleState) = state.copy(speedKmh = 0f, isUxRestricted = true)
    }

    companion object {
        // 실차 값을 하나도 모를 때의 상태. 기어 모름 + 조작 제한 중 → 화면은 주행 중으로 판단(안전 쪽)
        val INITIAL_STATE = VehicleState(gear = Gear.UNKNOWN, speedKmh = 0f, isUxRestricted = true)
    }
}
