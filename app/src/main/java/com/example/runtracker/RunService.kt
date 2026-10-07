package com.example.runtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 위치 업데이트를 받는 포그라운드 서비스.
 * 화면이 꺼지거나 앱이 백그라운드로 가도 GPS 기록이 끊기지 않게 합니다.
 */
class RunService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: FusedLocationProviderClient
    private var started = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { RunTracker.onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(RunTracker.state.value)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        if (!started) {
            started = true
            startLocationUpdates()
            // 알림 문구를 시간/거리 변화에 맞춰 갱신
            scope.launch {
                RunTracker.state
                    .map { Triple(it.phase, it.elapsedSec, (it.distanceM / 10).toInt()) }
                    .distinctUntilChanged()
                    .collect {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIF_ID, buildNotification(RunTracker.state.value))
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // 권한이 없으면 서비스를 종료 (MainActivity에서 미리 권한을 확인하므로 거의 발생하지 않음)
            stopSelf()
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- 알림

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "달리기 기록", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "달리기 기록 중 표시되는 알림" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(s: RunState): Notification {
        val summary = "${formatTime(s.elapsedSec)} · ${formatKm(s.distanceKm)} km"
        val (title, text) = when (s.phase) {
            Phase.COUNTDOWN -> "달리기 준비 중" to "GPS 신호를 잡고 있어요"
            Phase.PAUSED -> "일시정지" to summary
            else -> "달리는 중" to summary
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "run_tracking"
        const val NOTIF_ID = 1001
    }
}
