package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning

// 표정 위에 덧그리는 상태 표시 (docs/character/README.md 겹 구조)
enum class CharacterMark { SWEAT, FOOT_CAST, WORN_SHOES }

object CharacterMarkDecider {

    // 냉각수 과열·공기압 경고는 긴급 화면이 먼저 뜨므로, 이 표시는 긴급 화면이 아닐 때만 실제로 보인다
    fun decide(warnings: Set<VehicleWarning>, tireLevel: ConditionLevel?): Set<CharacterMark> = buildSet {
        if (VehicleWarning.ENGINE_TEMPERATURE in warnings) add(CharacterMark.SWEAT)
        if (VehicleWarning.TPMS in warnings) add(CharacterMark.FOOT_CAST)
        if (tireLevel == ConditionLevel.CAUTION) add(CharacterMark.WORN_SHOES)
    }
}
