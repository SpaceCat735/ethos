package com.naechamon

import android.content.Context
import android.os.SystemClock
import com.naechamon.data.DataSourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

// 앱 프로세스에 하나뿐인 주행 기록. Activity와 기록 서비스가 같은 기록을 본다.
object DrivingSessions {

    class Running(
        val session: DrivingSession,
        val screens: StateFlow<ScreenState>,
        val recording: Job,
    )

    // 기록과 화면 계산을 한 스레드에서만 돌린다
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private var current: Running? = null

    // 이미 기록 중이면 그 기록을 돌려준다. restart면 새 시나리오로 처음부터 (개발용 --es scenario 실행)
    // source가 "pleos"면 실제 차량 데이터, 아니면 시나리오 (기본값)
    @Synchronized
    fun obtain(
        context: Context,
        scenarioName: String? = null,
        playbackSpeed: Float = 1f,
        restart: Boolean = false,
        source: String? = null,
    ): Running {
        current?.let { running ->
            if (!restart) return running
            running.recording.cancel()
        }
        val isPleos = DataSourceProvider.isPleos(source)
        // 실제 차량은 배속이 없다
        val speed = if (isPleos) 1f else playbackSpeed
        val dataSource = if (isPleos) {
            DataSourceProvider.pleos(context.applicationContext)
        } else {
            DataSourceProvider.scenario(context.applicationContext, scenarioName, playbackSpeed)
        }
        // 기본 데모 시나리오는 알에서 시작해 첫 주행을 마치면 부화한다 (성장 시연). 다른 시나리오·실차는 지금처럼
        val eggStart = !isPleos && (scenarioName == null || scenarioName == DataSourceProvider.DEFAULT_SCENARIO)
        val session = DrivingSession(dataSource, speed, elapsedMillis = SystemClock::elapsedRealtime, eggStart = eggStart)
        return Running(session, session.screens(scope), session.record(scope)).also { current = it }
    }
}
