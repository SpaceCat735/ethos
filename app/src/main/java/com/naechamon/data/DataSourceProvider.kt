package com.naechamon.data

import android.content.Context
import android.util.Log
import com.naechamon.data.scenario.ScenarioParser

// 어떤 데이터 소스를 쓸지 정하는 유일한 곳. 다른 층은 VehicleDataSource만 받는다.
// 기본은 시나리오 모드(데모 영상·심사용). 실행 옵션 --es source pleos 일 때만 실제 차량(PleosVehicleDataSource).
object DataSourceProvider {

    private const val TAG = "DataSourceProvider"
    // 아무 인자 없이 켜면 데모 영상용 시나리오
    const val DEFAULT_SCENARIO = "demo"
    // 실행 옵션 source 값: 실제 차량 데이터
    const val SOURCE_PLEOS = "pleos"

    fun isPleos(source: String?): Boolean = source == SOURCE_PLEOS

    // 실차 모드에서 실행 중 요청할 퍼미션 (CAR_SPEED는 dangerous)
    val pleosRuntimePermissions: Array<String> get() = PleosVehicleDataSource.RUNTIME_PERMISSIONS

    fun pleos(context: Context): VehicleDataSource = PleosVehicleDataSource(context.applicationContext)

    fun scenario(context: Context, name: String?, playbackSpeed: Float): VehicleDataSource {
        val requested = name ?: DEFAULT_SCENARIO
        val text = readScenario(context, requested) ?: run {
            // 데모 중에 앱이 꺼지지 않도록 기본 시나리오로 대신한다
            Log.w(TAG, "시나리오 '$requested'을 찾지 못해 '$DEFAULT_SCENARIO'로 재생합니다")
            readScenario(context, DEFAULT_SCENARIO) ?: error("기본 시나리오가 없음")
        }
        // 시나리오 재생 중이어도 실제 차가 주행 제한 중이면 주행 화면만 보이게 덮는다
        return UxGuardedDataSource(
            ScenarioVehicleDataSource(ScenarioParser.parse(text), playbackSpeed),
            RealUxRestriction.flow(context),
        )
    }

    private fun readScenario(context: Context, name: String): String? = runCatching {
        context.assets.open("scenarios/$name.json").bufferedReader().use { it.readText() }
    }.getOrNull()
}
