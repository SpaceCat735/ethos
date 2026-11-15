package com.naechamon.data

import com.naechamon.domain.Consumable
import com.naechamon.domain.FuelKind
import com.naechamon.domain.ServiceRecord
import com.naechamon.domain.TripRecord
import com.naechamon.domain.VehicleProfile
import java.time.LocalDate
import java.time.LocalDateTime

// 데모용 차량 정보. 실제 SDK 연결(Odometer·CarInfo) 전까지 쓰는 가짜 값이다.
// 3주치 짧은 트립(동네 다람쥐형, 가혹 조건)이라 엔진오일은 가혹 주기 7,500km 기준으로 '주의',
// 공조 필터·와이퍼는 교체 1개월 안(주의), 나머지는 정상이 되게 맞췄다 (데모에서 정비 수첩 도장 3개 → 단골 관리까지 보이게).
object DemoVehicleProfile {

    // 데모 차량 연료. 실차에서는 CarInfo 연료 종류로 정한다 (PT 단계). 꾸미기 화면에서 바꿀 수 있다
    val FUEL = FuelKind.GAS

    // 날짜는 모두 '오늘' 기준 상대값이라 데모 영상을 언제 찍어도 같은 화면이 나온다
    // eggStart: 운전 기록을 13일치(14일 전 0시 30분부터 어제까지)로 둔다 → 알. 오늘 주행이 끝나면 2주가 채워져 부화한다
    fun forToday(today: LocalDate, eggStart: Boolean = false): VehicleProfile = VehicleProfile(
        odometerKm = 42_300,
        serviceRecords = mapOf(
            // 엔진오일: 9,100km 전 교체 → 일반 주기(10,000)면 예고, 가혹 주기(7,500)면 주의
            Consumable.ENGINE_OIL to ServiceRecord(odometerKm = 33_200, date = today.minusMonths(5)),
            // 공조 필터: 12개월 주기, 교체 25일 전 → 예고
            Consumable.AC_FILTER to ServiceRecord(odometerKm = 28_000, date = today.minusMonths(12).plusDays(25)),
            Consumable.TIRE to ServiceRecord(odometerKm = 5_000, date = today.minusMonths(30)),
            Consumable.BRAKE_PAD to ServiceRecord(odometerKm = 10_000, date = today.minusMonths(24)),
            // 와이퍼: 6개월 주기, 교체 20일 전 → 주의
            Consumable.WIPER to ServiceRecord(odometerKm = 40_100, date = today.minusMonths(6).plusDays(20)),
        ),
        // 공인 연비 (km/L). 데모용 가정값
        ratedEfficiency = 12.5,
        totalExperience = 310.0,
        tripHistory = tripHistory(today, if (eggStart) EGG_HISTORY_DAYS else HATCHED_HISTORY_DAYS),
    )

    // 최근 3주, 평일마다 낮 시간 짧은 시내 주행 2회 (4~5km, 평균 17~18km/h → 동네 다람쥐형·가혹 조건), 주말 1회 중거리
    private const val HATCHED_HISTORY_DAYS = 21L
    private const val EGG_HISTORY_DAYS = 14L

    private fun tripHistory(today: LocalDate, days: Long): List<TripRecord> = buildList {
        var day = today.minusDays(days)
        // 알 시작: 첫날은 0시 30분 짧은 주행으로 시작해, 오늘 언제 주행을 마쳐도 2주가 채워지게 한다
        if (days == EGG_HISTORY_DAYS) add(TripRecord(LocalDateTime.of(day, java.time.LocalTime.of(0, 30)), 3.0, 10 * 60_000L, 0))
        while (day < today) {
            val weekend = day.dayOfWeek.value >= 6
            if (weekend) {
                add(TripRecord(LocalDateTime.of(day, java.time.LocalTime.of(11, 0)), 18.0, 40 * 60_000L, 1))
            } else {
                add(TripRecord(LocalDateTime.of(day, java.time.LocalTime.of(10, 30)), 4.2, 14 * 60_000L, 1))
                add(TripRecord(LocalDateTime.of(day, java.time.LocalTime.of(15, 0)), 5.1, 18 * 60_000L, 0))
            }
            day = day.plusDays(1)
        }
    }
}
