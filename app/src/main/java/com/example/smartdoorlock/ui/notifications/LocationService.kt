package com.example.smartdoorlock.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

public class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    // --- 변경 사항 1: 위치 업데이트 주기를 5분(5 * 60 * 1000L)으로 변경 ---
    private val MIN_TIME_MS: Long = 5 * 60 * 1000L // 5분
    private val MIN_DISTANCE_M: Float = 10f // 10미터

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "🟢 서비스 시작됨 (5분 주기)");

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("위치 추적 중")
            .setContentText("스마트 도어락 위치 추적 서비스 실행 중 (5분 주기)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!hasAllPermissions()) {
            Log.e("LocationService", "❌ 위치 권한 부족 → 서비스 종료")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // --- 변경 사항 1 (적용) ---
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS, // 5분
                MIN_DISTANCE_M,
                this
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_MS, // 5분
                MIN_DISTANCE_M,
                this
            )
        } catch (e: Exception) {
            Log.e("LocationService", "❌ 위치 요청 실패: ${e.localizedMessage}")
            stopForeground(true)
            stopSelf()
        }

        return START_STICKY
    }

    private fun hasAllPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d("LocationService", "📍 위치 변경됨: ${location.latitude}, ${location.longitude}, 고도: ${location.altitude}")

        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            Log.e("LocationService", "❌ userId 없음 → 로그 저장 불가")
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // --- 변경 사항 2: 'altitude' (고도) 추가 ---
        val locationLog = mapOf(
            "user_id" to userId,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "altitude" to location.altitude, // 고도 추가
            "timestamp" to timestamp
        )

        // --- 변경 사항 3: 저장 경로를 'users/{userId}/location_logs'로 변경 ---
        FirebaseDatabase.getInstance().getReference("users") // 최상위 경로 'users'로 변경
            .child(userId)
            .child("location_logs") // 'location_logs' 하위에 저장
            .push()
            .setValue(locationLog)
            .addOnSuccessListener {
                Log.d("LocationService", "✅ users/${userId}/location_logs 저장 성공")
            }
            .addOnFailureListener {
                Log.e("LocationService", "❌ users/${userId}/location_logs 저장 실패: ${it.message}")
            }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("LocationService", "📡 위치 제공자 사용 가능: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.w("LocationService", "📡 위치 제공자 비활성화: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("LocationService", "📡 위치 상태 변경: $provider → $status")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e("LocationService", "❌ 위치 업데이트 해제 실패: ${e.localizedMessage}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "위치 추적 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
