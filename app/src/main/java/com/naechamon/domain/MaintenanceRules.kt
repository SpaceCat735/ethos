package com.naechamon.domain

// 교체 주기. null이면 그 축(거리 또는 기간)은 따지지 않는다.
data class ServiceInterval(val km: Int?, val months: Int?)

// 소모품 정비 주기 수치는 모두 여기에 상수로 모은다
object MaintenanceRules {

    // 출처: 현대 투싼(NX4) 2025 취급설명서 통상조건(가솔린) 10,000km 또는 12개월
    // https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/ko_KR/id6b219dd157c.html
    private val ENGINE_OIL = ServiceInterval(km = 10_000, months = 12)

    // 출처: 현대 쏘나타(DN8) 2024 통상조건 15,000km
    // https://ownersmanual.hyundai.com/full_webhelp/DN8/2024/ko_KR/id6b219dd157c.html
    // 기간: 현대 투싼(NX4) 2025 공조 필터 12개월
    // https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/ko_KR/id24275127be8.html
    private val AC_FILTER = ServiceInterval(km = 15_000, months = 12)

    // 앱 기본값. 공식 km 주기 없음, 현대 설명서는 마모 한계(홈 깊이 1.6mm)만 제시
    // https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/ko_KR/id844cd1e1bbb.html
    private val TIRE = ServiceInterval(km = 50_000, months = null)

    // 앱 기본값. 공식 교체 주기 없음(설명서는 점검 항목). 제동 빈도 반영은 #5 이후
    private val BRAKE_PAD = ServiceInterval(km = 40_000, months = null)

    // 앱 기본값. 공식 주기 없음
    private val WIPER = ServiceInterval(km = null, months = 6)

    // 가혹 조건 주기. 확인된 것은 엔진오일뿐이고 나머지는 일반 주기를 그대로 쓴다 (이슈 #20)
    // 출처: 현대 2026 취급설명서 요약 — 가혹 조건 엔진오일 7,500km 또는 6개월 (터보 등 일부 5,000km, 차종별 확인 필요)
    // https://ownersmanual.hyundai.com/full_webhelp/us4/2026/ko_kr/id7b77f1c5ffe.html
    private val ENGINE_OIL_SEVERE = ServiceInterval(km = 7_500, months = 6)

    // 예고 여유: 교체 1,000km 또는 1개월 전부터 예고 (기획안 3-4)
    const val NOTICE_MARGIN_KM = 1_000
    const val NOTICE_MARGIN_MONTHS = 1L

    // 주기가 취급설명서에서 온 값인지. 아니면 앱 기본값(일반 권장값)이라 화면에 "취급설명서 기준"이라고 쓰지 않는다
    fun isFromManual(consumable: Consumable): Boolean = when (consumable) {
        Consumable.ENGINE_OIL, Consumable.AC_FILTER -> true
        Consumable.TIRE, Consumable.BRAKE_PAD, Consumable.WIPER -> false
    }

    fun intervalOf(consumable: Consumable, severe: Boolean = false): ServiceInterval = when (consumable) {
        Consumable.ENGINE_OIL -> if (severe) ENGINE_OIL_SEVERE else ENGINE_OIL
        Consumable.TIRE -> TIRE
        Consumable.BRAKE_PAD -> BRAKE_PAD
        Consumable.AC_FILTER -> AC_FILTER
        Consumable.WIPER -> WIPER
    }
}
