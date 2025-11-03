package com.example.smartdoorlock.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartdoorlock.api.RetrofitClient
import com.example.smartdoorlock.data.AccessLog
import com.example.smartdoorlock.data.DoorLockLog
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var userId: String? = null
    private lateinit var statusPrefs: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val prefs = requireContext().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        userId = prefs.getString("saved_id", null)
        if (userId == null) {
            Toast.makeText(context, "사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        statusPrefs = requireContext().getSharedPreferences("status_prefs", Context.MODE_PRIVATE)

        updateUI()

        binding.LockOpen.setOnClickListener {
            val lastStatus = statusPrefs.getString("last_status", "잠금")
            val currentStatus = if (lastStatus == "잠금") "해제" else "잠금"
            val statusTransition = "$lastStatus → $currentStatus"
            statusPrefs.edit().putString("last_status", currentStatus).apply()

            val method = "BLE"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            userId?.let { id ->
                sendAccessLog(id, timestamp, statusTransition, method)
                fetchAndSaveDoorlockLog(id, statusTransition, method, timestamp)
            }

            updateUI()
        }
    }

    private fun updateUI() {
        val lastStatus = statusPrefs.getString("last_status", "잠금")
        if (lastStatus == "잠금") {
            binding.LockOpen.text = "해제"
            binding.textLockStatus.text = "상태 : 잠금"
        } else {
            binding.LockOpen.text = "잠금"
            binding.textLockStatus.text = "상태 : 해제"
        }
    }

    private fun sendAccessLog(userId: String, time: String, status: String, method: String) {
        val log = AccessLog(user_id = userId, time = time, status = status, method = method)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.sendLog(log)
                if (response.isSuccessful) {
                    Toast.makeText(context, "출입 로그 전송 성공", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "서버 응답 오류", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "연결 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchAndSaveDoorlockLog(userId: String, status: String, method: String, timestamp: String) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val lat = location?.latitude ?: 0.0
            val lon = location?.longitude ?: 0.0

            val log = DoorLockLog(
                user_id = userId,
                status = status,
                timestamp = timestamp,
                method = method
            )

            FirebaseDatabase.getInstance().getReference("doorlock_logs")
                .child(userId)
                .push()
                .setValue(log)
                .addOnSuccessListener {
                    Toast.makeText(context, "도어락 로그 저장 성공", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "도어락 로그 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }

        }.addOnFailureListener {
            Toast.makeText(context, "위치 가져오기 실패: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
