package com.naechamon.domain

import com.naechamon.data.model.Gear
import com.naechamon.data.model.VehicleState

// 주행 중 차 상태를 받아 위험운전을 실시간으로 감지하고 기분 점수와 연속 운전 시간을 갱신한다.
// 급가감속 판정은 DrivingScorer와 같은 기준(DrivingRules)을 쓴다.
// timeMillis는 차 상태가 측정된 시각이어야 한다. 같은 상태를 여러 번 넣으면 가감속이 틀어진다.
class LiveDrivingTracker(initialMood: Int = DrivingRules.MOOD_START) {

    var mood: Int = initialMood.coerceIn(DrivingRules.MOOD_MIN, DrivingRules.MOOD_MAX)
        private set

    // 마지막 위험운전이 감지된 시각. 없으면 null
    var lastEventTimeMillis: Long? = null
        private set

    // 이번 주행에서 감지된 위험운전 전체 (리포트용)
    private val _events = mutableListOf<DrivingEvent>()
    val events: List<DrivingEvent> get() = _events.toList()

    private var previous: SpeedSample? = null
    private var previousType: DrivingEventType? = null

    // 이번 연속 운전이 시작된 시각. P단 + 정지면 null
    private var drivingSinceMillis: Long? = null

    // 정체 속도로 기어가기 시작한 시각. 속도가 오르거나 주차하면 null
    private var slowSinceMillis: Long? = null

    // 오늘 달린 거리(km). 속도 샘플 사이를 사다리꼴로 적분한다 (주행거리 데이터 연결 전 임시값)
    var distanceKm: Double = 0.0
        private set

    // 오늘 실제로 달린 시간(ms). P단 정지 구간은 빼고 더한다
    var drivingMillis: Long = 0L
        private set

    // 이번 주행(출발 ~ P단 정지)이 시작될 때의 거리·위험운전 수. 안전 운전 보너스를 주행마다 따로 판정한다
    private var tripStartKm = 0.0
    private var tripStartEventCount = 0

    // 새로 감지된 위험운전을 돌려준다 (같은 종류가 이어지면 1회)
    fun update(state: VehicleState, timeMillis: Long): DrivingEvent? {
        val last = previous
        if (last != null && timeMillis <= last.timeMillis) return null

        val isParked = state.gear == Gear.PARK && state.speedKmh < CharacterRules.STOPPED_SPEED_KMH
        val wasDriving = drivingSinceMillis != null
        if (!isParked && !wasDriving) {
            tripStartKm = distanceKm
            tripStartEventCount = _events.size
        }
        drivingSinceMillis = if (isParked) null else drivingSinceMillis ?: timeMillis
        val isSlow = !isParked && state.speedKmh < CharacterRules.BORED_SPEED_KMH
        slowSinceMillis = if (isSlow) slowSinceMillis ?: timeMillis else null

        val sample = SpeedSample(timeMillis, state.speedKmh)
        previous = sample
        if (last == null) return null

        if (!isParked || wasDriving) drivingMillis += timeMillis - last.timeMillis
        val hours = (timeMillis - last.timeMillis) / 3_600_000.0
        distanceKm += (last.speedKmh + sample.speedKmh) / 2.0 * hours

        val type = DrivingScorer.classify(last, sample)
        val isNew = type != null && !type.sameFamily(previousType)
        previousType = type
        val event = if (isNew) {
            mood = (mood - DrivingRules.MOOD_PENALTY_PER_EVENT).coerceIn(DrivingRules.MOOD_MIN, DrivingRules.MOOD_MAX)
            lastEventTimeMillis = timeMillis
            DrivingEvent(type!!, timeMillis).also { _events += it }
        } else {
            null
        }

        // 주행이 끝나 P단 정지가 된 순간 한 번만 안전 운전 보너스를 판정한다 (정차 상태가 반복돼도 중복 없음)
        if (wasDriving && isParked) {
            val bonus = DrivingScorer.smoothTripBonus(_events.size - tripStartEventCount, distanceKm - tripStartKm)
            mood = (mood + bonus).coerceIn(DrivingRules.MOOD_MIN, DrivingRules.MOOD_MAX)
        }
        return event
    }

    // 리포트용 요약
    fun summary(): TripSummary = TripSummary(distanceKm = distanceKm, events = events, mood = mood, drivingMillis = drivingMillis)

    // 정체 속도로 이어서 기어간 시간
    fun slowDrivingMillis(nowMillis: Long): Long {
        val since = slowSinceMillis ?: return 0L
        return (nowMillis - since).coerceAtLeast(0L)
    }

    // 차 상태가 그대로여도 시간은 흐르므로 지금 시각을 받아 계산한다
    fun continuousDrivingMillis(nowMillis: Long): Long {
        val since = drivingSinceMillis ?: return 0L
        return (nowMillis - since).coerceAtLeast(0L)
    }
}
