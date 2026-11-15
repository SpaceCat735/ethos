package com.naechamon.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.naechamon.data.model.VehicleState
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// 실제 차량 데이터 소스.
// - Vehicle SDK: 기어(Gear), 누적 주행거리(Odometer). 공개 퍼미션 CAR_DRIVE만 쓴다
// - AAOS android.car: 차속(PERF_VEHICLE_SPEED), 주행 제한(CarUxRestrictions). Vehicle SDK에 없는 값
// - 경고등(telltale)·타이어 공기압 수치는 PRIVATE 퍼미션이 필요해서 쓰지 않는다 → warnings는 항상 비어 있음
// - Tire.getLowTirePressure는 저압 "경고"가 아니라 저압 "기준값"(CRITICALLY_LOW_TIRE_PRESSURE)이라 TPMS 판정에 쓸 수 없다
// 지원 확인에 실패하거나 퍼미션이 없으면 그 값은 안전한 기본값(기어 모름·속도 0·제한 중)으로 둔다.
class PleosVehicleDataSource(context: Context) : VehicleDataSource {

    private val appContext = context.applicationContext

    override val vehicleState: Flow<VehicleState> = callbackFlow<PleosUpdate> {
        val cleanup = Cleanup()

        // 1) Vehicle SDK
        val vehicle = Vehicle(appContext)
        val initialized = runCatching { vehicle.initialize() }
            .onFailure { Log.w(TAG, "Vehicle SDK 초기화 실패: 기어·주행거리 없이 동작", it) }
            .isSuccess
        if (initialized) {
            launch { setupOdometer(vehicle, cleanup) }
            launch { setupGear(vehicle, cleanup) }
        }

        // 2) AAOS 차속·주행 제한
        val car = setupCarService(cleanup)

        awaitClose {
            // 리스너를 모두 해제한 뒤에 연결을 끊는다
            cleanup.runAll()
            car?.let { runCatching { it.disconnect() } }
            if (initialized) runCatching { vehicle.release() }.onFailure { Log.w(TAG, "Vehicle SDK release 실패", it) }
        }
    }
        .scan(PleosUpdate.INITIAL_STATE) { state, update -> update.applyTo(state) }
        .distinctUntilChanged()

    // ---- Vehicle SDK ----

