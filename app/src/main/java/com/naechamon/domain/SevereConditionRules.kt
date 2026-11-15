package com.naechamon.domain

// 가혹 조건 판정 기준값 (앱 기본값). 수치는 여기서만 바꾼다.
// 취급설명서의 가혹 조건 항목 중 주행 데이터로 판정할 수 있는 것만 쓴다.
// 매뉴얼에 횟수·거리 수치는 없으므로 임계값은 팀이 정한 값이다 (이슈 #20).
// 출처: 현대 취급설명서 가혹 조건 항목 https://ownersmanual.hyundai.com/full_webhelp/us4/2026/ko_kr/id7b77f1c5ffe.html
object SevereConditionRules {

    // 판정에 필요한 최소 데이터: 트립 수와 기간 (이 전에는 '알' 상태)
    const val MIN_TRIPS = 10
    const val MIN_DAYS = 14

    // 짧은 거리 반복 주행: 이 거리 미만 트립이 전체의 이 비율 이상
    const val SHORT_TRIP_MAX_KM = 8.0
    const val SHORT_TRIP_RATIO = 0.5

    // 정체·잦은 정지와 출발: 평균 속도가 이 값 미만인 트립이 전체의 이 비율 이상
    const val SLOW_TRIP_MAX_AVG_KMH = 20.0
    const val SLOW_TRIP_RATIO = 0.5

    // 잦은 급가감속: 100km당 급가감속 횟수
    const val HARD_EVENTS_PER_100KM = 10.0

    // 위 항목 중 이 개수 이상 해당하면 가혹 조건
    const val CRITERIA_REQUIRED = 2

    // 차BTI: 장거리·고속 판정
    const val LONG_TRIP_MIN_KM = 30.0
    const val LONG_TRIP_RATIO = 0.3
    const val HIGHWAY_MIN_AVG_KMH = 60.0
    // 출퇴근 시간대 (시작 시각 기준, 평일)
    const val COMMUTE_MORNING_START_HOUR = 7
    const val COMMUTE_MORNING_END_HOUR = 9
    const val COMMUTE_EVENING_START_HOUR = 17
    const val COMMUTE_EVENING_END_HOUR = 20
    const val COMMUTE_TRIP_RATIO = 0.4
    const val WEEKEND_LONG_TRIP_RATIO = 0.2
}
