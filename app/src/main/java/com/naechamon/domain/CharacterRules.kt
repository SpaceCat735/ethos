package com.naechamon.domain

// 캐릭터 표정 판정 기준값. 수치는 여기서만 바꾼다. (앱 기본값)
object CharacterRules {

    // 연속 운전이 이 시간 이상이면 졸림 (기획안 5장: 연속 운전 2시간)
    const val SLEEPY_CONTINUOUS_DRIVING_MILLIS = 2 * 60 * 60 * 1000L

    // 위험운전 뒤 이 시간 동안 시무룩
    const val SAD_AFTER_EVENT_MILLIS = 10_000L

    // 기분 점수가 이 값 이상이면 기쁨
    const val HAPPY_MOOD_MIN = 70

    // 정체: 이 속도 미만으로 이 시간 이상 기어가면 답답한 표정 (가혹 조건의 저속 기준과 같은 20km/h)
    const val BORED_SPEED_KMH = 20f
    const val BORED_SLOW_DRIVING_MILLIS = 3 * 60 * 1000L

    // 부드러운 운전: 이 시간 이상 달리는 동안 급정거·급가속이 없으면 뿌듯한 표정
    const val PROUD_SMOOTH_DRIVING_MILLIS = 10 * 60 * 1000L

    // 이 속도 미만이면 정지로 본다 (P단 + 정지 = 연속 운전 시간 초기화)
    const val STOPPED_SPEED_KMH = 0.5f
}
