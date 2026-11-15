package com.naechamon.domain

// 첫 실행 때 한 번 묻는 차급 고르기 (이슈 #66). 캐릭터 모양만 바뀌고, 언제든 내 차 설정에서 다시 고를 수 있다.
// 차종 이름(실차 모델)으로 나누지 않고, 차고의 차급 일반형 id로만 고른다.
enum class StarterShape(val shapeId: String) {
    SEDAN("sedan_m"),
    SUV("suv_m"),
    COMPACT("mini"),
    ;

    companion object {
        // 고른 차급의 차종 id. "나중에"(null)는 기본 차종(승용 중형)을 그대로 쓴다
        fun shapeIdFor(choice: StarterShape?): String = choice?.shapeId ?: CustomSlot.SHAPE.defaultId
    }
}
