package com.naechamon.domain

import com.naechamon.data.model.VehicleWarning
import java.time.YearMonth

// 캐릭터 신체 부위. 차량 경고·소모품 상태를 여기에 매핑한다 (기획서 3-4 건강검진)
enum class BodyPart {
    // 엔진 경고등
    HEART,
    // 제동 (ABS, 전자식 주차 브레이크)
    LEGS,
    // 조향 (MDPS)
    ARMS,
    // 타이어 공기압 경고등 + 타이어 소모품
    FEET,
    // 12V 배터리
    ENERGY,
    // 에어백
    ARMOR,
    // 엔진오일 소모품
    STOMACH,
    // 와이퍼 소모품
    EYES,
}

// 국가건강검진 결과지처럼 세 단계. 고장을 단정하지 않고 "점검 권장"까지만 (안전 규칙)
enum class CheckupLevel { NORMAL, WATCH, CHECK }

data class CheckupItem(val part: BodyPart, val level: CheckupLevel)

// 캐릭터가 결과지 옆에서 하는 말의 종류
enum class CheckupMood { GOOD, TIRED, HUNGRY, SICK }

data class HealthCheckup(
    val month: YearMonth,
    val items: List<CheckupItem>,
    // '관리 잘 받은 차' 배지
    val wellMaintained: Boolean,
) {
    val worstLevel: CheckupLevel get() = items.maxOf { it.level }

    // 점검 권장이 배(엔진오일)뿐이면 배고픔(다른 부위는 관찰 필요까지 허용),
    // 다른 부위에 점검 권장이 있으면 아픔, 관찰 필요만 있으면 피곤
    val mood: CheckupMood get() {
        val flagged = items.filter { it.level != CheckupLevel.NORMAL }
        val checks = flagged.filter { it.level == CheckupLevel.CHECK }
        return when {
            flagged.isEmpty() -> CheckupMood.GOOD
            checks.isNotEmpty() && checks.all { it.part == BodyPart.STOMACH } -> CheckupMood.HUNGRY
            checks.isNotEmpty() -> CheckupMood.SICK
            else -> CheckupMood.TIRED
        }
    }
}

// 건강검진 기준값 (앱 기본값). 수치는 여기서만 바꾼다.
object CheckupRules {
    // 배지: 점검 권장 0개, 관찰 필요 이 개수 이하
    const val BADGE_MAX_WATCH = 1
}

// 경고등과 소모품 상태로 부위별 단계를 정한다.
// 지금 켜진 경고등 → 점검 권장, 이번 달에 켜졌다 꺼진 경고등 → 관찰 필요,
// 소모품 '주의' → 점검 권장, '예고' → 관찰 필요.
object HealthCheckupBuilder {

    private val warningParts: Map<VehicleWarning, BodyPart> = mapOf(
        VehicleWarning.ENGINE to BodyPart.HEART,
        VehicleWarning.ENGINE_TEMPERATURE to BodyPart.HEART,
        VehicleWarning.ABS to BodyPart.LEGS,
        VehicleWarning.ELECTRONIC_PARKING_BRAKE to BodyPart.LEGS,
        VehicleWarning.MDPS to BodyPart.ARMS,
        VehicleWarning.TPMS to BodyPart.FEET,
        VehicleWarning.LOW_VOLT_BATTERY to BodyPart.ENERGY,
        VehicleWarning.AIRBAG to BodyPart.ARMOR,
    )

    private val consumableParts: Map<Consumable, BodyPart> = mapOf(
        Consumable.ENGINE_OIL to BodyPart.STOMACH,
        Consumable.TIRE to BodyPart.FEET,
        Consumable.WIPER to BodyPart.EYES,
        // 브레이크 패드는 다리, 공조 필터는 부위 없음(리포트에서만)
        Consumable.BRAKE_PAD to BodyPart.LEGS,
    )

    fun build(
        warnings: Set<VehicleWarning>,
        consumables: List<ConsumableStatus>,
        month: YearMonth,
        // 이번 달에 한 번이라도 켜졌던 경고등 (지금은 꺼져 있어도 관찰 필요)
        pastWarnings: Set<VehicleWarning> = emptySet(),
    ): HealthCheckup {
        val levels = BodyPart.entries.associateWith { CheckupLevel.NORMAL }.toMutableMap()
        fun raise(part: BodyPart, level: CheckupLevel) {
            if (level > levels.getValue(part)) levels[part] = level
        }
        pastWarnings.forEach { warning -> warningParts[warning]?.let { raise(it, CheckupLevel.WATCH) } }
        warnings.forEach { warning -> warningParts[warning]?.let { raise(it, CheckupLevel.CHECK) } }
        consumables.forEach { status ->
            val part = consumableParts[status.consumable] ?: return@forEach
            when (status.level) {
                ConditionLevel.CAUTION -> raise(part, CheckupLevel.CHECK)
                ConditionLevel.NOTICE -> raise(part, CheckupLevel.WATCH)
                ConditionLevel.NORMAL -> Unit
            }
        }
        val items = BodyPart.entries.map { CheckupItem(it, levels.getValue(it)) }
        val watch = items.count { it.level == CheckupLevel.WATCH }
        val check = items.count { it.level == CheckupLevel.CHECK }
        return HealthCheckup(
            month = month,
            items = items,
            wellMaintained = check == 0 && watch <= CheckupRules.BADGE_MAX_WATCH,
        )
    }
}
