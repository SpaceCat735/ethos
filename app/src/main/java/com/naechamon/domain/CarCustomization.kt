package com.naechamon.domain

import java.time.LocalDate

// 내 차 꾸미기 (기획안 3-6). 꾸미기는 "정비 기록의 영수증"이다.
// 보상 항목은 정비·점검 기록으로만 열리고, 주행거리로는 열리지 않는다 (더 많이 달리라고 유도하지 않음).
// 화면(3D 차고)은 여기서 정한 항목 id·잠김 여부만 받아서 그린다.

enum class FuelKind { GAS, HYBRID, EV }

// 판정할 데이터가 아직 앱에 없는 보상 (PT 단계에서 연결). 지금은 잠김 + 얻는 방법만 보여준다
enum class PendingReason {
    CHECKUP_NORMAL_MONTHS,
    NO_HARD_BRAKE_PARKS,
    NO_HARD_ACCEL_PARKS,
    EARLY_SERVICE,
    CHECK_AFTER_WARNING,
    FAMILY_STAMPS,
}

// 항목을 얻는 방법
sealed interface Unlock {
    // 처음 등록 때 내 차를 닮게 고르는 항목
    data object Free : Unlock

    // 해당 소모품 교체를 앱에 기록하면 열림
    data class Serviced(val consumable: Consumable) : Unlock

    // 정비 기록 n회 (소모품 종류 무관)
    data class ServiceCount(val count: Int) : Unlock

    data class Pending(val reason: PendingReason) : Unlock
}

data class CustomOption(val id: String, val unlock: Unlock = Unlock.Free)

private fun free(vararg ids: String) = ids.map { CustomOption(it) }
private fun locked(unlock: Unlock, vararg ids: String) = ids.map { CustomOption(it, unlock) }

// 차체 색 id (3D 화면이 색 값을 정한다)
private val COLOR_IDS = arrayOf(
    "white", "cream_pearl", "silver", "cyber_gray", "amazon_gray", "eco_gray", "black_pearl",
    "lucid_blue", "teal_green", "mirage_green", "ultimate_red", "gravity_gold", "nocturne_gray", "forest",
    "sunset_orange", "sun_yellow", "sky_blue", "mint", "lavender", "rose", "burgundy", "olive",
    "sand", "copper", "deep_purple", "ivory_matte", "navy", "graphite",
)

private val PART_COLORS = free("base", "body", "black", "white", "silver", "chrome", "carbon")

