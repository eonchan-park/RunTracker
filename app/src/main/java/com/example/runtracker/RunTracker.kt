package com.example.runtracker

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val COUNTDOWN_SECONDS = 5

/** 화면 단계 */
enum class Phase { HOME, COUNTDOWN, RUNNING, PAUSED, SUMMARY }

/** 경로 위의 한 점 */
data class RoutePoint(val lat: Double, val lon: Double)

data class RunState(
    val phase: Phase = Phase.HOME,
    val countdown: Int = COUNTDOWN_SECONDS,
    val elapsedSec: Long = 0L,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    /** 이동 경로. 일시정지로 끊긴 구간마다 리스트가 하나씩. 결과 화면(SUMMARY)에서만 채워진다. */
    val route: List<List<RoutePoint>> = emptyList(),
) {
    val distanceKm: Double get() = distanceM / 1000.0

    /** 평균 속도 (km/h) = 총 거리 / 총 시간 */
    val avgSpeedKmh: Double
        get() = if (elapsedSec > 0) distanceM / elapsedSec * 3.6 else 0.0
}

/**
 * 달리기 상태를 한 곳에서 관리하는 싱글톤.
 * - UI(MainActivity)와 포그라운드 서비스(RunService)가 같은 상태를 공유합니다.
 * - 서비스가 살아 있는 동안 프로세스가 유지되므로 화면이 꺼져도 기록이 이어집니다.
 */
object RunTracker {

    // ---- 튜닝 값 ----
    private const val MAX_ACCURACY_M = 25f      // 이보다 부정확한 GPS 점은 버림
    private const val MAX_SPEED_MS = 12.0       // 이보다 빠른 이동(순간이동성 튐)은 버림 (약 43km/h)
    private const val MIN_STEP_M = 3f           // 이보다 짧은 이동은 GPS 떨림으로 보고 거리에 더하지 않음
    private const val STALE_FIX_MS = 5000L      // 이 시간 동안 위치가 없으면 속도를 0으로

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var timerJob: Job? = null

    private var segmentStart = 0L      // 현재 달리기 구간 시작 시각 (elapsedRealtime)
    private var accumulatedMs = 0L     // 이전 구간들에서 누적된 시간
    private var lastLocation: Location? = null
    private var lastFixAt = 0L
    private var smoothedSpeedMs = 0.0

    private val routeSegments = mutableListOf<MutableList<RoutePoint>>()
    private var announcedKm = 0        // 이미 음성 안내한 마지막 km

    // ---------------------------------------------------------------- 제어

