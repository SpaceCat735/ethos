package com.naechamon.domain

import java.time.LocalDate
import java.time.LocalDateTime

// 정비 수첩: 성장 = 관리 이력의 누적 (2026-11-07 팀 결정).
// 정비 완료 기록 하나가 도장 하나다. 도장이 쌓이면 관리 등급이 오른다. 주행거리·운전 점수로는 오르지 않는다.

// 도장 하나. 이전 버전에서 저장한 기록은 날짜가 없다 (null)
data class CareStamp(val consumable: Consumable, val date: LocalDate?)

// 관리 등급 (선언 순서 = 낮은 등급 → 높은 등급). 내려가지 않는다
enum class CareGrade { NEW, STARTED, REGULAR, THOROUGH }

object CareGradeRules {
    // 등급별 최소 도장 수 (앱 기본값)
    const val STARTED_MIN = 1
    const val REGULAR_MIN = 3
    const val THOROUGH_MIN = 6

    fun gradeOf(stampCount: Int): CareGrade = when {
        stampCount >= THOROUGH_MIN -> CareGrade.THOROUGH
        stampCount >= REGULAR_MIN -> CareGrade.REGULAR
        stampCount >= STARTED_MIN -> CareGrade.STARTED
        else -> CareGrade.NEW
    }

    // 다음 등급까지 남은 도장 수. 가장 높은 등급이면 null
    fun stampsToNext(stampCount: Int): Int? = when {
        stampCount < STARTED_MIN -> STARTED_MIN - stampCount
        stampCount < REGULAR_MIN -> REGULAR_MIN - stampCount
        stampCount < THOROUGH_MIN -> THOROUGH_MIN - stampCount
        else -> null
    }
}

// 화면이 그릴 정비 수첩. 도장은 최근 것이 먼저 (날짜 없는 옛 기록은 맨 뒤)
data class CareBook(val stamps: List<CareStamp>) {
    val grade: CareGrade get() = CareGradeRules.gradeOf(stamps.size)
    val stampsToNext: Int? get() = CareGradeRules.stampsToNext(stamps.size)
    val newestFirst: List<CareStamp> get() = stamps.withIndex()
        .sortedWith(compareByDescending<IndexedValue<CareStamp>> { it.value.date != null }.thenByDescending { it.value.date }.thenByDescending { it.index })
        .map { it.value }

    // 도장 하나를 더 찍으면 등급이 오르는지 (등급이 오를 때만 한 줄 안내를 띄운다)
    fun gradeAfterOneMore(): CareGrade? = CareGradeRules.gradeOf(stamps.size + 1).takeIf { it != grade }
}

// 끝난 주행을 운전 기록에 더한다. 주차가 끝난 트립만 더한다 (주행 중에 판정이 바뀌어 부화·차BTI가 흔들리지 않게)
object TripHistoryRules {
    // 이보다 짧으면 주행으로 치지 않는다 (시동만 걸고 끈 경우)
    const val MIN_TRIP_KM = 0.05

    fun withFinishedTrip(history: List<TripRecord>, trip: TripSummary, finishedAt: LocalDateTime?): List<TripRecord> =
        if (finishedAt == null || trip.distanceKm < MIN_TRIP_KM) {
            history
        } else {
            history + TripRecord(finishedAt.minusNanos(trip.drivingMillis * 1_000_000), trip.distanceKm, trip.drivingMillis, trip.events.size)
        }
}
