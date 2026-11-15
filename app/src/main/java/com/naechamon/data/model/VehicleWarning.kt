package com.naechamon.data.model

// 계기판 경고등. Vehicle SDK의 TelltaleType 중 에토스가 쓰는 것만 둔다.
enum class VehicleWarning {
    ENGINE,
    ENGINE_TEMPERATURE,
    ABS,
    ELECTRONIC_PARKING_BRAKE,
    MDPS,
    AIRBAG,
    TPMS,
    LOW_VOLT_BATTERY,
}