// 꾸미기 칸. key는 3D 화면과 주고받는 이름. 기본값(defaultId, 따로 안 정하면 첫 항목)은 항상 무료다
// customColor: 팔레트로 직접 고른 색(#RRGGBB)을 받는 칸이면 그 색을 여는 조건, 받지 않으면 null
enum class CustomSlot(
    val key: String,
    val options: List<CustomOption>,
    val defaultId: String = options.first().id,
    val customColor: Unlock? = null,
) {
    // 경차·승용 소/중/대·SUV 소/중/대. 특정 실차·브랜드를 닮지 않은 차급 일반형이고, 트럭·화물은 없다
    SHAPE("shape", free("mini", "sedan_s", "sedan_m", "sedan_l", "suv_s", "suv_m", "suv_l"), defaultId = "sedan_m"),
    FUEL("fuel", free("gas", "hybrid", "ev")),
    // 기본 흰색: 국내·세계 모두 가장 많이 타는 차 색 (2026-11-07 팀 결정)
    COLOR("color", free(*COLOR_IDS), defaultId = "white", customColor = Unlock.Free),
    ROOF(
        "roof",
        free("none") + locked(Unlock.ServiceCount(CustomizationRules.ROOF_SERVICE_COUNT), *COLOR_IDS),
        customColor = Unlock.ServiceCount(CustomizationRules.ROOF_SERVICE_COUNT),
    ),
    FINISH("finish", free("gloss") + locked(Unlock.ServiceCount(CustomizationRules.FINISH_SERVICE_COUNT), "matte", "metal", "pearl", "satin")),
    // 옆면 데칼(기본 제공 그림). 처음부터 고를 수 있다
    DECAL("decal", free("none", "sweep", "roundel", "geo", "topo", "wave", "dots")),
    STRIPE("stripe", free("none") + locked(Unlock.Pending(PendingReason.NO_HARD_BRAKE_PARKS), "center", "side")),
    // 음표·구름은 처음부터, 체크 깃발·발자국은 정비 기록 1번이면 열린다
    STICKER(
        "sticker",
        free("none", "note", "cloud") +
            locked(Unlock.ServiceCount(CustomizationRules.STICKER_SERVICE_COUNT), "checker", "paw") +
            locked(Unlock.Pending(PendingReason.CHECKUP_NORMAL_MONTHS), "star", "heart", "bolt", "leaf"),
    ),
    // 엔진오일 교체를 기록하면 반짝 휠
    // 타이어 교체를 기록하면 터빈·5스포크 휠
    WHEEL_STYLE(
        "wheelStyle",
        free("aero", "cross", "mesh") +
            locked(Unlock.Serviced(Consumable.ENGINE_OIL), "star", "flower", "solid") +
            locked(Unlock.Serviced(Consumable.TIRE), "turbine", "five"),
    ),
    WHEEL_COLOR("wheelColor", free("silver", "black", "gold", "body", "gunmetal", "bronze"), customColor = Unlock.Free),
    // 전조등·후미등 모양 (전조등 기본 슬림 2단, 후미등 기본 분리형). 특정 브랜드 시그니처를 닮지 않은 일반형만 둔다. 처음부터 고를 수 있다
    // 원형 단등·사각 단등·쿼드 사각·세로 스택·팝업·물방울 커버·멀티 렌즈·리플렉터·프로젝터 하우징은 실제로 쓰였던 연대별 대표 유형이다 (근거: 2026-11-07 팀 조사)
    HEAD_LAMP("headLamp", free("slim", "bar", "round", "slant", "single", "rect", "quad", "stack", "popup", "drop", "multi", "reflector", "projector")),
    // 후미등: 칸나눔·통패널·감싸기·범퍼형·투명 렌즈·스모크·블레이드는 실제로 쓰였던 유형 (근거: 2026-11-07 팀 조사)
    TAIL_LAMP("tailLamp", free("split", "bar", "vertical", "round", "dual", "band", "panel", "wrap", "bumper", "clear", "smoke", "blade")),
    LAMP("lamp", free("warm", "white", "blue")),
    WHEEL_SIZE("wheelSize", free("m", "s", "l")),
    HEAD(
        "head",
        free("none") +
            locked(Unlock.Serviced(Consumable.AC_FILTER), "sprout") +
            locked(Unlock.Pending(PendingReason.EARLY_SERVICE), "ribbon") +
            locked(Unlock.Pending(PendingReason.CHECK_AFTER_WARNING), "antenna") +
            locked(Unlock.Pending(PendingReason.CHECKUP_NORMAL_MONTHS), "crown") +
            locked(Unlock.Serviced(Consumable.WIPER), "pennant") +
            locked(Unlock.ServiceCount(CustomizationRules.STAR_PIN_SERVICE_COUNT), "starpin") +
            locked(Unlock.Serviced(Consumable.BRAKE_PAD), "beacon"),
    ),
    ROOF_ACC(
        "roofAcc",
        free("none") +
            locked(Unlock.Pending(PendingReason.FAMILY_STAMPS), "box", "rack", "surf") +
            locked(Unlock.Serviced(Consumable.TIRE), "bike") +
            locked(Unlock.ServiceCount(CustomizationRules.KAYAK_SERVICE_COUNT), "kayak") +
            locked(Unlock.ServiceCount(CustomizationRules.TENT_SERVICE_COUNT), "tent"),
    ),
    REAR(
        "rear",
        free("none", "carrier") +
            locked(Unlock.Pending(PendingReason.NO_HARD_ACCEL_PARKS), "spoiler") +
            locked(Unlock.Serviced(Consumable.ENGINE_OIL), "ducktail") +
            locked(Unlock.ServiceCount(CustomizationRules.WING_SERVICE_COUNT), "wing"),
    ),
    GLASSES("glasses", free("none", "sun", "round", "goggle", "square", "half")),
    BLUSH("blush", free("off", "on")),

    // ── 외관 튜닝 (실제 에어로 파츠 유형 조사 기반, 근거: 2026-11-07 팀 조사) ──
    // 철제 범퍼가드·네온·지붕 라이트바·앞유리 틴팅·번호판 가림은 불법 구조변경 단속 대상이라 넣지 않는다.
    // 차급에 안 맞는 선택(예: 승용의 사이드 스텝)은 3D 화면이 비슷한 부품으로 바꿔 보여 준다
    FRONT_BUMPER(
        "frontBumper",
        free("base", "lip") +
            locked(Unlock.ServiceCount(1), "splitter") +
            locked(Unlock.ServiceCount(2), "outdoor"),
    ),
    REAR_BUMPER(
        "rearBumper",
        free("base", "diffuser") +
            locked(Unlock.ServiceCount(2), "skid") +
            locked(Unlock.Serviced(Consumable.BRAKE_PAD), "towhook"),
    ),
    // 배기 팁은 범퍼 면 안쪽에만 (밖으로 튀어나오면 불법). 전기차는 3D 화면이 그리지 않는다
    EXHAUST(
        "exhaust",
        free("base", "single", "dual") +
            locked(Unlock.ServiceCount(1), "square") +
            locked(Unlock.Serviced(Consumable.ENGINE_OIL), "center"),
    ),
    SIDE("side", free("base", "skirt", "molding") + locked(Unlock.ServiceCount(2), "step")),
    GRILLE(
        "grille",
        free("base", "mesh", "horiz") +
            locked(Unlock.ServiceCount(1), "vert") +
            locked(Unlock.Serviced(Consumable.AC_FILTER), "panel"),
    ),
    HOOD(
        "hood",
        free("base", "vent") +
            locked(Unlock.ServiceCount(3), "scoop") +
            locked(Unlock.ServiceCount(4), "carbon"),
    ),
    FENDER(
        "fender",
        free("base", "molding") +
            locked(Unlock.Serviced(Consumable.TIRE), "mudguard") +
            locked(Unlock.ServiceCount(3), "over"),
    ),
    // 미러·손잡이·창문 몰딩 색
    TRIM(
        "trim",
        free("base", "black", "chrome") +
            locked(Unlock.ServiceCount(1), "body") +
            locked(Unlock.ServiceCount(4), "carbon"),
    ),
    CALIPER(
        "caliper",
        free("base", "red") + locked(Unlock.Serviced(Consumable.BRAKE_PAD), "yellow", "blue"),
        customColor = Unlock.Serviced(Consumable.BRAKE_PAD),
    ),
    TIRE_LETTER("tireLetter", free("off", "on")),
    ROOF_TOP("roofTop", free("base", "rail") + locked(Unlock.ServiceCount(2), "panorama")),

    // ── 조사 기반 추가 (2026-11-07 팀 조사) ──
    WHEEL_FINISH("wheelFinish", free("gloss", "matte", "polished", "twotone")),
    // 옆·뒤 유리 색. 앞유리(표정)는 바꾸지 않고, 기본보다 진하게 하는 선택지는 두지 않는다 (틴팅 투과율 규정)
    GLASS_TINT("glassTint", free("base", "light", "bronze")),
    ANTENNA("antenna", free("none", "shark", "rod")),
    // 차고는 기본에서 한 단계씩만 (과도한 차고 변경은 구조변경 승인 대상)
    RIDE_HEIGHT("rideHeight", free("base", "low", "high")),
    // 램프 안쪽 테두리만 검게. 렌즈 착색·필름(스모크)은 불법이라 두지 않는다
    LAMP_HOUSING("lampHousing", free("base", "black")),

    // ── 부품별 색: 기본(원래 색)·차체색·검정·흰색·은색·크롬·카본 + 팔레트 ──
    MIRROR_COLOR("mirrorColor", PART_COLORS, customColor = Unlock.Free),
    FRONT_PART_COLOR("frontPartColor", PART_COLORS, customColor = Unlock.Free),
    SIDE_PART_COLOR("sidePartColor", PART_COLORS, customColor = Unlock.Free),
    REAR_PART_COLOR("rearPartColor", PART_COLORS, customColor = Unlock.Free),
    FENDER_PART_COLOR("fenderPartColor", PART_COLORS, customColor = Unlock.Free),
    HOOD_PART_COLOR("hoodPartColor", PART_COLORS, customColor = Unlock.Free),
    GRILLE_COLOR("grilleColor", PART_COLORS, customColor = Unlock.Free),
    // 줄무늬·옆 데칼 색 (기본 흰색)
    STRIPE_COLOR("stripeColor", free("base", "black", "silver", "red", "yellow", "blue", "body"), customColor = Unlock.Free),
    ;

    fun option(id: String): CustomOption? = options.firstOrNull { it.id == id }
}

