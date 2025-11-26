package com.example.smartdoorlock.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null
    private var currentMacAddress: String? = null
    // 인증 방식 변수는 유지하지만, 문 열기 로직에서는 더 이상 사용하지 않습니다.
    private var currentAuthMethod: String = "BLE"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddDevice.setOnClickListener {
            try { findNavController().navigate(R.id.action_dashboard_to_scan) }
            catch (e: Exception) { showSafeToast("이동 오류") }
        }

        binding.btnUnlock.setOnClickListener { unlockDoor() }

        checkAndMonitorDoorlock()
        monitorAuthMethod()
    }

    private fun checkAndMonitorDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            updateDashboardUI("로그인이 필요합니다", false)
            return
        }

        val myLocksRef = database.getReference("users").child(userId).child("my_doorlocks")
        myLocksRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            if (snapshot.exists() && snapshot.childrenCount > 0) {
                currentMacAddress = snapshot.children.first().key
                if (currentMacAddress != null) startRealtimeMonitoring(currentMacAddress!!)
            } else {
                updateDashboardUI("등록된 도어락이 없습니다", false)
            }
        }.addOnFailureListener {
            updateDashboardUI("데이터 로드 실패", false)
        }
    }

    private fun monitorAuthMethod() {
        val userId = auth.currentUser?.uid ?: return
        val authMethodRef = database.getReference("users").child(userId).child("authMethod")

        authMethodRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAuthMethod = snapshot.getValue(String::class.java) ?: "BLE"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startRealtimeMonitoring(mac: String) {
        if (statusRef != null && statusListener != null) {
            statusRef?.removeEventListener(statusListener!!)
        }
        statusRef = database.getReference("doorlocks").child(mac).child("status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                if (snapshot.exists()) {
                    val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                    if (state == "UNLOCK") updateDashboardUI("문이 열려 있습니다 (UNLOCKED)", true, true)
                    else updateDashboardUI("문이 잠겨 있습니다 (LOCKED)", true, false)
                } else {
                    updateDashboardUI("도어락 연결됨 (대기 중)", true)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef?.addValueEventListener(statusListener!!)
    }

    private fun updateDashboardUI(statusText: String, isEnabled: Boolean, isUnlocked: Boolean = false) {
        if (_binding == null) return
        binding.txtStatus.text = statusText
        binding.btnUnlock.isEnabled = isEnabled
        if (isEnabled) {
            if (isUnlocked) {
                binding.txtStatus.setTextColor(Color.parseColor("#2196F3"))
                binding.btnUnlock.text = "문 잠그기 (LOCK)"
            } else {
                binding.txtStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.btnUnlock.text = "문 열기 (UNLOCK)"
            }
            binding.btnUnlock.alpha = 1.0f
        } else {
            binding.txtStatus.setTextColor(Color.parseColor("#888888"))
            binding.btnUnlock.text = "도어락 연결 필요"
            binding.btnUnlock.alpha = 0.5f
        }
    }

    private fun unlockDoor() {
        if (currentMacAddress == null) {
            showSafeToast("도어락 정보를 불러오는 중입니다.")
            return
        }

        // [수정] 인증 방식 체크 로직 제거 (모든 방식 허용)
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "UnknownUser") ?: "UnknownUser"

        // [핵심] ESP32가 감지할 명령 경로 (doorlocks/{mac}/command)
        val commandRef = database.getReference("doorlocks").child(currentMacAddress!!).child("command")

        // 앱 UI 및 로그용 경로
        val statusRef = database.getReference("doorlocks").child(currentMacAddress!!).child("status")
        val sharedLogsRef = database.getReference("doorlocks").child(currentMacAddress!!).child("logs")
        val userLogsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        statusRef.get().addOnSuccessListener { snapshot ->
            val currentState = snapshot.child("state").getValue(String::class.java)
            // 현재 상태의 반대로 명령 설정 (열려있으면 잠그고, 잠겨있으면 열기)
            val newState = if (currentState == "UNLOCK") "LOCK" else "UNLOCK"
            val method = "APP" // 원격 제어 로그는 "APP"으로 기록

            // 1. ESP32로 원격 명령 전송 (UNLOCK 또는 LOCK)
            commandRef.setValue(newState).addOnSuccessListener {
                val action = if (newState == "UNLOCK") "열림" else "잠김"
                showSafeToast("원격으로 문 $action 명령을 보냈습니다.")
            }

            // 2. DB 상태값 직접 업데이트 (앱 UI 반응성 및 로그 기록용)
            val updates = mapOf(
                "state" to newState,
                "last_method" to method,
                "last_time" to currentTime,
                "door_closed" to (newState == "LOCK")
            )

            statusRef.updateChildren(updates)

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
    }

    private fun showSafeToast(message: String) {
        if (context != null && isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (statusListener != null && statusRef != null) {
            statusRef?.removeEventListener(statusListener!!)
        }
        _binding = null
    }
}