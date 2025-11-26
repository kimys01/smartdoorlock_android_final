package com.example.smartdoorlock.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
// import android.location.LocationListener // [삭제] 더 이상 필요 없음
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.data.LocationLog
import com.example.smartdoorlock.data.UwbLog
import com.example.smartdoorlock.utils.LocationUtils
import com.google.android.gms.location.* // Google Location Services 사용
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

// [수정] LocationListener 인터페이스 제거
class LocationService : Service() {

    // [수정] FusedLocationProviderClient 사용 (더 효율적이고 정확함)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val database = FirebaseDatabase.getInstance()
    private lateinit var uwbManager: UwbServiceManager

    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    // 위치 업데이트 주기 설정 (요청에 따라 조정 가능)
    // 너무 짧으면 배터리 소모가 큼. 현재: 3분(180초)마다 저장
    private val UPDATE_INTERVAL_MS: Long = 10 * 1000L // 10초마다 위치 확인 (UWB 거리 체크용)
    private val SAVE_INTERVAL_MS: Long = 3 * 60 * 1000L // 3분마다 DB 저장
    private var lastSavedTime: Long = 0

    private var targetMac: String? = null
    private var fixedLocation: Location? = null
    private var isUwbAuthEnabled = false
    private var isInside = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 위치 콜백 정의
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }

        uwbManager = UwbServiceManager(this)
        uwbManager.init()

        uwbManager.onUnlockRangeEntered = {
            unlockDoor()
            isInside = true
            Log.d("LocationService", "🏠 귀가 완료 (UWB OFF)")
        }

        uwbManager.onLogUpdate = { frontDist, backDist ->
            saveUwbLogToDB(frontDist, backDist)
        }

        loadDoorlockInfo()
    }

    // 위치 처리 로직 분리
    private fun processLocation(location: Location) {
        val currentTime = System.currentTimeMillis()

        // [핵심] 앱이 꺼져있어도 서비스가 돌면서 DB에 저장
        if (currentTime - lastSavedTime >= SAVE_INTERVAL_MS) {
            saveLocationToDB(location)
            lastSavedTime = currentTime
            Log.d("LocationService", "📍 백그라운드 위치 저장 완료: ${location.latitude}, ${location.longitude}")
        }

        // UWB 거리 체크 등 다른 로직 수행
        checkDistanceAndControlUwb(location)
    }

    private fun saveUwbLogToDB(front: Double, back: Double) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm:ss", Locale.getDefault()).format(Date())
        val log = UwbLog(front_distance = front, back_distance = back, timestamp = timestamp)
        val uwbLogsRef = database.getReference("users").child(username).child("uwb_logs")

        uwbLogsRef.push().setValue(log).addOnSuccessListener {
            // 로그 개수 제한 (최신 100개 유지)
            uwbLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    if (count > 100) {
                        val toRemoveCount = (count - 100).toInt()
                        var removed = 0
                        for (child in snapshot.children) {
                            if (removed < toRemoveCount) {
                                child.ref.removeValue()
                                removed++
                            } else break
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 포그라운드 서비스 알림 설정 (상단바 고정)
        val notificationIntent = Intent(this, com.example.smartdoorlock.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("스마트 도어락 위치 서비스")
            .setContentText("백그라운드에서 위치 정보를 수집 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // 아이콘 변경 가능
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 사용자가 지울 수 없게 설정
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()

        // [핵심] 시스템에 의해 강제 종료되어도 다시 시작하도록 설정
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // 위치 요청 설정
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // 높은 정확도 (GPS + Network)
            UPDATE_INTERVAL_MS // 기본 업데이트 주기
        ).apply {
            setMinUpdateIntervalMillis(5000L) // 최소 업데이트 주기
            setWaitForAccurateLocation(false) // 정확한 위치 기다리지 않음 (빠른 응답)
        }.build()

        // FusedLocationProviderClient를 사용하여 업데이트 요청
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // ... (이하 기존 로직과 동일)
    private fun checkDistanceAndControlUwb(currentLoc: Location) {
        if (fixedLocation == null || !isUwbAuthEnabled) return
        val distance = LocationUtils.calculateDistance3D(currentLoc, fixedLocation!!)

        if (distance > 150) {
            if (isInside) isInside = false
            uwbManager.stopRanging()
        } else if (distance <= 100) {
            if (!isInside) uwbManager.startRanging()
            else uwbManager.stopRanging()
        }
    }

    private fun unlockDoor() {
        if (targetMac == null) return

        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "AutoSystem") ?: "AutoSystem"

        // [수정] ESP32 호환을 위해 command에 UNLOCK 전송
        val commandRef = database.getReference("doorlocks").child(targetMac!!).child("command")
        val statusRef = database.getReference("doorlocks").child(targetMac!!).child("status")
        val sharedLogsRef = database.getReference("doorlocks").child(targetMac!!).child("logs")
        val userLogsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val method = "UWB_AUTO"
        val newState = "UNLOCK"

        // 1. ESP32 명령 전송
        commandRef.setValue(newState)

        // 2. DB 상태 업데이트
        statusRef.updateChildren(mapOf(
            "state" to newState,
            "last_method" to method,
            "last_time" to currentTime,
            "door_closed" to false
        ))

        // 3. 로그 저장
        val logData = DoorlockLog(
            method = method,
            state = newState,
            time = currentTime,
            user = userId
        )

        sharedLogsRef.push().setValue(logData)
        userLogsRef.push().setValue(logData)
    }

    private fun loadDoorlockInfo() {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        database.getReference("users").child(username).child("authMethod")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val method = snapshot.getValue(String::class.java)
                    isUwbAuthEnabled = (method == "UWB")
                    if (!isUwbAuthEnabled) uwbManager.stopRanging()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        database.getReference("users").child(username).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    targetMac = snapshot.children.first().key
                    if (targetMac != null) fetchFixedLocation(targetMac!!)
                }
            }
    }

    private fun fetchFixedLocation(mac: String) {
        database.getReference("doorlocks").child(mac).child("location")
            .get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lon = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val alt = snapshot.child("altitude").getValue(Double::class.java) ?: 0.0
                    val loc = Location("fixed")
                    loc.latitude = lat; loc.longitude = lon; loc.altitude = alt
                    fixedLocation = loc
                }
            }
    }

    private fun saveLocationToDB(location: Location) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return
        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm", Locale.getDefault()).format(Date())
        val log = LocationLog(location.altitude, location.latitude, location.longitude, timestamp)
        database.getReference("users").child(username).child("location_logs").push().setValue(log)
    }

    // [수정] LocationListener 인터페이스 제거로 인해 불필요해진 메서드 삭제
    // override fun onProviderEnabled(provider: String) {}
    // override fun onProviderDisabled(provider: String) {}

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "스마트 도어락 위치 서비스", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스가 종료될 때 위치 업데이트 중지
        fusedLocationClient.removeLocationUpdates(locationCallback)
        uwbManager.stopRanging()
        Log.d("LocationService", "서비스 종료됨")
    }
}