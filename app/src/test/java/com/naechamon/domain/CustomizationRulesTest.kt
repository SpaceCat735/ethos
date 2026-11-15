package com.naechamon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizationRulesTest {

    private val none = ServiceHistory()
    private fun custom(vararg pairs: Pair<CustomSlot, String>, name: String = "붕붕이") =
        CarCustomization(name, "붕붕 1004", mapOf(*pairs))

    @Test
    fun everySlot_defaultIsFree() {
        CustomSlot.entries.forEach { slot ->
            assertTrue(slot.name, CustomizationRules.isUnlocked(slot.options.first(), none))
        }
    }

    @Test
    fun everySlot_defaultIdIsInOptionsAndFree() {
        CustomSlot.entries.forEach { slot ->
            val option = slot.option(slot.defaultId)
            assertTrue("${slot.name} 기본값이 options에 없음", option != null)
            assertTrue("${slot.name} 기본값이 무료가 아님", CustomizationRules.isUnlocked(option!!, none))
        }
    }

    @Test
    fun explicitDefaults_shapeColorWheel() {
        assertEquals("sedan_m", CustomSlot.SHAPE.defaultId)
        assertEquals("white", CustomSlot.COLOR.defaultId)
        assertEquals("aero", CustomSlot.WHEEL_STYLE.defaultId)
        assertEquals(7, CustomSlot.SHAPE.options.size)
        assertEquals(28, CustomSlot.COLOR.options.size)
    }

    @Test
    fun sanitize_legacyIds_moveToNearestNewIds() {
        val result = CustomizationRules.sanitize(
            custom(CustomSlot.COLOR to "red", CustomSlot.SHAPE to "hatch"),
            none,
            fallbackName = "에토스",
        )
        assertEquals("ultimate_red", result.choice(CustomSlot.COLOR))
        assertEquals("sedan_s", result.choice(CustomSlot.SHAPE))
        assertEquals("mint", CustomizationRules.sanitize(custom(CustomSlot.COLOR to "mint"), none, "에토스").choice(CustomSlot.COLOR))
    }

    @Test
    fun optionIds_areUniqueWithinSlot() {
        CustomSlot.entries.forEach { slot ->
            assertEquals(slot.name, slot.options.size, slot.options.map { it.id }.toSet().size)
        }
    }

    @Test
    fun defaults_useGivenFuel() {
        val d = CustomizationRules.defaults(FuelKind.EV, "붕붕이", "")
        assertEquals("ev", d.choice(CustomSlot.FUEL))
        assertEquals("sedan_m", d.choice(CustomSlot.SHAPE))
    }

    @Test
    fun shinyWheel_opensOnlyAfterEngineOilRecord() {
        val star = CustomSlot.WHEEL_STYLE.option("star")!!
        assertFalse(CustomizationRules.isUnlocked(star, none))
        assertFalse(CustomizationRules.isUnlocked(star, ServiceHistory(listOf(Consumable.WIPER))))
        assertTrue(CustomizationRules.isUnlocked(star, ServiceHistory(listOf(Consumable.ENGINE_OIL))))
    }

    @Test
    fun finish_needsTwoServiceRecords() {
        val matte = CustomSlot.FINISH.option("matte")!!
        assertFalse(CustomizationRules.isUnlocked(matte, ServiceHistory(listOf(Consumable.WIPER))))
        assertTrue(CustomizationRules.isUnlocked(matte, ServiceHistory(listOf(Consumable.WIPER, Consumable.TIRE))))
    }

    @Test
    fun pendingRewards_stayLocked() {
        val all = ServiceHistory(Consumable.entries + Consumable.entries)
        assertFalse(CustomizationRules.isUnlocked(CustomSlot.HEAD.option("crown")!!, all))
        assertFalse(CustomizationRules.isUnlocked(CustomSlot.REAR.option("spoiler")!!, all))
    }

    @Test
    fun sanitize_lockedOrUnknownChoice_fallsBackToDefault() {
        val result = CustomizationRules.sanitize(
            custom(CustomSlot.WHEEL_STYLE to "star", CustomSlot.SHAPE to "spaceship", CustomSlot.COLOR to "ultimate_red"),
            none,
            fallbackName = "에토스",
        )
        assertEquals("aero", result.choice(CustomSlot.WHEEL_STYLE))
        assertEquals("sedan_m", result.choice(CustomSlot.SHAPE))
        assertEquals("ultimate_red", result.choice(CustomSlot.COLOR))
    }

    @Test
    fun sanitize_keepsUnlockedReward() {
        val result = CustomizationRules.sanitize(
            custom(CustomSlot.WHEEL_STYLE to "star"),
            ServiceHistory(listOf(Consumable.ENGINE_OIL)),
            fallbackName = "에토스",
        )
        assertEquals("star", result.choice(CustomSlot.WHEEL_STYLE))
    }

    @Test
    fun sanitize_trimsAndLimitsName_andFillsEmpty() {
        assertEquals("가나다라마바사아자차", CustomizationRules.sanitize(custom(name = " 가나다라마바사아자차카 "), none, "에토스").name)
        assertEquals("에토스", CustomizationRules.sanitize(custom(name = "  "), none, "에토스").name)
    }

    @Test
    fun species_followsCarBti() {
        assertEquals("egg", CustomizationRules.speciesOf(null))
        assertEquals("squirrel", CustomizationRules.speciesOf(CarBtiType.SQUIRREL))
        assertEquals("picnic", CustomizationRules.speciesOf(CarBtiType.WEEKEND_TRAVELER))
    }

    @Test
    fun newOptions_unlockByServiceRecordsOrFree() {
        fun open(slot: CustomSlot, id: String, h: ServiceHistory) = CustomizationRules.isUnlocked(slot.option(id)!!, h)
        // 처음부터 열린 새 항목
        listOf(
            CustomSlot.DECAL to "wave", CustomSlot.DECAL to "dots", CustomSlot.STICKER to "note", CustomSlot.STICKER to "cloud",
            CustomSlot.WHEEL_STYLE to "mesh", CustomSlot.WHEEL_COLOR to "gunmetal", CustomSlot.WHEEL_COLOR to "bronze",
            CustomSlot.REAR to "carrier", CustomSlot.GLASSES to "goggle", CustomSlot.GLASSES to "square", CustomSlot.GLASSES to "half",
        ).forEach { (slot, id) -> assertTrue("$slot/$id", open(slot, id, none)) }
        // 정비 기록이 있어야 열리는 새 항목 (주행거리로는 열리지 않는다)
        val tire = ServiceHistory(listOf(Consumable.TIRE))
        assertFalse(open(CustomSlot.ROOF_ACC, "bike", none))
        assertTrue(open(CustomSlot.ROOF_ACC, "bike", tire))
        assertTrue(open(CustomSlot.WHEEL_STYLE, "turbine", tire))
        assertTrue(open(CustomSlot.STICKER, "paw", tire))
        assertFalse(open(CustomSlot.HEAD, "starpin", tire))
        assertTrue(open(CustomSlot.HEAD, "pennant", ServiceHistory(listOf(Consumable.WIPER))))
        assertTrue(open(CustomSlot.REAR, "ducktail", ServiceHistory(listOf(Consumable.ENGINE_OIL))))
        val three = ServiceHistory(listOf(Consumable.ENGINE_OIL, Consumable.TIRE, Consumable.WIPER))
        assertTrue(open(CustomSlot.ROOF_ACC, "kayak", three))
        assertTrue(open(CustomSlot.REAR, "wing", three))
        assertFalse(open(CustomSlot.ROOF_ACC, "tent", three))
        assertTrue(open(CustomSlot.ROOF_ACC, "tent", ServiceHistory(three.serviced + Consumable.BRAKE_PAD)))
    }

    @Test
    fun headLamps_thirteenFreeTypes_defaultSlim() {
        assertEquals("slim", CustomSlot.HEAD_LAMP.defaultId)
        assertEquals(13, CustomSlot.HEAD_LAMP.options.size)
        CustomSlot.HEAD_LAMP.options.forEach { assertTrue(it.id, CustomizationRules.isUnlocked(it, none)) }
    }

    @Test
    fun tailLamps_twelveFreeTypes_defaultSplit() {
        assertEquals("split", CustomSlot.TAIL_LAMP.defaultId)
        assertEquals(12, CustomSlot.TAIL_LAMP.options.size)
        CustomSlot.TAIL_LAMP.options.forEach { assertTrue(it.id, CustomizationRules.isUnlocked(it, none)) }
    }

    @Test
    fun tuningSlots_freeBaseAndServiceRewardsOnly() {
        val tuning = listOf(
            CustomSlot.FRONT_BUMPER, CustomSlot.REAR_BUMPER, CustomSlot.EXHAUST, CustomSlot.SIDE, CustomSlot.GRILLE,
            CustomSlot.HOOD, CustomSlot.FENDER, CustomSlot.TRIM, CustomSlot.CALIPER, CustomSlot.ROOF_TOP,
        )
        tuning.forEach { slot ->
            assertEquals(slot.name, "base", slot.defaultId)
            assertTrue(slot.name, slot.options.size in 3..5)
            // 튜닝 보상은 정비 기록으로만 열린다 (판정 대기·주행거리 보상 없음)
            slot.options.forEach { assertFalse("${slot.name}/${it.id}", it.unlock is Unlock.Pending) }
        }
        val two = ServiceHistory(listOf(Consumable.ENGINE_OIL, Consumable.TIRE))
        assertFalse(CustomizationRules.isUnlocked(CustomSlot.SIDE.option("step")!!, ServiceHistory(listOf(Consumable.TIRE))))
        assertTrue(CustomizationRules.isUnlocked(CustomSlot.SIDE.option("step")!!, two))
        assertTrue(CustomizationRules.isUnlocked(CustomSlot.EXHAUST.option("center")!!, two))
        assertEquals("off", CustomSlot.TIRE_LETTER.defaultId)
    }

    @Test
    fun customColor_acceptedOnlyOnPaletteSlotsAndValidHex() {
        fun pick(slot: CustomSlot, v: String, h: ServiceHistory = none) =
            CustomizationRules.sanitize(custom(slot to v), h, "에토스").choice(slot)
        assertEquals("#1A2B3C", pick(CustomSlot.COLOR, "#1a2b3c"))
        assertEquals("#FF8800", pick(CustomSlot.GRILLE_COLOR, "#ff8800"))
        // 형식이 틀리면 기본값
        assertEquals("white", pick(CustomSlot.COLOR, "#12345"))
        assertEquals("white", pick(CustomSlot.COLOR, "red;drop"))
        // 팔레트를 받지 않는 칸
        assertEquals("aero", pick(CustomSlot.WHEEL_STYLE, "#FFFFFF"))
        // 지붕 투톤 직접 색은 지붕 투톤과 같은 조건(정비 기록 3번)으로 열린다
        assertEquals("none", pick(CustomSlot.ROOF, "#FFFFFF"))
        val three = ServiceHistory(listOf(Consumable.ENGINE_OIL, Consumable.TIRE, Consumable.WIPER))
        assertEquals("#FFFFFF", pick(CustomSlot.ROOF, "#ffffff", three))
        listOf(CustomSlot.MIRROR_COLOR, CustomSlot.FRONT_PART_COLOR, CustomSlot.SIDE_PART_COLOR, CustomSlot.REAR_PART_COLOR,
            CustomSlot.FENDER_PART_COLOR, CustomSlot.HOOD_PART_COLOR, CustomSlot.GRILLE_COLOR, CustomSlot.STRIPE_COLOR,
        ).forEach { assertEquals(it.name, "base", it.defaultId) }
    }

    @Test
    fun serviceReward_onlyWhenDue_andOncePerDay() {
        val today = java.time.LocalDate.of(2026, 11, 7)
        // 교체 시기가 아니면(정상) 보상 기록 없음
        assertFalse(ServiceRewardRules.shouldRecord(ConditionLevel.NORMAL, null, today))
        // 주의·교체 권장이면 기록
        assertTrue(ServiceRewardRules.shouldRecord(ConditionLevel.NOTICE, null, today))
        assertTrue(ServiceRewardRules.shouldRecord(ConditionLevel.CAUTION, null, today))
        // 같은 날 같은 소모품을 또 누르면 기록 없음 (반복해서 눌러 보상을 여는 것 막기)
        assertFalse(ServiceRewardRules.shouldRecord(ConditionLevel.CAUTION, today, today))
        // 다른 날이면 다시 기록
        assertTrue(ServiceRewardRules.shouldRecord(ConditionLevel.CAUTION, today.minusDays(1), today))
    }
}
