package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning

// 긴급 상황으로 보는 경고등 (안전 규칙: 엔진·제동·조향·에어백 이상, 공기압 급감, 냉각수 과열)
object SafetyRules {
    val emergencyWarnings: Map<VehicleWarning, EmergencyType> = mapOf(
        VehicleWarning.ENGINE to EmergencyType.ENGINE,
        VehicleWarning.ABS to EmergencyType.BRAKE,
        VehicleWarning.ELECTRONIC_PARKING_BRAKE to EmergencyType.BRAKE,
        VehicleWarning.MDPS to EmergencyType.STEERING,
        VehicleWarning.AIRBAG to EmergencyType.AIRBAG,
        VehicleWarning.ENGINE_TEMPERATURE to EmergencyType.COOLANT_OVERHEAT,
        // SDK는 공기압 급감 여부를 따로 주지 않으므로 경고등이 켜지면 긴급으로 본다
        VehicleWarning.TPMS to EmergencyType.TIRE_PRESSURE,
    )
}
