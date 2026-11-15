package com.naechamon

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.naechamon.data.CustomizationStore
import com.naechamon.data.DataSourceProvider
import com.naechamon.domain.CareGrade
import com.naechamon.domain.CareStamp
import com.naechamon.domain.ConditionLevel
import com.naechamon.domain.StarterShape
import com.naechamon.ui.GarageScreen
import com.naechamon.ui.LocalMyCar
import androidx.compose.foundation.isSystemInDarkTheme
import com.naechamon.ui.loadMyCarImages
import com.naechamon.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 개발용: adb shell am start -n com.naechamon/.MainActivity --es scenario demo --ei speed 5 [--ez checkup true]
        val scenarioName = intent.getStringExtra(EXTRA_SCENARIO)
        val playbackSpeed = intent.getIntExtra(EXTRA_SPEED, 1).coerceAtLeast(1).toFloat()
        // 데모용: --ez checkup true 면 정차 시 건강검진표부터 보인다
        val openCheckup = intent.getBooleanExtra(EXTRA_CHECKUP, false)
        // 실제 차량 연결: --es source pleos (없으면 지금처럼 시나리오 모드)
        val source = intent.getStringExtra(EXTRA_SOURCE)
        val isPleos = DataSourceProvider.isPleos(source)

        // 주행 기록은 화면 밖(DrivingSessions)에 있다. 창 크기 변경으로 화면이 다시 만들어져도 기록은 이어진다.
        // 시나리오나 실차 모드를 지정해 새로 실행한 경우에만 처음부터 다시 시작한다
        val running = DrivingSessions.obtain(
            context = this,
            scenarioName = scenarioName,
            playbackSpeed = playbackSpeed,
            restart = savedInstanceState == null && (scenarioName != null || isPleos),
            source = source,
        )
        // 실차 모드에서만 차속(CAR_SPEED) 등 퍼미션을 요청한다. 거부해도 속도 0·제한 중으로 계속 동작한다
        if (isPleos && savedInstanceState == null) requestPleosPermissions()
        // 내비를 띄워도 기록이 끊기지 않게 기록 서비스를 켠다
        DrivingRecordService.start(this)

        // 내 차 꾸미기 저장소 (기기 안에만 저장)
        val customization = CustomizationStore(this)

        setContent {
            val screen by running.screens.collectAsState()
            // 차의 낮/밤 설정에 맞는 캐릭터 그림 (밤에는 전조등·후미등이 켜진 그림)
            val dark = isSystemInDarkTheme()
            var myCar by remember(dark) { mutableStateOf(loadMyCarImages(this@MainActivity, customization, dark)) }
            // 첫 실행 차급 고르기를 마쳤는지 (기기 안에 저장)
            var shapePicked by remember { mutableStateOf(customization.isShapePicked()) }
            // 정비 수첩 (도장·관리 등급)과 방금 찍힌 도장 연출
            var careBook by remember { mutableStateOf(customization.careBook()) }
            var stampFlash by remember { mutableStateOf<Pair<CareStamp, CareGrade?>?>(null) }
            CompositionLocalProvider(LocalMyCar provides myCar) {
                MainScreen(
                    mode = screen.mode,
                    appearance = screen.appearance,
                    todayDistanceKm = screen.todayDistanceKm,
                    report = screen.report,
                    checkup = screen.checkup,
                    greeting = screen.greeting,
                    openCheckupAtStart = openCheckup,
                    onServiceDone = { consumable ->
                        // 정비 기록은 꾸미기 보상의 근거 (주행거리로는 보상 없음). 교체 시기가 아닌데 누르거나 같은 날 반복하면 보상 기록은 남지 않는다
                        val level = screen.report.consumables.firstOrNull { it.consumable == consumable }?.level ?: ConditionLevel.NORMAL
                        val before = careBook
                        if (customization.recordService(consumable, level, java.time.LocalDate.now())) {
                            careBook = customization.careBook()
                            stampFlash = careBook.newestFirst.first() to before.gradeAfterOneMore()
                        }
                        running.session.markServiced(consumable, screen.report.odometerKm)
                    },
                    careBook = careBook,
                    stampFlash = stampFlash,
                    onStampShown = { stampFlash = null },
                    garage = { starter, onClose ->
                        GarageScreen(
                            store = customization,
                            expression = screen.appearance.expression,
                            carBti = screen.report.carBti,
                            onClose = onClose,
                            onSaved = {
                                myCar = loadMyCarImages(this@MainActivity, customization, dark)
                                // 첫 실행 차급 고르기는 고른 차의 그림까지 다 저장됐을 때만 마친 것으로 본다.
                                // 중간에 끊기면(주행 시작·뒤로 가기) 저장된 것이 없으므로 다음 정차 때 다시 묻는다
                                if (starter != null) {
                                    customization.markShapePicked()
                                    shapePicked = true
                                }
                            },
                            starterShapeId = starter?.let { StarterShape.shapeIdFor(it) },
                        )
                    },
                    askStarterShape = !shapePicked,
                    onStarterShape = { choice ->
                        // "나중에"(null)는 기본 차종(승용 중형) 그대로 두고 고르기만 마친다. 고른 차종은 차고 저장이 끝날 때 반영된다
                        if (choice == null) {
                            customization.markShapePicked()
                            shapePicked = true
                        }
                    },
                )
            }
        }
    }

    // 데이터 소스가 퍼미션 허용을 스스로 기다리므로 결과는 기록만 한다
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Log.i(TAG, "실차 퍼미션 결과: $result")
    }

    private fun requestPleosPermissions() {
        val missing = DataSourceProvider.pleosRuntimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private companion object {
        const val TAG = "MainActivity"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_CHECKUP = "checkup"
    }
}
