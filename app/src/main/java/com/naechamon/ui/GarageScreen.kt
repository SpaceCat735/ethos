package com.naechamon.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naechamon.R
import com.naechamon.data.CustomizationStore
import com.naechamon.domain.CarBtiType
import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.CustomOption
import com.naechamon.domain.CustomSlot
import com.naechamon.domain.CustomizationRules
import com.naechamon.domain.Unlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// 내 차 꾸미기 (3D 차고). 정차 중에만 MainScreen이 띄운다.
// 3D는 앱 안에 넣은 페이지(assets/garage, three.js + Blender로 내보낸 차종별 메시)를 WebView로 연다. 인터넷을 쓰지 않는다.
// 항목·잠김은 두뇌 층(CustomizationRules)이 정하고, 페이지는 그리기와 정지 그림 찍기만 한다.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GarageScreen(
    store: CustomizationStore,
    expression: CharacterExpression,
    carBti: CarBtiType?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    // 첫 실행 차급 고르기 뒤: 이 차종으로 바꾼 차를 페이지가 바로 찍어 저장하고 닫는다 (null = 보통 차고)
    starterShapeId: String? = null,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val initJson = remember(dark, starterShapeId) { garageInitJson(context, store, expression, carBti, dark, starterShapeId) }
    val close by rememberUpdatedState(onClose)
    val saved by rememberUpdatedState(onSaved)
    BackHandler(onBack = onClose)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // 높이를 내용에 맞추면(기본값) 페이지 높이가 0이 된다. 화면을 꽉 채운다
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                // 기기 파일·콘텐츠 접근은 막는다. assets(file:///android_asset)는 이 설정과 상관없이 열린다
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    // 페이지 밖으로 나가는 이동은 막는다
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
                // 페이지 오류를 logcat으로 (태그 NaechamonGarage)
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, "${message.messageLevel()} ${message.message()} (${message.lineNumber()})")
                        return true
                    }
                }
                addJavascriptInterface(
                    GarageBridge(store, initJson, onDone = { saved(); close() }, onClose = { close() }),
                    BRIDGE_NAME,
                )
                loadUrl(GARAGE_URL)
            }
        },
        onRelease = { web ->
            web.removeJavascriptInterface(BRIDGE_NAME)
            web.destroy()
        },
    )
}

// 표정별 내 차 그림을 읽는다. 아직 꾸미지 않았으면 기본 3D 차(승용 중형·딥 포레스트) 정지 그림을 쓴다.
// 기본 그림은 이 차고 페이지로 미리 찍어 둔 것이라 꾸민 차 그림과 모양·구도가 같다 (docs/character/README.md)
// 밤(dark)에는 전조등·후미등이 켜진 밤 그림을 쓴다. 꾸민 차의 밤 그림이 없으면(옛 저장) 낮 그림으로 대신한다
fun loadMyCarImages(context: Context, store: CustomizationStore, dark: Boolean): Map<CharacterExpression, ImageBitmap> =
    CharacterExpression.entries.mapNotNull { expression ->
        val day = store.snapshotFile(expression.faceKey())
        val night = store.snapshotFile(NIGHT_PREFIX + expression.faceKey())
        val file = if (dark && night.isFile) night else day
        val bitmap = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        (bitmap ?: BitmapFactory.decodeResource(context.resources, expression.defaultCar3dRes(dark)))
            ?.let { expression to it.asImageBitmap() }
    }.toMap()

