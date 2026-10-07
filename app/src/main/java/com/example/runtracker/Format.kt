package com.example.runtracker

import java.util.Locale
import kotlin.math.roundToInt

/** 초 → "mm:ss" 또는 "h:mm:ss" */
fun formatTime(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** km/h → 페이스 "m'ss\"" (분/km). 거의 멈춰 있으면 "--'--\"" */
fun formatPace(speedKmh: Double): String {
    if (speedKmh < 0.5) return "--'--\""
    val secPerKm = (3600.0 / speedKmh).roundToInt()
    val m = secPerKm / 60
    val s = secPerKm % 60
    if (m >= 60) return "--'--\""
    return String.format(Locale.US, "%d'%02d\"", m, s)
}

fun formatKm(km: Double): String = String.format(Locale.US, "%.2f", km)

fun formatSpeed(kmh: Double): String = String.format(Locale.US, "%.1f", kmh)

// ------------------------------------------------------------------ 음성 안내 문구

/** 초 → "1시간 5분 30초" (0인 단위는 생략) */
fun spokenDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val parts = buildList {
        if (h > 0) add("${h}시간")
        if (m > 0) add("${m}분")
        if (s > 0 || (h == 0L && m == 0L)) add("${s}초")
    }
    return parts.joinToString(" ")
}

/** 1km 지점마다 읽어 줄 문구: 현재 달린 거리, 시간, 평균 속도 */
fun kmMarkAnnouncement(km: Int, elapsedSec: Long, avgKmh: Double): String =
    "${km}킬로미터 달렸어요. 달린 시간은 ${spokenDuration(elapsedSec)}, " +
        "평균 속도는 시속 ${formatSpeed(avgKmh)}킬로미터예요."

/** 종료 시 읽어 줄 문구: 총 거리, 총 시간, 평균 속도 */
fun finishAnnouncement(distanceKm: Double, elapsedSec: Long, avgKmh: Double): String =
    "수고하셨어요. 총 달린 거리는 ${formatKm(distanceKm)}킬로미터, " +
        "총 시간은 ${spokenDuration(elapsedSec)}, " +
        "평균 속도는 시속 ${formatSpeed(avgKmh)}킬로미터예요."
