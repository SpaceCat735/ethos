package com.naechamon.data

import android.content.Context
import com.naechamon.R
import com.naechamon.domain.CarCustomization
import com.naechamon.domain.CareBook
import com.naechamon.domain.CareStamp
import com.naechamon.domain.ConditionLevel
import com.naechamon.domain.Consumable
import com.naechamon.domain.CustomSlot
import com.naechamon.domain.CustomizationRules
import com.naechamon.domain.ServiceHistory
import com.naechamon.domain.ServiceRewardRules
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 내 차 꾸미기 저장소. 기기 안(SharedPreferences, 앱 전용 폴더)에만 둔다. 밖으로 보내지 않는다.
// 꾸미기 선택, 앱에서 기록한 소모품 교체(보상 판정용), 표정별 내 차 정지 그림(PNG)을 저장한다.
class CustomizationStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val snapshotDir = File(appContext.filesDir, SNAPSHOT_DIR)
    private val stagingDir = File(appContext.filesDir, SNAPSHOT_DIR + "_staging")
    private val defaultName: String get() = appContext.getString(R.string.garage_default_name)

    fun serviceHistory(): ServiceHistory = ServiceHistory(
        prefs.getString(KEY_SERVICED, "").orEmpty()
            .split(',')
            .mapNotNull { name -> Consumable.entries.firstOrNull { it.name == name } },
    )

    // 정비 완료 기록 → 꾸미기 보상 근거. 두뇌 층 규칙(ServiceRewardRules)이 허락할 때만 남긴다. 남겼으면 true
    fun recordService(consumable: Consumable, level: ConditionLevel, today: LocalDate): Boolean {
        val last = prefs.getString(KEY_SERVICED_ON + consumable.name, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (!ServiceRewardRules.shouldRecord(level, last, today)) return false
        val serviced = serviceHistory().serviced + consumable
        val stamps = prefs.getString(KEY_STAMPS, "").orEmpty().split(",").filter { it.isNotBlank() } + "${consumable.name}@$today"
        prefs.edit()
            .putString(KEY_SERVICED, serviced.joinToString(",") { it.name })
            .putString(KEY_SERVICED_ON + consumable.name, today.toString())
            .putString(KEY_STAMPS, stamps.joinToString(","))
            .apply()
        return true
    }

    // 정비 수첩: 보상 기록 하나 = 도장 하나. 날짜를 남기기 전(이전 버전)의 기록은 날짜 없는 도장으로 채운다
    fun careBook(): CareBook {
        val dated = prefs.getString(KEY_STAMPS, "").orEmpty().split(",").mapNotNull { entry ->
            val (name, date) = entry.split("@").takeIf { it.size == 2 } ?: return@mapNotNull null
            val consumable = Consumable.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            CareStamp(consumable, runCatching { LocalDate.parse(date) }.getOrNull())
        }
        val all = serviceHistory().serviced
        val undated = all.size - dated.size
        val legacy = if (undated > 0) all.take(undated).map { CareStamp(it, null) } else emptyList()
        return CareBook(legacy + dated)
    }

    fun load(): CarCustomization {
        val defaults = CustomizationRules.defaults(DemoVehicleProfile.FUEL, defaultName, appContext.getString(R.string.garage_default_plate))
        val text = prefs.getString(KEY_CUSTOM, null) ?: return defaults
        val parsed = runCatching { fromFlatJson(text) }.getOrNull() ?: return defaults
        return CustomizationRules.sanitize(parsed, serviceHistory(), defaultName)
    }

    // 3D 화면이 넘긴 값을 그대로 믿지 않고 두뇌 층 규칙으로 걸러서 저장한다
    fun saveFromPage(flatJson: String): CarCustomization? {
        val parsed = runCatching { fromFlatJson(flatJson) }.getOrNull() ?: return null
        val clean = CustomizationRules.sanitize(parsed, serviceHistory(), defaultName)
        prefs.edit().putString(KEY_CUSTOM, toFlatJson(clean).toString()).apply()
        return clean
    }

    // 첫 실행 차급 고르기에서 고른 차종만 바꾼 꾸미기 (저장은 하지 않는다. 차고가 정지 그림까지 다 찍은 뒤 commit에서 저장)
    fun withShape(shapeId: String): CarCustomization {
        val current = load()
        return CustomizationRules.sanitize(
            current.copy(choices = current.choices + (CustomSlot.SHAPE to shapeId)),
            serviceHistory(),
            defaultName,
        )
    }

    // 차고 저장은 한 번에: 선택과 표정별 그림을 임시 폴더에 모았다가 다 찍었을 때만 바꾼다.
    // 중간에 끊기면(주행 시작·뒤로 가기·오류) 이전 선택·그림이 그대로 남아 표정마다 다른 차가 섞이지 않는다
    fun beginSnapshots() {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
    }

    fun writeStagedSnapshot(faceKey: String, png: ByteArray) {
        stagingDir.mkdirs()
        File(stagingDir, "$faceKey.png").writeBytes(png)
    }

    // 임시 폴더의 그림 수가 expected와 같을 때만 선택을 저장하고 그림을 옮긴다. 성공하면 true
    fun commit(flatJson: String, expected: Int): Boolean {
        val staged = stagingDir.listFiles { f -> f.name.endsWith(".png") }.orEmpty()
        if (staged.size != expected || saveFromPage(flatJson) == null) {
            stagingDir.deleteRecursively()
            return false
        }
        snapshotDir.mkdirs()
        staged.forEach { src ->
            val dst = File(snapshotDir, src.name)
            if (!src.renameTo(dst)) { src.copyTo(dst, overwrite = true); src.delete() }
        }
        stagingDir.deleteRecursively()
        return true
    }

    // 첫 실행 차급 고르기를 마쳤는지 ("나중에"를 눌러도 마친 것으로 본다)
    fun isShapePicked(): Boolean = prefs.getBoolean(KEY_SHAPE_PICKED, false)

    fun markShapePicked() {
        prefs.edit().putBoolean(KEY_SHAPE_PICKED, true).apply()
    }

    fun snapshotFile(faceKey: String): File = File(snapshotDir, "$faceKey.png")

    companion object {
        private const val PREFS_NAME = "my_car"
        private const val KEY_CUSTOM = "customization"
        private const val KEY_SERVICED = "serviced"
        // 소모품별 마지막 보상 기록 날짜 (같은 날 반복 기록 막기)
        private const val KEY_SERVICED_ON = "serviced_on_"
        // 정비 수첩 도장 (소모품@날짜, 쉼표로 이음)
        private const val KEY_STAMPS = "care_stamps"
        private const val KEY_SHAPE_PICKED = "shape_picked"
        // 차 모양이 바뀌면 폴더 이름을 올려 옛 정지 그림(이전 차고의 차)을 쓰지 않게 한다
        private const val SNAPSHOT_DIR = "my_car_v4"
        private const val KEY_NAME = "name"
        private const val KEY_PLATE = "plate"

        // 3D 화면과 주고받는 모양: {"name":..,"plate":..,"shape":"mini",...} (칸 key → 항목 id)
        fun toFlatJson(custom: CarCustomization): JsonObject = JsonObject(
            mapOf(KEY_NAME to JsonPrimitive(custom.name), KEY_PLATE to JsonPrimitive(custom.plate)) +
                CustomSlot.entries.associate { it.key to JsonPrimitive(custom.choice(it)) },
        )

        private fun fromFlatJson(text: String): CarCustomization {
            val obj = Json.parseToJsonElement(text).jsonObject
            fun str(key: String): String? = runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()
            return CarCustomization(
                name = str(KEY_NAME).orEmpty(),
                plate = str(KEY_PLATE).orEmpty(),
                choices = CustomSlot.entries.mapNotNull { slot -> str(slot.key)?.let { slot to it } }.toMap(),
            )
        }
    }
}
