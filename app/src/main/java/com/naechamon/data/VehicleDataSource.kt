package com.naechamon.data

import com.naechamon.data.model.VehicleState
import kotlinx.coroutines.flow.Flow

// 차량 데이터를 받는 통로. 다른 층은 이 인터페이스만 사용한다.
// 구현체: PleosVehicleDataSource(실제 SDK), ScenarioVehicleDataSource(가짜 시나리오, #4)
interface VehicleDataSource {
    val vehicleState: Flow<VehicleState>
}
