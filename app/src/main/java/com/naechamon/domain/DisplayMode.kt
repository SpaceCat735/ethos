package com.naechamon.domain

// 화면 모드. 화면 층은 이 값만 보고 무엇을 그릴지 정한다.
sealed interface DisplayMode {
    // 주행 중: 캐릭터 표정만, 조작 UI 없음
    data object Driving : DisplayMode

    // 주차 중(P단 + 조작 제한 해제): 리포트·카드 허용
    data object Parked : DisplayMode

    // 긴급: 캐릭터 연출을 멈추고 일반 경고 화면
    data class Emergency(val types: Set<EmergencyType>) : DisplayMode
}

enum class EmergencyType {
    ENGINE,
    BRAKE,
    STEERING,
    AIRBAG,
    COOLANT_OVERHEAT,
    TIRE_PRESSURE,
}
