package com.naechamon.domain

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState

// 우선순위: 긴급 > 주행 > 주차. 판단이 애매하면 주행으로 본다.
object DisplayModeDecider {

    fun decide(state: VehicleState): DisplayMode {
        val emergencies = state.warnings
            .mapNotNull { SafetyRules.emergencyWarnings[it] }
            .toSortedSet()
        if (emergencies.isNotEmpty()) {
            return DisplayMode.Emergency(emergencies)
        }

        // P단이어도 차가 조작을 제한 중이면 주행으로 본다
        val isParked = state.gear == Gear.PARK && !state.isUxRestricted
        return if (isParked) DisplayMode.Parked else DisplayMode.Driving
    }
}
