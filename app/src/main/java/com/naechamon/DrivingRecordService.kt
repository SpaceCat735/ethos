package com.naechamon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

// 내비가 화면을 쓰는 동안에도 주행 기록(급정거·주행거리·트립)이 끊기지 않게 앱 프로세스를 살려 두는 서비스.
// 기록 자체는 DrivingSessions가 한다. 이 서비스는 화면에 아무것도 그리지 않는다.
// 포그라운드 서비스는 알림이 꼭 있어야 해서 "주행 기록 중" 알림을 만든다 (소리·팝업 없음).
// 단, 알림 퍼미션(POST_NOTIFICATIONS)을 넣지 않았으므로 Android 13 이상에서는 알림 센터에 보이지 않는다.
// 기록은 알림이 안 보여도 그대로 이어진다 (2026-11-02 에뮬레이터 확인). 알림을 보이게 하려면 퍼미션 추가를 팀이 먼저 정한다.
class DrivingRecordService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // 화면 없이 서비스만 살아난 경우에도 기록을 이어 간다
        DrivingSessions.obtain(applicationContext)
        // 프로세스가 강제로 끝나면 다시 띄우지 않는다. 다음 시동 때 앱을 열면 다시 시작한다
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.record_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(getString(R.string.record_notification_title))
            .setContentText(getString(R.string.record_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "DrivingRecordService"
        private const val CHANNEL_ID = "driving_record"
        private const val NOTIFICATION_ID = 1

        // 앱 화면이 떠 있을 때(포그라운드) 부른다. 백그라운드에서 부르면 시스템이 막으므로 실패해도 앱은 계속 동작한다
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DrivingRecordService::class.java))
            }.onFailure { Log.w(TAG, "주행 기록 서비스를 시작하지 못했습니다. 화면이 떠 있는 동안만 기록합니다", it) }
        }
    }
}
