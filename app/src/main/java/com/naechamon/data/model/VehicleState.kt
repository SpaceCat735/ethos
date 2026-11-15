package com.naechamon.data.model

// 차량 상태 한 시점의 값. 항목은 #1(SDK 데이터 범위 확인) 결과에 맞춰 늘린다.
data class VehicleState(
    val gear: Gear,
    val speedKmh: Float,
    // 차가 화면 조작을 제한 중인지 (AAOS CarUxRestrictions). 값을 모르면 제한 중으로 본다.
    val isUxRestricted: Boolean = true,
    // 지금 켜진 계기판 경고등
    val warnings: Set<VehicleWarning> = emptySet(),
    // 실차 누적 주행거리(km, Vehicle SDK Odometer). 시나리오 모드·모르는 경우 null
    val odometerKm: Float? = null,
)

enum class Gear {
    PARK,
    REVERSE,
    NEUTRAL,
    DRIVE,
    UNKNOWN,
}
