package com.naechamon.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingDeciderTest {

    @Test
    fun egg_comesFirst() {
        assertEquals(GreetingType.EGG, GreetingDecider.decide(hour = 8, hatched = false, worstConsumable = ConditionLevel.CAUTION))
    }

    @Test
    fun consumableIssue_isHungry() {
        assertEquals(GreetingType.HUNGRY, GreetingDecider.decide(8, true, ConditionLevel.NOTICE))
        assertEquals(GreetingType.HUNGRY, GreetingDecider.decide(8, true, ConditionLevel.CAUTION))
    }

    @Test
    fun timeOfDay_whenAllGood() {
        assertEquals(GreetingType.MORNING, GreetingDecider.decide(7, true, ConditionLevel.NORMAL))
        assertEquals(GreetingType.DAY, GreetingDecider.decide(14, true, null))
        assertEquals(GreetingType.EVENING, GreetingDecider.decide(20, true, ConditionLevel.NORMAL))
        // 경계: 11시는 낮, 5시는 아침
        assertEquals(GreetingType.DAY, GreetingDecider.decide(11, true, null))
        assertEquals(GreetingType.MORNING, GreetingDecider.decide(5, true, null))
    }
}
