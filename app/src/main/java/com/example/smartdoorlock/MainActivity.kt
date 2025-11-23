package com.example.smartdoorlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.smartdoorlock.databinding.ActivityMainBinding
import com.example.smartdoorlock.service.LocationService
import com.example.smartdoorlock.service.UwbServiceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private lateinit var uwbManager: UwbServiceManager
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // [수정] 리스너 중복 방지를 위한 변수
    private var authListener: ValueEventListener? = null
    private var authRef: DatabaseReference? = null

    private val REQUEST_LOCATION_PERMISSIONS = 1001

    private val LOCATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UWB 매니저 초기화
        uwbManager = UwbServiceManager(this)
        uwbManager.init()

        supportActionBar?.let { actionBar ->
            val gradient = ContextCompat.getDrawable(this, R.drawable.gradient_actionbar_background)
            actionBar.setBackgroundDrawable(gradient)
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_profile,
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.navigation_settings,
                R.id.navigation_login,
                R.id.deviceScanFragment,
                R.id.navigation_auth_method,
                R.id.navigation_detail_setting,
                R.id.wifiSettingFragment,
                R.id.navigation_help
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        val navView: BottomNavigationView = binding.navView
        navView.setupWithNavController(navController)

        // 화면 전환 리스너
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_login -> {
                    navView.visibility = View.GONE
                    // 로그아웃 상태이거나 로그인 화면이면 리스너 제거 고려 가능
                }
                R.id.navigation_register -> {
                    navView.visibility = View.GONE
                }
                else -> {
                    navView.visibility = View.VISIBLE
                    // 로그인 후 메인 화면 진입 시 인증 모드 감시 시작
                    if (auth.currentUser != null) observeAuthMethod()
                }
            }
        }

        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMISSIONS)
        } else {
            startLocationTrackingService()
        }

        if (savedInstanceState == null) {
            navController.navigate(R.id.navigation_login)
        }

        // 앱 시작 시 이미 로그인된 상태라면 감시 시작
        if (auth.currentUser != null) {
            observeAuthMethod()
        }
    }

    // [수정] Firebase DB 감시 (중복 실행 방지 적용)
    private fun observeAuthMethod() {
        // 이미 리스너가 등록되어 있다면 중복 등록하지 않음
        if (authListener != null) return

        val uid = auth.currentUser?.uid ?: return
        authRef = database.getReference("users").child(uid).child("authMethod")

        authListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val method = snapshot.getValue(String::class.java) ?: "BLE"
                Log.d("AuthMonitor", "인증 모드 변경 감지: $method")

                when (method) {
                    "UWB" -> {
                        Log.i("AuthMonitor", "UWB 모드 활성화")
                        uwbManager.startRanging()
                    }
                    else -> {
                        Log.i("AuthMonitor", "UWB 모드 비활성화 (현재: $method)")
                        uwbManager.stopRanging()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AuthMonitor", "DB 읽기 오류", error.toException())
            }
        }

        // 리스너 등록
        authRef?.addValueEventListener(authListener!!)
    }

    private fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startLocationTrackingService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startLocationTrackingService()
            }
        }
    }

    // 앱 종료 시 리소스 정리
    override fun onDestroy() {
        super.onDestroy()
        uwbManager.stopRanging()

        // [추가] DB 리스너 제거 (메모리 누수 방지)
        if (authListener != null && authRef != null) {
            authRef?.removeEventListener(authListener!!)
            authListener = null
        }
    }
}