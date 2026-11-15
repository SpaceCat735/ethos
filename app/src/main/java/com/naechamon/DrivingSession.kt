package com.naechamon

import com.naechamon.data.DemoVehicleProfile
import com.naechamon.data.VehicleDataSource
import com.naechamon.data.model.VehicleState
import com.naechamon.data.model.VehicleWarning
import com.naechamon.domain.AppearanceDecider
import com.naechamon.domain.CharacterAppearance
import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.CharacterStage
import com.naechamon.domain.Consumable
import com.naechamon.domain.ConsumableCondition
import com.naechamon.domain.DisplayMode
import com.naechamon.domain.DisplayModeDecider
import com.naechamon.domain.GreetingDecider
import com.naechamon.domain.GreetingType
import com.naechamon.domain.HealthCheckup
import com.naechamon.domain.HealthCheckupBuilder
import com.naechamon.domain.LiveDrivingTracker
import com.naechamon.domain.ParkedReport
import com.naechamon.domain.ParkedReportBuilder
import com.naechamon.domain.TripSummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 화면이 그릴 한 시점의 결과 묶음
data class ScreenState(
    val mode: DisplayMode,
    val appearance: CharacterAppearance,
    val todayDistanceKm: Double,
    val report: ParkedReport,
    val checkup: HealthCheckup,
    val greeting: GreetingType,
)

// 주행 기록과 화면 상태를 화면(Activity) 밖에서 들고 있는 곳.
// 화면이 내비에 가려지거나, 창 크기가 바뀌어 Activity가 다시 만들어져도 기록이 이어진다.
// record()와 screens()는 같은 단일 스레드 scope에서 돌려야 한다 (LiveDrivingTracker는 스레드 안전하지 않음).
class DrivingSession(
    private val dataSource: VehicleDataSource,
    // 시나리오 배속. 실제 차량이면 1
    private val playbackSpeed: Float,
    // 기기 부팅 후 경과 시간(ms). 테스트에서 바꿔 끼운다
    private val elapsedMillis: () -> Long,
    private val today: () -> LocalDate = LocalDate::now,
    // true면 데모 운전 기록을 13일치로 시작한다 (알 → 오늘 주행을 마치면 부화)
    eggStart: Boolean = false,
) {
    private val startMillis = elapsedMillis()

    // 시나리오 시각(ms). 배속 재생이면 그만큼 빨리 흐른다
    fun nowMillis(): Long = ((elapsedMillis() - startMillis) * playbackSpeed).toLong()

    // 차량 정보(누적 주행거리·정비 기록). 실제 SDK 연결 전까지 데모 값. 정비 완료 기록이 여기를 바꾼다
    // 실차 누적 주행거리(VehicleState.odometerKm) 반영은 PT 단계에서 연결한다
    private val profile = MutableStateFlow(DemoVehicleProfile.forToday(today(), eggStart))

    private val tracker = LiveDrivingTracker()

    // 이번 달(지금은 이번 실행) 동안 켜졌던 경고등. 건강검진표의 '관찰 필요' 근거. 영구 저장은 별도 이슈
    private val seenWarnings = mutableSetOf<VehicleWarning>()

    private val latestState = MutableStateFlow<VehicleState?>(null)

    // 화면을 보든 말든 차 상태를 받아 기록한다. 차 상태가 바뀔 때만 추적기에 넣는다
    fun record(scope: CoroutineScope): Job = scope.launch {
        dataSource.vehicleState.collect { state ->
            tracker.update(state, nowMillis())
            seenWarnings += state.warnings
            latestState.value = state
        }
    }

    // 지금까지 기록된 주행 요약 (리포트·테스트용)
    fun summary(): TripSummary = tracker.summary()

    // 오늘 주행을 마지막으로 주차해 마친 시각 (아직 없으면 null)
    private var lastFinishedAt: java.time.LocalDateTime? = null

    // 화면용 상태. 보는 화면이 있을 때만 계산한다 (가려져 있으면 계산을 쉬고 기록만 한다)
    fun screens(scope: CoroutineScope): StateFlow<ScreenState> {
        // 차 상태가 그대로여도 시무룩 해제·졸림은 시간이 지나면 바뀌므로 주기적으로 다시 판정한다
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MILLIS)
            }
        }
        return combine(latestState.filterNotNull(), ticks, profile) { state, _, currentProfile ->
            val now = nowMillis()
            val mode = DisplayModeDecider.decide(state)
            // 주차가 끝난 오늘 주행만 운전 기록에 더한다. 다시 달리는 동안에는 마지막으로 주차를 마친 시각을 그대로 써서
            // 판정이 주행 중에 바뀌지 않고, 한 번 깨어난 알이 다시 알로 돌아가지 않게 한다
            if (mode == DisplayMode.Parked && tracker.distanceKm > 0.0) lastFinishedAt = java.time.LocalDateTime.of(today(), LocalTime.now())
            val finishedAt = lastFinishedAt
            val report = ParkedReportBuilder.build(tracker.summary(), currentProfile, today(), finishedAt)
            val appearance = AppearanceDecider.decide(
                warnings = state.warnings,
                continuousDrivingMillis = tracker.continuousDrivingMillis(now),
                consumableLevels = report.consumables.map { it.level },
                tireLevel = report.consumables.firstOrNull { it.consumable == Consumable.TIRE }?.level,
                lastEventTimeMillis = tracker.lastEventTimeMillis,
                nowMillis = now,
                mood = tracker.mood,
                slowDrivingMillis = tracker.slowDrivingMillis(now),
                // 차BTI가 판정되면 부화, 데이터가 모자라면 알
                hatched = report.carBti != null,
            )
            val checkup = HealthCheckupBuilder.build(state.warnings, report.consumables, YearMonth.now(), pastWarnings = seenWarnings.toSet())
            val greeting = GreetingDecider.decide(LocalTime.now().hour, hatched = report.carBti != null, worstConsumable = report.worstConsumable?.level)
            ScreenState(mode, appearance, tracker.distanceKm, report, checkup, greeting)
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), initialScreen())
    }

    // 정비 완료: 지금 누적 주행거리·오늘 날짜로 기록을 새로 시작한다 (앱을 끄면 사라지는 임시 저장)
    fun markServiced(consumable: Consumable, odometerKm: Int) {
        profile.update { current ->
            current.copy(serviceRecords = current.serviceRecords + (consumable to ConsumableCondition.reset(odometerKm, today())))
        }
    }

    // 첫 값이 오기 전에는 안전한 쪽(주행)으로 둔다
    private fun initialScreen(): ScreenState {
        val report = ParkedReportBuilder.build(TripSummary(0.0, emptyList(), tracker.mood), profile.value, today())
        return ScreenState(
            mode = DisplayMode.Driving,
            appearance = CharacterAppearance(CharacterStage.CAR, CharacterExpression.NORMAL),
            todayDistanceKm = 0.0,
            report = report,
            checkup = HealthCheckupBuilder.build(emptySet(), report.consumables, YearMonth.now()),
            greeting = GreetingType.DAY,
        )
    }

    private companion object {
        const val TICK_MILLIS = 100L
        // 창 크기 변경처럼 잠깐 화면이 사라질 때는 계산을 멈추지 않는다
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