// 앱에 기록한 소모품 교체 (기록한 순서대로)
data class ServiceHistory(val serviced: List<Consumable> = emptyList())

// 사용자가 고른 내 차. choices에 없는 칸은 기본값
data class CarCustomization(
    val name: String,
    val plate: String,
    val choices: Map<CustomSlot, String>,
) {
    fun choice(slot: CustomSlot): String = choices[slot] ?: slot.defaultId
}

// 꾸미기 보상의 근거가 되는 정비 기록을 남길지. 정상 상태에서 누르거나 같은 소모품을 하루에 여러 번 누르면 남기지 않는다
// ("정비 완료 기록"을 반복해서 눌러 정비 횟수 보상을 한꺼번에 여는 것을 막는다. 꾸미기는 정비 기록의 영수증)
object ServiceRewardRules {
    fun shouldRecord(level: ConditionLevel, lastRecordedOn: LocalDate?, today: LocalDate): Boolean =
        level != ConditionLevel.NORMAL && lastRecordedOn != today
}

object CustomizationRules {
    const val NAME_MAX_LENGTH = 10
    const val FINISH_SERVICE_COUNT = 2
    const val ROOF_SERVICE_COUNT = 3
    const val STICKER_SERVICE_COUNT = 1
    const val STAR_PIN_SERVICE_COUNT = 2
    const val KAYAK_SERVICE_COUNT = 3
    const val WING_SERVICE_COUNT = 3
    const val TENT_SERVICE_COUNT = 4