    private suspend fun ProducerScope<PleosUpdate>.setupOdometer(vehicle: Vehicle, cleanup: Cleanup) {
        awaitPermissionOrTimeout(PERMISSION_CAR_DRIVE, SDK_PERMISSION_WAIT_MILLIS)
        val odometer = vehicle.getOdometer()
        val config = retrying("Odometer 지원 확인") {
            awaitSdk<CapabilityConfig> { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities(ok, fail) }
        }
        if (config == null || !PleosMapping.isReadable(config.accessType.name)) {
            Log.i(TAG, "누적 주행거리 미지원: odometerKm는 null로 둔다 (${config?.accessType})")
            return
        }
        // 처음 값
        awaitSdk<Float?> { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) }
            .onSuccess { trySend(PleosUpdate.OdometerChanged(PleosMapping.odometerKm(it))) }
        // 바뀔 때마다
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                trySend(PleosUpdate.OdometerChanged(PleosMapping.odometerKm(value)))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "주행거리 구독 오류", e)
            }
        }
        runCatching { odometer.registerDistanceDriven(listener) }
            .onSuccess { cleanup.add { odometer.unregisterDistanceDriven(listener) } }
            .onFailure { Log.w(TAG, "주행거리 구독 실패", it) }
    }

    // Gear에는 지원 확인 메서드가 없다. 처음 값 읽기가 성공해야 지원하는 것으로 보고 구독한다.
    // SDK 2.3.2에서 Gear API는 deprecated("Google GEAR_SELECTION property를 쓰라")이고 공식 샘플도 뺐다.
    // AAOS GEAR_SELECTION은 별도 퍼미션(CAR_POWERTRAIN)이 필요해 승인 전까지 이 API를 쓴다
    @Suppress("DEPRECATION")
    private suspend fun ProducerScope<PleosUpdate>.setupGear(vehicle: Vehicle, cleanup: Cleanup) {
        awaitPermissionOrTimeout(PERMISSION_CAR_DRIVE, SDK_PERMISSION_WAIT_MILLIS)
        val gear = vehicle.getGear()
        val initial = retrying("기어 읽기") {
            awaitSdk<VehicleGear?> { ok, fail -> gear.getCurrentGearSelectionState(ok, fail) }
        }
        if (initial == null) {
            Log.i(TAG, "기어 미지원 또는 읽기 실패: Gear.UNKNOWN 유지(주행 중으로 판단)")
            return
        }
        trySend(PleosUpdate.GearChanged(PleosMapping.gearFromSdkName(initial.name)))
        val listener = object : GearSelectionListener {
            override fun onSelected(value: VehicleGear?) {
                trySend(PleosUpdate.GearChanged(PleosMapping.gearFromSdkName(value?.name)))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "기어 구독 오류", e)
                trySend(PleosUpdate.GearChanged(PleosMapping.gearFromSdkName(null)))
            }
        }
        runCatching { gear.registerGearSelection(listener) }
            .onSuccess { cleanup.add { gear.unregisterGearSelection(listener) } }
            .onFailure { Log.w(TAG, "기어 구독 실패", it) }
    }

    // SDK의 onComplete/onFailed 콜백을 한 번의 결과로 바꾼다. 응답이 없으면 실패로 본다
    private suspend fun <T> awaitSdk(call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): Result<T> =
        withTimeoutOrNull(SDK_CALL_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    call(
                        { value -> if (cont.isActive) cont.resume(Result.success(value)) },
                        { error -> if (cont.isActive) cont.resume(Result.failure(error)) },
                    )
                }.onFailure { if (cont.isActive) cont.resume(Result.failure(it)) }
            }
        } ?: Result.failure(IllegalStateException("Vehicle SDK 응답 없음"))

    // initialize() 뒤 서비스 연결은 비동기라 처음 몇 번은 실패할 수 있다. 몇 번 다시 해 보고 안 되면 미지원으로 본다
    private suspend fun <T> retrying(what: String, call: suspend () -> Result<T>): T? {
        repeat(SDK_RETRY_COUNT) { attempt ->
            val result = call()
            if (result.isSuccess) return result.getOrNull()
            Log.d(TAG, "$what 실패 (${attempt + 1}/$SDK_RETRY_COUNT): ${result.exceptionOrNull()?.message}")
            delay(SDK_RETRY_DELAY_MILLIS)
        }
        return null
    }

    // ---- AAOS (android.car) ----

    // 연결을 끊을 수 있게 Car를 돌려준다. 못 쓰면 null
    private fun ProducerScope<PleosUpdate>.setupCarService(cleanup: Cleanup): Car? {
        // createCar(…, lifecycleListener)는 Android 11(API 30)부터. 그 아래는 속도 0·제한 중으로 둔다
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "API ${Build.VERSION.SDK_INT}: 차속·주행 제한 미사용")
            return null
        }
        val car = try {
            // 연결은 기다리지 않는다. 준비되면 메인 스레드에서 리스너가 불린다 (재연결 때도)
            Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { readyCar, ready ->
                if (ready) onCarReady(readyCar, cleanup) else trySend(PleosUpdate.CarServiceLost)
            }
        } catch (e: Throwable) {
            // android.car 라이브러리가 없는 기기 (NoClassDefFoundError 등)
            Log.w(TAG, "android.car 사용 불가: 차속·주행 제한 없이 동작", e)
            null
        }
        return car
    }

    private fun ProducerScope<PleosUpdate>.onCarReady(car: Car, cleanup: Cleanup) {
        // 주행 제한: 퍼미션 없이 쓸 수 있다
        runCatching {
            val uxManager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                ?: return@runCatching
            trySend(
                PleosUpdate.UxRestrictionChanged(
                    PleosMapping.isUxRestricted(uxManager.currentCarUxRestrictions?.isRequiresDistractionOptimization),
                ),
            )
            uxManager.registerListener { restrictions ->
                trySend(PleosUpdate.UxRestrictionChanged(PleosMapping.isUxRestricted(restrictions?.isRequiresDistractionOptimization)))
            }
            cleanup.add { uxManager.unregisterListener() }
        }.onFailure { Log.w(TAG, "주행 제한 구독 실패: 제한 중으로 유지", it) }

        // 차속: CAR_SPEED(dangerous) 퍼미션이 필요하다. 허용될 때까지 기다렸다가 구독한다.
        // 매니페스트에 선언이 없으면(1차 제출본) 기다리지 않고 차속 0으로 둔다. 선언 한 줄을 되살리면 다시 읽는다
        launch {
            if (!isDeclared(PERMISSION_CAR_SPEED)) {
                Log.i(TAG, "차속 퍼미션 선언 없음: 차속은 0으로 둔다")
                return@launch
            }
            awaitPermission(PERMISSION_CAR_SPEED)
            if (!car.isConnected) return@launch
            registerSpeed(car, cleanup)
        }
    }

    // registerCallback·status는 API 35 스텁에서 deprecated지만, 대상 기기(Pleos Connect, API 34)에는 새 API가 없어 이것을 쓴다
    @Suppress("DEPRECATION")
    private fun ProducerScope<PleosUpdate>.registerSpeed(car: Car, cleanup: Cleanup) {
        runCatching {
            val propertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: return@runCatching
            if (propertyManager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null) {
                Log.i(TAG, "차속 미지원: 0으로 둔다")
                return@runCatching
            }
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val speed = if (value.status == CarPropertyValue.STATUS_AVAILABLE) value.value as? Float else null
                    trySend(PleosUpdate.SpeedChanged(PleosMapping.speedKmhFromMetersPerSecond(speed)))
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    trySend(PleosUpdate.SpeedChanged(PleosMapping.speedKmhFromMetersPerSecond(null)))
                }
            }
            val registered = propertyManager.registerCallback(
                callback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL,
            )
            if (registered) cleanup.add { propertyManager.unregisterCallback(callback) }
        }.onFailure { Log.w(TAG, "차속 구독 실패: 0으로 둔다", it) }
    }

    // ---- 퍼미션 ----

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    // 매니페스트에 선언된 퍼미션인지. 선언이 없으면 물어볼 수도 없어서 영원히 기다리게 되므로 먼저 확인한다
    @Suppress("DEPRECATION")
    private fun isDeclared(permission: String): Boolean = runCatching {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(permission) == true
    }.getOrDefault(false)

    // 화면에서 퍼미션 창을 띄우는 동안 기다린다. 거부하면 계속 기다리기만 한다 (값은 기본값 유지)
    private suspend fun awaitPermission(permission: String) {
        var logged = false
        while (!isGranted(permission)) {
            if (!logged) {
                Log.i(TAG, "$permission 허용 대기 중")
                logged = true
            }
            delay(PERMISSION_POLL_MILLIS)
        }
    }

    // Vehicle SDK 퍼미션은 보호 수준을 확인하지 못했다. 조금 기다려 보고, 그래도 없으면 일단 호출해 본다(SDK가 실패를 알려 줌)
    private suspend fun awaitPermissionOrTimeout(permission: String, timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { awaitPermission(permission) }
    }

    // 등록한 리스너 해제 작업 모음. 콜백(메인 스레드)과 수집 코루틴이 동시에 만질 수 있어 잠근다.
    // 이미 닫힌 뒤에 들어온 해제 작업은 바로 실행한다 (닫히는 중에 늦게 등록된 리스너 정리)
    private class Cleanup {
        private val actions = mutableListOf<() -> Unit>()
        private var closed = false

        @Synchronized
        fun add(action: () -> Unit) {
            if (closed) {
                runCatching(action)
            } else {
                actions += action
            }
        }

        @Synchronized
        fun runAll() {
            closed = true
            actions.asReversed().forEach { action ->
                runCatching(action).onFailure { Log.w(TAG, "해제 중 오류", it) }
            }
            actions.clear()
        }
    }

    companion object {
        private const val TAG = "PleosVehicleDataSource"

        const val PERMISSION_CAR_DRIVE = "pleos.car.permission.CAR_DRIVE"
        // android.car.Car.PERMISSION_SPEED 와 같은 값 (android.car가 없는 기기에서도 클래스 로딩 없이 쓰려고 문자열로 둔다)
        const val PERMISSION_CAR_SPEED = "android.car.permission.CAR_SPEED"

        // 실차 모드에서 화면이 실행 중에 요청할 퍼미션. 이미 허용됐거나 일반 퍼미션이면 창이 뜨지 않는다.
        // 매니페스트에 선언이 없는 퍼미션(지금은 CAR_SPEED)은 창 없이 바로 거부된다
        val RUNTIME_PERMISSIONS = arrayOf(PERMISSION_CAR_SPEED, PERMISSION_CAR_DRIVE)

        private const val SDK_CALL_TIMEOUT_MILLIS = 3_000L
        private const val SDK_RETRY_COUNT = 5
        private const val SDK_RETRY_DELAY_MILLIS = 2_000L
        private const val SDK_PERMISSION_WAIT_MILLIS = 30_000L
        private const val PERMISSION_POLL_MILLIS = 1_000L
    }
}
