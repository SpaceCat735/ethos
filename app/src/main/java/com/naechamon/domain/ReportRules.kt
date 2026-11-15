package com.naechamon.domain

// 주차 후 리포트 기준값 (앱 기본값). 수치는 여기서만 바꾼다.
object ReportRules {

    // 가족 칭찬 카드: 급정거·급가속 합계가 이 횟수 이하면 칭찬, 넘으면 "살살 가자"
    const val PRAISE_MAX_EVENTS = 1

    // 다음 단계(진화)까지 필요한 누적 경험치
    const val NEXT_STAGE_EXPERIENCE = 500.0
}
