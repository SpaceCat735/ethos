package com.naechamon.domain

import java.time.LocalDate

// 소모품 하나의 상태. 남은 값이 null이면 그 축(거리/기간)은 기준이 없는 것
data class ConsumableStatus(
    val consumable: Consumable,
    val level: ConditionLevel,
    val remainingKm: Int?,
    // 기간 기준까지 남은 날. 기간 기준이 없으면 null
    val remainingDays: Long?,
    val interval: ServiceInterval,
    // 주기가 취급설명서 값인지 (아니면 앱 기본값)
    val fromManual: Boolean = MaintenanceRules.isFromManual(consumable),
)

// 가족 칭찬 카드 단계
enum class PraiseLevel { GREAT, ROUGH }

// 이번 주행 요약 (추적기에서 가져온 값)
data class TripSummary(
    val distanceKm: Double,
    val events: List<DrivingEvent>,
    val mood: Int,
    // 연비. 데이터 연결 전에는 null
    val tripEfficiency: Double? = null,
    // 오늘 실제로 달린 시간(ms, 시나리오 시각). 끝난 주행을 운전 기록에 더할 때 평균 속도 계산에 쓴다
    val drivingMillis: Long = 0L,
)

// 차량 정보: 누적 주행거리, 소모품별 마지막 정비 기록, 공인 연비
data class VehicleProfile(
    val odometerKm: Int,
    val serviceRecords: Map<Consumable, ServiceRecord>,
    val ratedEfficiency: Double? = null,
    val totalExperience: Double = 0.0,
    // 끝난 주행 기록 (차BTI·가혹 조건 판정용)
    val tripHistory: List<TripRecord> = emptyList(),
)

// 주차 후 리포트. 화면은 이 값만 받아 그린다.
// 겉(카드 앞면)은 가족이 같이 보기 좋게, 상세는 운전자가 확인하는 정보.
data class ParkedReport(
    val distanceKm: Double,
    val events: List<DrivingEvent>,
    val hardBrakeCount: Int,
    val hardAccelCount: Int,
    val praise: PraiseLevel,
    val mood: Int,
    val experience: Double,
    val fuelMultiplier: Double,
    val tripEfficiency: Double?,
    val ratedEfficiency: Double?,
    val totalExperience: Double,
    val nextStageExperience: Double,
    val odometerKm: Int,
    val consumables: List<ConsumableStatus>,
    val severe: SevereConditionResult,
    // 데이터가 모자라면 null (알 상태)
    val carBti: CarBtiType?,
) {
    val eventCount: Int get() = hardBrakeCount + hardAccelCount

    // 시동 직후(아직 안 달림): 리포트 대신 시동 인사 화면
    val isBeforeTrip: Boolean get() = distanceKm < 0.05 && events.isEmpty()

    // 차량 상태 화면의 칭찬 한 줄. 아직 안 달렸으면 칭찬할 주행이 없으니 null
    val todayPraise: PraiseLevel? get() = if (isBeforeTrip) null else praise

    // 가장 급한 소모품 (주의 > 예고). 모두 정상이면 null
    val worstConsumable: ConsumableStatus? get() =
        consumables.filter { it.level != ConditionLevel.NORMAL }.maxByOrNull { it.level }
}

object ParkedReportBuilder {

    // finishedTripAt: 오늘 주행이 끝나 P단 정지가 된 시각. 있으면 그 주행을 운전 기록에 더해 차BTI·가혹 조건을 판정한다 (알 → 부화)
    fun build(trip: TripSummary, profile: VehicleProfile, today: LocalDate, finishedTripAt: java.time.LocalDateTime? = null): ParkedReport {
        val hardBrakeCount = trip.events.count { it.type == DrivingEventType.HARD_DECELERATION || it.type == DrivingEventType.HARD_STOP }
        val hardAccelCount = trip.events.size - hardBrakeCount
        val praise = if (hardBrakeCount + hardAccelCount <= ReportRules.PRAISE_MAX_EVENTS) PraiseLevel.GREAT else PraiseLevel.ROUGH

        val fuelMultiplier = DrivingScorer.fuelMultiplier(trip.tripEfficiency, profile.ratedEfficiency)
        val experience = trip.distanceKm.coerceAtLeast(0.0) * fuelMultiplier

        // 가혹 조건이면 짧은 정비 주기를 쓴다 (기획안 v2 3-2)
        val trips = TripHistoryRules.withFinishedTrip(profile.tripHistory, trip, finishedTripAt)
        val severe = SevereConditionDecider.decide(trips)
        val carBti = CarBtiDecider.decide(trips)

        // 오늘 달린 거리를 더한 누적 주행거리로 소모품을 판정한다
        val odometerKm = profile.odometerKm + trip.distanceKm.toInt()
        val consumables = Consumable.entries.mapNotNull { consumable ->
            profile.serviceRecords[consumable]?.let { ConsumableCondition.status(consumable, it, odometerKm, today, severe.isSevere) }
        }

        return ParkedReport(
            distanceKm = trip.distanceKm,
            events = trip.events,
            hardBrakeCount = hardBrakeCount,
            hardAccelCount = hardAccelCount,
            praise = praise,
            mood = trip.mood,
            experience = experience,
            fuelMultiplier = fuelMultiplier,
            tripEfficiency = trip.tripEfficiency,
            ratedEfficiency = profile.ratedEfficiency,
            totalExperience = profile.totalExperience + experience,
            nextStageExperience = ReportRules.NEXT_STAGE_EXPERIENCE,
            odometerKm = odometerKm,
            consumables = consumables,
            severe = severe,
            carBti = carBti,
        )
    }
}
