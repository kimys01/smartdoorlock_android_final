package com.example.smartdoorlock

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.smartdoorlock.databinding.ActivityMainBinding
import com.example.smartdoorlock.service.LocationService
import com.example.smartdoorlock.service.UwbServiceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private lateinit var uwbManager: UwbServiceManager
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var authListener: ValueEventListener? = null
    private var authRef: DatabaseReference? = null

    private val REQUEST_ALL_PERMISSIONS = 1001

    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uwbManager = UwbServiceManager(this)
        uwbManager.init()

        supportActionBar?.let {
            val gradient = ContextCompat.getDrawable(this, R.drawable.gradient_actionbar_background)
            it.setBackgroundDrawable(gradient)
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_dashboard,
                R.id.navigation_profile,
                R.id.navigation_notifications,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // [수정] BottomNavigationView 아이템 선택 리스너 재정의 (탭 전환 시 스택 초기화)
        binding.navView.setOnItemSelectedListener { item ->
            if (item.itemId != binding.navView.selectedItemId) {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(item.itemId, true)
                    .build()

                try {
                    navController.navigate(item.itemId, null, navOptions)
                    return@setOnItemSelectedListener true
                } catch (e: IllegalArgumentException) {
                    return@setOnItemSelectedListener false
                }
            }
            true
        }

        // 화면 전환 시 UI 제어 및 로그인 상태 체크
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_login,
                R.id.navigation_register,
                R.id.findPasswordFragment -> {
                    binding.navView.visibility = View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    if (auth.currentUser == null) {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.mobile_navigation, true)
                            .build()
                        navController.navigate(R.id.navigation_login, null, navOptions)
                    } else {
                        binding.navView.visibility = View.VISIBLE
                        supportActionBar?.show()
                        observeAuthMethod()
                    }
                }
            }
        }

        // [수정] 뒤로가기 처리 커스터마이징 (종료 확인 팝업 추가)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestination = navController.currentDestination?.id

                // 1. 로그인/회원가입 화면에서는 바로 종료 (기존 로직 유지)
                if (currentDestination == R.id.navigation_login ||
                    currentDestination == R.id.navigation_register ||
                    auth.currentUser == null) {
                    finish()
                    return
                }

                // 2. 그 외 화면 (메인 탭 등)
                // popBackStack()이 false를 반환하면 더 이상 뒤로 갈 곳이 없다는 뜻 (=최상위 화면)
                // 이때 바로 종료하지 않고 확인 팝업을 띄웁니다.
                if (!navController.popBackStack()) {
                    showExitConfirmationDialog()
                }
            }
        })

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_ALL_PERMISSIONS)
        } else {
            startLocationTrackingService()
        }

        if (auth.currentUser == null) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mobile_navigation, true)
                .build()
            navController.navigate(R.id.navigation_login, null, navOptions)
        }
    }

    // [추가] 앱 종료 확인 다이얼로그
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("앱 종료")
            .setMessage("정말 앱을 종료하시겠습니까?")
            .setPositiveButton("종료") { _, _ ->
                finish() // 진짜 종료
            }
            .setNegativeButton("취소", null) // 팝업 닫기
            .show()
    }

    private fun observeAuthMethod() {
        if (authListener != null) return
        val uid = auth.currentUser?.uid ?: return
        authRef = database.getReference("users").child(uid).child("authMethod")

        authListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val method = snapshot.getValue(String::class.java) ?: "BLE"
                if (method == "UWB") uwbManager.startRanging()
                else uwbManager.stopRanging()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        authRef?.addValueEventListener(authListener!!)
    }

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationTrackingService()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uwbManager.stopRanging()
        if (authListener != null && authRef != null) {
            authRef?.removeEventListener(authListener!!)
            authListener = null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}