private fun CharacterExpression.defaultCar3dRes(dark: Boolean): Int = if (dark) when (this) {
    CharacterExpression.NORMAL -> R.drawable.car3d_night_face_normal
    CharacterExpression.HAPPY -> R.drawable.car3d_night_face_happy
    CharacterExpression.SAD -> R.drawable.car3d_night_face_sad
    CharacterExpression.HURT -> R.drawable.car3d_night_face_hurt
    CharacterExpression.SLEEPY -> R.drawable.car3d_night_face_sleepy
    CharacterExpression.HUNGRY -> R.drawable.car3d_night_face_hungry
    CharacterExpression.BORED -> R.drawable.car3d_night_face_bored
    CharacterExpression.PROUD -> R.drawable.car3d_night_face_proud
} else when (this) {
    CharacterExpression.NORMAL -> R.drawable.car3d_face_normal
    CharacterExpression.HAPPY -> R.drawable.car3d_face_happy
    CharacterExpression.SAD -> R.drawable.car3d_face_sad
    CharacterExpression.HURT -> R.drawable.car3d_face_hurt
    CharacterExpression.SLEEPY -> R.drawable.car3d_face_sleepy
    CharacterExpression.HUNGRY -> R.drawable.car3d_face_hungry
    CharacterExpression.BORED -> R.drawable.car3d_face_bored
    CharacterExpression.PROUD -> R.drawable.car3d_face_proud
}

// 페이지에서 부르는 함수들. WebView 전용 스레드에서 불리므로 화면 콜백은 메인 스레드로 넘긴다
private class GarageBridge(
    private val store: CustomizationStore,
    private val initJson: String,
    private val onDone: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val faceKeys = CharacterExpression.entries.map { it.faceKey() }.toSet()

    @JavascriptInterface
    fun initialState(): String = initJson

    // 선택은 바로 저장하지 않고 들고 있다가, 그림을 다 찍은 뒤 done()에서 그림과 함께 한 번에 바꾼다
    @Volatile private var pendingJson: String? = null

    @JavascriptInterface
    fun save(flatJson: String) {
        pendingJson = flatJson
        store.beginSnapshots()
    }

    @JavascriptInterface
    fun saveSnapshot(faceKey: String, dataUrl: String) {
        // 정해진 표정 이름(낮) 또는 night_ + 표정 이름(밤)과 PNG만 받는다
        if (faceKey.removePrefix(NIGHT_PREFIX) !in faceKeys || !dataUrl.startsWith(PNG_PREFIX) || dataUrl.length > MAX_SNAPSHOT_CHARS) return
        val png = runCatching { Base64.decode(dataUrl.substring(PNG_PREFIX.length), Base64.DEFAULT) }.getOrNull() ?: return
        store.writeStagedSnapshot(faceKey, png)
    }

    @JavascriptInterface
    fun done() {
        // 낮·밤 그림이 표정 수만큼 다 모였을 때만 반영한다. 모자라면 이전 차 그대로 두고 닫는다
        val json = pendingJson
        pendingJson = null
        if (json == null) {
            main.post(onClose)
            return
        }
        val ok = runCatching { store.commit(json, faceKeys.size * 2) }
            .onFailure { Log.w(LOG_TAG, "저장 반영 실패", it) }
            .getOrDefault(false)
        main.post(if (ok) onDone else onClose)
    }

    @JavascriptInterface
    fun close() {
        main.post(onClose)
    }
}

