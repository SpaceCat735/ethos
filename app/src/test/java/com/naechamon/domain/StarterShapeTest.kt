package com.naechamon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterShapeTest {

    @Test
    fun everyChoice_isFreeShapeOption() {
        StarterShape.entries.forEach { choice ->
            val option = CustomSlot.SHAPE.option(choice.shapeId)
            assertNotNull("${choice.name}: 차종 칸에 없는 id", option)
            assertTrue("${choice.name}: 무료 항목이 아님", CustomizationRules.isUnlocked(option!!, ServiceHistory()))
        }
    }

    @Test
    fun choices_mapToExpectedShapes() {
        assertEquals("sedan_m", StarterShape.shapeIdFor(StarterShape.SEDAN))
        assertEquals("suv_m", StarterShape.shapeIdFor(StarterShape.SUV))
        assertEquals("mini", StarterShape.shapeIdFor(StarterShape.COMPACT))
    }

    @Test
    fun later_keepsDefaultSedanM() {
        assertEquals("sedan_m", StarterShape.shapeIdFor(null))
        assertEquals(CustomSlot.SHAPE.defaultId, StarterShape.shapeIdFor(null))
    }

    @Test
    fun choices_areDistinct() {
        assertEquals(StarterShape.entries.size, StarterShape.entries.map { it.shapeId }.toSet().size)
    }
}
