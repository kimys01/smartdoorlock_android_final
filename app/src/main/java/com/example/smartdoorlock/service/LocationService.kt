package com.example.smartdoorlock.service

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
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.smartdoorlock.data.LocationLog
import com.example.smartdoorlock.data.UwbLog
import com.example.smartdoorlock.utils.LocationUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val database = FirebaseDatabase.getInstance()
    private lateinit var uwbManager: UwbServiceManager

    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    private val UPDATE_INTERVAL_MS: Long = 10 * 1000L
    private val SAVE_INTERVAL_MS: Long = 3 * 60 * 1000L // 3분
    private var lastSavedTime: Long = 0

    private var targetMac: String? = null
    private var fixedLocation: Location? = null
    private var isUwbAuthEnabled = false
    private var isInside = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

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

    private fun saveUwbLogToDB(front: Double, back: Double) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm:ss", Locale.getDefault()).format(Date())

        val log = UwbLog(
            front_distance = front,
            back_distance = back,
            timestamp = timestamp
        )

        val uwbLogsRef = database.getReference("users").child(username).child("uwb_logs")

        uwbLogsRef.push().setValue(log).addOnSuccessListener {
            // 100개 유지 로직
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
                            } else {
                                break
                            }
                        }
                        Log.d("LocationService", "🧹 UWB 로그 정리 완료: ${removed}개 삭제됨")
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("스마트 도어락 서비스")
            .setContentText("위치 및 접근 감지 중...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_MS, 0f, this)
        } catch (e: Exception) {
            Log.e("LocationService", "위치 요청 실패", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        val currentTime = System.currentTimeMillis()
        // 3분마다 GPS 저장
        if (currentTime - lastSavedTime >= SAVE_INTERVAL_MS) {
            saveLocationToDB(location)
            lastSavedTime = currentTime
        }
        checkDistanceAndControlUwb(location)
    }

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
        val statusRef = database.getReference("doorlocks").child(targetMac!!).child("status")
        val logsRef = database.getReference("doorlocks").child(targetMac!!).child("logs")
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        statusRef.updateChildren(mapOf("state" to "UNLOCK", "last_method" to "UWB_AUTO", "last_time" to currentTime, "door_closed" to false))
        logsRef.push().setValue(mapOf("method" to "UWB_AUTO", "state" to "UNLOCK", "time" to currentTime))
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

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "스마트 도어락 서비스", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationManager.isInitialized) locationManager.removeUpdates(this)
        uwbManager.stopRanging()
    }
}