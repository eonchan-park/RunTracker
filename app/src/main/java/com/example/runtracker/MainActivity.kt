package com.example.runtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // 알림 권한은 거부돼도 달리기는 가능. 정확한 위치 권한만 필수.
            if (hasFineLocation()) {
                tryStart()
            } else {
                Toast.makeText(this, "달리기 기록에는 정확한 위치 권한이 필요해요", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // 경로 지도(osmdroid) 설정: 사용자 에이전트와 타일 캐시 위치
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        setContent {
            RunApp(onStartClicked = ::onStartClicked)
        }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 시작 버튼: 필요한 권한을 확인/요청한 뒤 카운트다운 시작 */
    private fun onStartClicked() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) tryStart() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun tryStart() {
        if (!hasFineLocation()) return

        val lm = getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            Toast.makeText(this, "위치(GPS)를 켠 뒤 다시 시작해 주세요", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        RunTracker.startCountdown(this)
    }
}