    fun defaults(fuel: FuelKind, name: String, plate: String): CarCustomization =
        CarCustomization(name, plate, mapOf(CustomSlot.FUEL to fuel.id()))

    fun isUnlocked(option: CustomOption, history: ServiceHistory): Boolean = when (val u = option.unlock) {
        Unlock.Free -> true
        is Unlock.Serviced -> u.consumable in history.serviced
        is Unlock.ServiceCount -> history.serviced.size >= u.count
        is Unlock.Pending -> false
    }

    // 저장값·화면에서 온 값을 믿지 않는다: 모르는 id나 아직 잠긴 항목은 기본값으로, 이름은 길이 제한
    fun sanitize(custom: CarCustomization, history: ServiceHistory, fallbackName: String): CarCustomization =
        CarCustomization(
            name = custom.name.trim().take(NAME_MAX_LENGTH).ifEmpty { fallbackName },
            plate = custom.plate.trim().take(NAME_MAX_LENGTH),
            choices = CustomSlot.entries.associateWith { slot ->
                // 이전 버전에서 저장한 id는 가장 가까운 새 id로 옮긴다 (업데이트해도 고른 색·차급이 기본값으로 바뀌지 않게)
                val id = custom.choice(slot).let { LEGACY_IDS[slot]?.get(it) ?: it }
                val option = slot.option(id)
                val custom = customColorOf(slot, id)
                when {
                    option != null && isUnlocked(option, history) -> option.id
                    custom != null && isUnlocked(CustomOption(custom, slot.customColor!!), history) -> custom
                    else -> slot.defaultId
                }
            },
        )

    // 팔레트로 고른 색: 이 칸이 받는 칸이고 #RRGGBB 꼴이면 대문자로 맞춰 돌려준다 (그 밖의 값은 null)
    fun customColorOf(slot: CustomSlot, value: String): String? =
        if (slot.customColor != null && HEX_COLOR.matches(value)) value.uppercase() else null

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    // 이전 버전 id → 지금 id. 옛 색 10종(민트·흰색은 그대로)과 옛 차급 10종을 새 28색·7차급으로 옮긴다
    private val LEGACY_COLORS = mapOf(
        "black" to "black_pearl", "gray" to "cyber_gray", "red" to "ultimate_red", "blue" to "lucid_blue",
        "yellow" to "sun_yellow", "pink" to "rose", "orange" to "sunset_orange", "purple" to "deep_purple",
    )
    private val LEGACY_IDS: Map<CustomSlot, Map<String, String>> = mapOf(
        CustomSlot.COLOR to LEGACY_COLORS,
        CustomSlot.ROOF to LEGACY_COLORS,
        CustomSlot.SHAPE to mapOf(
            "hatch" to "sedan_s", "bubble" to "mini", "sedan" to "sedan_m", "coupe" to "sedan_m",
            "suvS" to "suv_s", "suvL" to "suv_l", "van" to "suv_l", "pickup" to "suv_l", "truck" to "suv_l",
        ),
    )

    // 차BTI → 종족 id (꾸미기 화면 제목 글자용. 3D 장식은 붙이지 않는다). 판정 전(데이터 부족)은 알
    fun speciesOf(carBti: CarBtiType?): String = when (carBti) {
        null -> "egg"
        CarBtiType.SQUIRREL -> "squirrel"
        CarBtiType.TURTLE -> "turtle"
        CarBtiType.MIGRATORY_BIRD -> "bird"
        CarBtiType.WEEKEND_TRAVELER -> "picnic"
    }

    private fun FuelKind.id(): String = when (this) {
        FuelKind.GAS -> "gas"
        FuelKind.HYBRID -> "hybrid"
        FuelKind.EV -> "ev"
    }
}
