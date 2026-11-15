package com.naechamon.data

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// 실제 차의 주행 제한 상태(AAOS CarUxRestrictions)만 읽는다. 퍼미션이 필요 없다.
// 시나리오 모드에서 실제로 달리는 중에 주차 화면·연출이 뜨지 않게 하는 데 쓴다 (UxGuardedDataSource).
// true = 제한 중. 차량 서비스를 못 쓰거나 연결이 끊기면 false(시나리오 값을 따름)를 보낸다.
internal object RealUxRestriction {

    private const val TAG = "RealUxRestriction"

    fun flow(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        var uxManager: CarUxRestrictionsManager? = null
        // createCar(…, lifecycleListener)는 Android 11(API 30)부터
        val car: Car? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            null
        } else {
            try {
                Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { readyCar, ready ->
                    if (!ready) {
                        uxManager = null
                        trySend(false)
                        return@createCar
                    }
                    runCatching {
                        val manager = readyCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                            ?: return@runCatching
                        uxManager = manager
                        trySend(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true)
                        manager.registerListener { restrictions ->
                            trySend(restrictions?.isRequiresDistractionOptimization == true)
                        }
                    }.onFailure { Log.w(TAG, "주행 제한 구독 실패: 시나리오 값을 따름", it) }
                }
            } catch (e: Throwable) {
                // android.car 라이브러리가 없는 기기 (NoClassDefFoundError 등)
                Log.w(TAG, "android.car 사용 불가: 시나리오 값을 따름", e)
                null
            }
        }
        awaitClose {
            runCatching { uxManager?.unregisterListener() }
            runCatching { car?.disconnect() }
        }
    }.distinctUntilChanged()
}