// 페이지에 넘기는 값: 문구(strings.xml), 칸별 항목·잠김·얻는 방법, 저장된 선택, 지금 표정, 종족
private fun garageInitJson(
    context: Context,
    store: CustomizationStore,
    expression: CharacterExpression,
    carBti: CarBtiType?,
    // 차의 낮/밤 설정. 페이지 배경·글자색이 앱과 같은 테마를 따른다
    dark: Boolean,
    // 있으면 이 차종으로 바꾼 차를 열자마자 저장(정지 그림 찍기)까지 하고 done()을 부른다 (첫 실행 차급 고르기)
    starterShapeId: String?,
): String {
    val history = store.serviceHistory()
    fun str(name: String): String {
        @SuppressLint("DiscouragedApi")
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) "" else context.getString(id)
    }
    fun how(unlock: Unlock): String = when (unlock) {
        Unlock.Free -> ""
        is Unlock.Serviced -> context.getString(R.string.garage_how_serviced, str("consumable_" + unlock.consumable.name.lowercase()))
        is Unlock.ServiceCount -> context.getString(R.string.garage_how_service_count, unlock.count)
        is Unlock.Pending -> str("garage_how_" + unlock.reason.name.lowercase())
    }
    val slots = CustomSlot.entries.associate { slot ->
        val key = slot.key.lowercase()
        // 팔레트(직접 고른 색)를 받는 칸이면 잠김 여부와 여는 방법도 넘긴다
        val custom = slot.customColor?.let { unlock ->
            val locked = !CustomizationRules.isUnlocked(CustomOption("custom", unlock), history)
            JsonObject(mapOf("locked" to JsonPrimitive(locked), "how" to JsonPrimitive(if (locked) how(unlock) else "")))
        }
        slot.key to JsonObject(
            mapOf(
                "title" to JsonPrimitive(str("garage_slot_$key")),
                "custom" to (custom ?: JsonPrimitive(false)),
                "options" to JsonArray(
                    slot.options.map { option ->
                        val isColor = (slot == CustomSlot.COLOR || slot == CustomSlot.ROOF) && option.id != "none"
                        val label = if (isColor) str("garage_color_${option.id}") else str("garage_opt_${key}_${option.id.lowercase()}")
                        val locked = !CustomizationRules.isUnlocked(option, history)
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(option.id),
                                "label" to JsonPrimitive(label),
                                "locked" to JsonPrimitive(locked),
                                "how" to JsonPrimitive(if (locked) how(option.unlock) else ""),
                            ),
                        )
                    },
                ),
            ),
        )
    }
    val labelKeys = listOf(
        "tab_basic", "tab_paint", "tab_wheel", "tab_acc", "tab_tune", "tab_parts", "pick_color", "custom_color", "palette_title", "palette_light", "spin", "front", "side", "top",
        "hint", "save", "cancel", "saving", "note", "slot_name", "slot_plate",
    )
    val labels = labelKeys.associateWith { JsonPrimitive(str("garage_$it")) } + SPECIES_LABELS.mapKeys { "species_${it.key}" }
        .mapValues { JsonPrimitive(context.getString(it.value)) }
    return JsonObject(
        mapOf(
            "labels" to JsonObject(labels),
            "slots" to JsonObject(slots),
            "state" to CustomizationStore.toFlatJson(starterShapeId?.let { store.withShape(it) } ?: store.load()),
            "face" to JsonPrimitive(expression.faceKey()),
            "species" to JsonPrimitive(CustomizationRules.speciesOf(carBti)),
            "faces" to JsonArray(CharacterExpression.entries.map { JsonPrimitive(it.faceKey()) }),
            "nameMax" to JsonPrimitive(CustomizationRules.NAME_MAX_LENGTH),
            "dark" to JsonPrimitive(dark),
            "autoSave" to JsonPrimitive(starterShapeId != null),
        ),
    ).toString()
}

// 3D 종족 장식 id → 차BTI 이름 (리포트와 같은 문구)
private val SPECIES_LABELS = mapOf(
    "egg" to R.string.carbti_egg,
    "squirrel" to R.string.carbti_squirrel,
    "turtle" to R.string.carbti_turtle,
    "bird" to R.string.carbti_migratory_bird,
    "picnic" to R.string.carbti_weekend_traveler,
)

private const val LOG_TAG = "EthosGarage"
// 밤 그림(전조등·후미등 켜짐) 파일 이름 앞머리. 차고 페이지의 saveAll과 같은 값
private const val NIGHT_PREFIX = "night_"
private const val BRIDGE_NAME = "NaechamonApp"
private const val GARAGE_URL = "file:///android_asset/garage/index.html"
private const val PNG_PREFIX = "data:image/png;base64,"
// 640×640 PNG 한 장은 수백 KB. 터무니없이 큰 값은 받지 않는다
private const val MAX_SNAPSHOT_CHARS = 8_000_000
