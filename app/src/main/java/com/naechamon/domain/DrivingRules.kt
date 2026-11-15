package com.naechamon.domain

// 운전 습관 점수(기분·경험치) 기준값. 수치는 여기서만 바꾼다.
object DrivingRules {

    // 위험운전 판정 기준 (속도 km/h, 변화량 km/h/초)
    // 출처: 교통안전공단 특허 KR101803662B1 표 1에 실린 기존 위험운전행동 기준 중 택시 값
    // (승용차 공식 기준을 찾지 못해 가장 가까운 값 사용). https://patents.google.com/patent/KR101803662B1/ko

    // 급가속: 6.0km/h 이상에서 1초에 8.0km/h 이상 가속
    const val HARD_ACCEL_MIN_SPEED_KMH = 6.0
    const val HARD_ACCEL_KMH_PER_SEC = 8.0

    // 급출발: 5.0km/h 이하에서 출발해 1초에 10.0km/h 이상 가속
    const val HARD_START_MAX_SPEED_KMH = 5.0
    const val HARD_START_KMH_PER_SEC = 10.0

    // 급감속: 6.0km/h 이상에서 1초에 14.0km/h 이상 감속
    const val HARD_DECEL_MIN_SPEED_KMH = 6.0
    const val HARD_DECEL_KMH_PER_SEC = 14.0

    // 급정지: 1초에 14.0km/h 이상 감속해 5.0km/h 이하가 됨
    const val HARD_STOP_KMH_PER_SEC = 14.0
    const val HARD_STOP_MAX_SPEED_KMH = 5.0

    // 기분: 앱 기본값(게임 밸런스)
    const val MOOD_MIN = 0
    const val MOOD_MAX = 100
    const val MOOD_START = 50
    const val SMOOTH_TRIP_MIN_KM = 1.0 // 위험운전 없이 이 거리 이상 달리면 보너스
    const val SMOOTH_TRIP_MOOD_BONUS = 5
    const val MOOD_PENALTY_PER_EVENT = 3

    // 경험치 = 주행거리(km) × 연비 배수. 연비 배수 = 이번 주행 연비 ÷ 공인 연비 (앱 기본값)
    const val FUEL_MULTIPLIER_MIN = 0.5
    const val FUEL_MULTIPLIER_MAX = 2.0
    const val FUEL_MULTIPLIER_DEFAULT = 1.0 // 연비를 모를 때
}
