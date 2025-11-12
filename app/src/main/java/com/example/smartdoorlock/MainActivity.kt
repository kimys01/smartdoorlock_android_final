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
import com.example.smartdoorlock.notifications.LocationService
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val REQUEST_LOCATION_PERMISSIONS = 1001

    // Android 10 이상만 background location 포함
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

        supportActionBar?.let { actionBar ->
            // 'gradient_button_background' 대신 'gradient_actionbar_background'를 사용합니다.
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
                R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        val navView: BottomNavigationView = binding.navView
        navView.setupWithNavController(navController)

        // --- [추가된 블록 시작] ---
        // NavController가 화면을 변경할 때마다 감지하는 리스너를 추가합니다.
        // 로그인 화면에서는 하단 네비게이션 바를 숨기기 위함입니다.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // mobile_navigation.xml에 정의된 LoginFragment의 ID입니다.
                R.id.navigation_login -> {
                    navView.visibility = View.GONE // 하단 바 숨기기
                }
                // (선택 사항) 회원가입 등 추가로 숨길 화면이 있다면 여기에 추가
                // R.id.navigation_signup -> {
                //     navView.visibility = View.GONE
                // }
                else -> {
                    navView.visibility = View.VISIBLE // 그 외 모든 화면에서 하단 바 보이기
                }
            }
        }
        // --- [추가된 블록 끝] ---

        // 위치 권한 확인 및 요청
        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMISSIONS)
        } else {
            startLocationTrackingService()
        }
        // 앱 최초 실행 시 로그인 화면으로 이동
        if (savedInstanceState == null) {
            navController.navigate(R.id.navigation_login)
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startLocationTrackingService() {
        Log.d("MainActivity", "📡 위치 추적 서비스 시작")
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

            // 💡 수정된 부분
            (permissions zip grantResults.toTypedArray()).forEach { (permission, result) ->
                Log.d("PermissionResult", "$permission: ${if (result == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied"}")
            }

            if (allGranted) {
                startLocationTrackingService()
            } else {
                Log.w("MainActivity", "❌ 일부 위치 권한 거부됨 → 서비스 시작하지 않음")
            }
        }
    }
}