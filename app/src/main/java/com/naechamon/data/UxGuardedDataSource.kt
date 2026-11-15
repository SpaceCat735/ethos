package com.naechamon.data

import com.naechamon.data.model.VehicleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

// 시나리오 재생 중에도 실제 차의 주행 제한을 따르게 하는 덮개.
// 실제 차가 조작을 제한 중이면 시나리오가 주차 구간이어도 "제한 중"으로 바꿔 보낸다 (화면은 주행 화면이 된다).
// 실제 차가 제한 중이 아니거나 값을 모르면 시나리오 값을 그대로 둔다.
class UxGuardedDataSource(
    private val inner: VehicleDataSource,
    // true = 실제 차가 조작을 제한 중
    private val realUxRestricted: Flow<Boolean>,
) : VehicleDataSource {

    override val vehicleState: Flow<VehicleState> =
        // 차량 서비스 연결이 늦거나 없어도 시나리오가 바로 재생되게 false로 시작한다
        inner.vehicleState.combine(realUxRestricted.onStart { emit(false) }) { state, restricted ->
            if (restricted) state.copy(isUxRestricted = true) else state
        }
}