    /** 시작 버튼: 음성 엔진과 서비스를 먼저 켜서 카운트다운 동안 준비해 둔다. */
    fun startCountdown(context: Context) {
        if (_state.value.phase != Phase.HOME) return
        val app = context.applicationContext
        _state.value = RunState(phase = Phase.COUNTDOWN, countdown = COUNTDOWN_SECONDS)
        Speaker.init(app)
        ContextCompat.startForegroundService(app, Intent(app, RunService::class.java))

        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (n in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = n) }
                delay(1000)
            }
            begin()
        }
    }

    fun cancelCountdown(context: Context) {
        if (_state.value.phase != Phase.COUNTDOWN) return
        countdownJob?.cancel()
        stopService(context)
        _state.value = RunState()
    }

    private fun begin() {
        accumulatedMs = 0L
        lastLocation = null
        lastFixAt = 0L
        smoothedSpeedMs = 0.0
        announcedKm = 0
        routeSegments.clear()
        routeSegments.add(mutableListOf())
        segmentStart = SystemClock.elapsedRealtime()
        _state.value = RunState(phase = Phase.RUNNING)
        startTimer()
    }

    fun pause() {
        if (_state.value.phase != Phase.RUNNING) return
        timerJob?.cancel()
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        smoothedSpeedMs = 0.0
        _state.update { it.copy(phase = Phase.PAUSED, elapsedSec = accumulatedMs / 1000) }
    }

    fun resume() {
        if (_state.value.phase != Phase.PAUSED) return
        // 일시정지 중 이동한 거리가 더해지지 않도록 기준점을 초기화하고, 경로도 새 구간으로 시작
        lastLocation = null
        lastFixAt = 0L
        if (routeSegments.isEmpty() || routeSegments.last().isNotEmpty()) {
            routeSegments.add(mutableListOf())
        }
        segmentStart = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = Phase.RUNNING) }
        startTimer()
    }

    /** 멈춤/종료: 기록을 마치고 결과 화면으로 + 총 거리·시간·평균 속도 음성 안내 */
    fun finish(context: Context) {
        val phase = _state.value.phase
        if (phase != Phase.RUNNING && phase != Phase.PAUSED) return
        if (phase == Phase.RUNNING) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        }
        timerJob?.cancel()
        stopService(context)

        val finalState = _state.value.copy(
            phase = Phase.SUMMARY,
            elapsedSec = accumulatedMs / 1000,
            speedKmh = 0.0,
            route = routeSegments.map { it.toList() },
        )
        _state.value = finalState

        Speaker.speak(
            finishAnnouncement(finalState.distanceKm, finalState.elapsedSec, finalState.avgSpeedKmh)
        )
    }

    fun reset() {
        if (_state.value.phase != Phase.SUMMARY) return
        routeSegments.clear()
        _state.value = RunState()
    }

    private fun stopService(context: Context) {
        val app = context.applicationContext
        app.stopService(Intent(app, RunService::class.java))
    }

    // ---------------------------------------------------------------- 타이머

    private fun currentElapsedSec(): Long =
        (accumulatedMs + SystemClock.elapsedRealtime() - segmentStart) / 1000

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = (accumulatedMs + now - segmentStart) / 1000
                val stale = lastFixAt != 0L && now - lastFixAt > STALE_FIX_MS
                if (stale) smoothedSpeedMs = 0.0
                _state.update { s ->
                    s.copy(elapsedSec = elapsed, speedKmh = if (stale) 0.0 else s.speedKmh)
                }
                delay(250)
            }
        }
    }

    // ---------------------------------------------------------------- GPS

    /** RunService가 위치를 받을 때마다 호출 (메인 스레드) */
    fun onLocation(loc: Location) {
        if (_state.value.phase != Phase.RUNNING) return
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return

        val now = SystemClock.elapsedRealtime()
        val prev = lastLocation
        if (prev == null) {
            lastLocation = loc
            lastFixAt = now
            addRoutePoint(loc)
            return
        }

        val dist = prev.distanceTo(loc)
        val dt = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
        if (dt <= 0.0) return
        if (dist / dt > MAX_SPEED_MS) return   // GPS 튐

        lastFixAt = now

        // 속도: 기기가 주는 속도 우선, 없으면 이동거리/시간. 지수이동평균으로 부드럽게.
        val rawSpeed = if (loc.hasSpeed()) loc.speed.toDouble() else dist / dt
        smoothedSpeedMs =
            if (smoothedSpeedMs == 0.0) rawSpeed else smoothedSpeedMs * 0.7 + rawSpeed * 0.3
        val speedKmh = smoothedSpeedMs * 3.6

        if (dist < MIN_STEP_M) {
            // 제자리 떨림: 거리는 더하지 않고 속도만 갱신
            _state.update { it.copy(speedKmh = speedKmh) }
            return
        }

        lastLocation = loc
        addRoutePoint(loc)
        val newDistance = _state.value.distanceM + dist
        _state.update { it.copy(distanceM = newDistance, speedKmh = speedKmh) }
        checkKmAnnouncement(newDistance)
    }

    private fun addRoutePoint(loc: Location) {
        routeSegments.lastOrNull()?.add(RoutePoint(loc.latitude, loc.longitude))
    }

    /** 1km를 새로 넘을 때마다 현재 거리·시간·평균 속도를 음성으로 안내 */
    private fun checkKmAnnouncement(distanceM: Double) {
        val km = (distanceM / 1000).toInt()
        if (km <= announcedKm) return
        announcedKm = km
        val elapsed = currentElapsedSec()
        val avgKmh = if (elapsed > 0) distanceM / elapsed * 3.6 else 0.0
        Speaker.speak(kmMarkAnnouncement(km, elapsed, avgKmh))
    }
}
