package com.naechamon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLifeRulesTest {

    @Test
    fun egg_neverChangesExpression() {
        CharacterExpression.entries.forEach {
            assertNull(IdleLifeRules.glanceOf(CharacterStage.EGG, it))
        }
    }

    @Test
    fun hurtAndSad_stayAsTheyAre() {
        // 경고등·위험운전 직후 표정은 장난스럽게 바꾸지 않는다
        assertNull(IdleLifeRules.glanceOf(CharacterStage.CAR, CharacterExpression.HURT))
        assertNull(IdleLifeRules.glanceOf(CharacterStage.CAR, CharacterExpression.SAD))
    }

    @Test
    fun glance_isAlwaysADifferentExpression_andNeverHurt() {
        CharacterExpression.entries.forEach { base ->
            val glance = IdleLifeRules.glanceOf(CharacterStage.CAR, base) ?: return@forEach
            assertNotEquals(base, glance)
            // 잠깐 짓는 표정으로 "아픔"을 쓰면 경고처럼 보인다
            assertNotEquals(CharacterExpression.HURT, glance)
        }
        assertEquals(CharacterExpression.HAPPY, IdleLifeRules.glanceOf(CharacterStage.CAR, CharacterExpression.NORMAL))
    }

    @Test
    fun motion_isSmallAndSlow() {
        // 차량 관리 앱: 움직임은 작고 느리게
        assertTrue(IdleLifeRules.BREATH_SCALE <= 0.02f)
        assertTrue(IdleLifeRules.BREATH_MILLIS >= 3_000)
        assertTrue(IdleLifeRules.HOP_EVERY_MILLIS >= 20_000L)
        assertTrue(IdleLifeRules.GLANCE_MILLIS < IdleLifeRules.GLANCE_EVERY_MILLIS / 5)
    }
}
