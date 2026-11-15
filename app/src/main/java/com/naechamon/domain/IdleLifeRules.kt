package com.naechamon.domain

// 정차 화면에서 캐릭터가 가만히 있을 때의 작은 움직임 기준값 (앱 기본값). 수치는 여기서만 바꾼다.
// 정차 중에만 쓴다. 주행 화면은 정지 그림 한 장이고, 안전 경고 화면에는 캐릭터가 없다 (안전 규칙).
// 차량 관리 앱이므로 움직임은 작고 느리게 둔다.
object IdleLifeRules {

    // 숨쉬기: 이 시간에 한 번 아주 조금 커졌다가 돌아온다
    const val BREATH_MILLIS = 3_600
    const val BREATH_SCALE = 0.015f

    // 가끔 깡총: 이 간격마다 한 번 작게 뛴다 (처음 나타날 때의 깡총과 별개)
    const val HOP_EVERY_MILLIS = 26_000L
    // 처음 깡총 높이에 대한 비율
    const val HOP_SCALE = 0.45f

    // 표정 잠깐 바꾸기: 이 간격마다 잠깐 다른 표정을 지었다가 돌아온다
    const val GLANCE_EVERY_MILLIS = 17_000L
    const val GLANCE_MILLIS = 1_600L

    // 잠깐 지을 표정. null이면 표정을 바꾸지 않는다.
    // 아픔(경고등)·시무룩(위험운전 직후)은 장난스럽게 바꾸지 않는다. 알은 얼굴이 없다.
    fun glanceOf(stage: CharacterStage, expression: CharacterExpression): CharacterExpression? {
        if (stage == CharacterStage.EGG) return null
        return when (expression) {
            CharacterExpression.NORMAL -> CharacterExpression.HAPPY
            CharacterExpression.HAPPY -> CharacterExpression.PROUD
            CharacterExpression.PROUD -> CharacterExpression.HAPPY
            CharacterExpression.HUNGRY -> CharacterExpression.SAD
            CharacterExpression.SLEEPY -> CharacterExpression.NORMAL
            CharacterExpression.BORED -> CharacterExpression.SLEEPY
            CharacterExpression.SAD, CharacterExpression.HURT -> null
        }
    }
}
