package com.example.smartdoorlock.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.example.smartdoorlock.helper.FirebaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * DashboardFragment v2.0 - 실시간 양방향 동기화
 *
 * 📱 앱 → ESP32: command 경로에 "LOCK"/"UNLOCK" 전송
 * 📡 ESP32 → 앱: status 경로 실시간 감시로 UI 자동 업데이트
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // Firebase 리스너
    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null
    private var currentDoorlockId: String? = null

    // 상태 캐시 (중복 업데이트 방지)
    private var lastKnownState: String = ""

    companion object {
        private const val TAG = "Dashboard"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 새 도어락 추가 버튼
        binding.btnAddDevice.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_scan)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation error", e)
            }
        }

        // 잠금/해제 버튼
        binding.btnUnlock.setOnClickListener {
            sendDoorCommand()
        }

        // 도어락 확인 및 실시간 모니터링 시작
        checkAndMonitorDoorlock()
    }

    /**
     * 사용자의 도어락 확인 후 실시간 모니터링 시작
     */
    private fun checkAndMonitorDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            updateDashboardUI("로그인이 필요합니다", false)
            return
        }

        Log.d(TAG, "Checking doorlocks for user: $userId")

        // 사용자의 첫 번째 도어락 가져오기
        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    currentDoorlockId = snapshot.children.first().key
                    Log.d(TAG, "Found doorlock: $currentDoorlockId")

                    if (currentDoorlockId != null) {
                        // 실시간 모니터링 시작
                        startRealtimeMonitoring(currentDoorlockId!!)
                    }
                } else {
                    Log.d(TAG, "No doorlock registered")
                    updateDashboardUI("등록된 도어락이 없습니다", false)
                    binding.btnAddDevice.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get doorlocks", e)
                updateDashboardUI("도어락 정보 로드 실패", false)
            }
    }

    /**
     * 🔄 실시간 상태 모니터링 시작
     *
     * ESP32가 status를 업데이트하면 즉시 이 콜백이 호출됨
     */
    private fun startRealtimeMonitoring(doorlockId: String) {
        // 기존 리스너 제거
        if (statusRef != null && statusListener != null) {
            statusRef?.removeEventListener(statusListener!!)
        }

        // status 전체 경로 감시 (state, last_method, last_time 모두 포함)
        statusRef = database.getReference("doorlocks").child(doorlockId).child("status")

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                Log.d(TAG, "========== STATUS CHANGED ==========")
                Log.d(TAG, "Raw snapshot: ${snapshot.value}")

                if (!snapshot.exists()) {
                    updateDashboardUI("상태 정보 없음", false)
                    return
                }

                // 상태 정보 파싱
                val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                val lastMethod = snapshot.child("last_method").getValue(String::class.java) ?: ""
                val lastTime = snapshot.child("last_time").getValue(String::class.java) ?: ""
                val doorClosed = snapshot.child("door_closed").getValue(Boolean::class.java) ?: true

                Log.d(TAG, "State: $state")
                Log.d(TAG, "Last Method: $lastMethod")
                Log.d(TAG, "Last Time: $lastTime")
                Log.d(TAG, "Door Closed: $doorClosed")

                // 상태가 변경되었을 때만 UI 업데이트
                if (state != lastKnownState) {
                    lastKnownState = state
                    Log.d(TAG, "State changed! Updating UI...")

                    when (state.uppercase()) {
                        "UNLOCK", "OPEN" -> {
                            updateDashboardUI("🔓 문이 열려 있습니다", true, true)
                            showMethodInfo(lastMethod, lastTime)
                        }
                        "LOCK", "CLOSE" -> {
                            updateDashboardUI("🔒 문이 잠겨 있습니다", true, false)
                            showMethodInfo(lastMethod, lastTime)
                        }
                        else -> {
                            updateDashboardUI("상태: $state", true, false)
                        }
                    }
                } else {
                    Log.d(TAG, "Same state, no UI update needed")
                }

                Log.d(TAG, "=====================================")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Status listener cancelled: ${error.message}")
                updateDashboardUI("연결 오류", false)
            }
        }

        // 리스너 등록 (addValueEventListener = 실시간 감시)
        statusRef?.addValueEventListener(statusListener!!)
        Log.d(TAG, "Started realtime monitoring for: $doorlockId")
    }

    /**
     * 📱 → 📡 명령 전송
     *
     * command 경로에 LOCK/UNLOCK 전송
     * ESP32가 이를 수신하여 처리 후 status 업데이트
     */
    private fun sendDoorCommand() {
        if (currentDoorlockId == null) {
            Toast.makeText(context, "도어락이 연결되지 않았습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 현재 상태 기반으로 반대 명령 결정
        val newCommand = if (lastKnownState.uppercase() == "UNLOCK" ||
            lastKnownState.uppercase() == "OPEN") {
            "LOCK"
        } else {
            "UNLOCK"
        }

        Log.d(TAG, "Sending command: $newCommand")

        // 버튼 비활성화 (중복 클릭 방지)
        binding.btnUnlock.isEnabled = false
        binding.btnUnlock.text = "처리 중..."

        // Firebase command 경로에 명령 전송
        val commandRef = database.getReference("doorlocks")
            .child(currentDoorlockId!!)
            .child("command")

        commandRef.setValue(newCommand)
            .addOnSuccessListener {
                Log.d(TAG, "Command sent successfully: $newCommand")

                // 앱 로그 기록
                FirebaseHelper.addAppLog("원격 제어: $newCommand 명령 전송")

                // 버튼 다시 활성화 (실제 상태는 status 리스너가 업데이트)
                binding.btnUnlock.isEnabled = true

                // 사용자 피드백
                val message = if (newCommand == "UNLOCK") "열림 명령 전송됨" else "잠금 명령 전송됨"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send command", e)
                binding.btnUnlock.isEnabled = true
                binding.btnUnlock.text = if (lastKnownState.uppercase() == "UNLOCK") "문 잠그기 🔒" else "문 열기 🔓"
                Toast.makeText(context, "명령 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * UI 업데이트
     */
    private fun updateDashboardUI(statusText: String, isEnabled: Boolean, isUnlocked: Boolean = false) {
        if (_binding == null) return

        binding.txtStatus.text = statusText
        binding.btnUnlock.isEnabled = isEnabled

        if (isEnabled) {
            if (isUnlocked) {
                // 열린 상태
                binding.txtStatus.setTextColor(Color.parseColor("#2196F3"))  // 파란색
                binding.btnUnlock.text = "문 잠그기 🔒"
                binding.btnUnlock.setBackgroundResource(R.drawable.gradient_button_background)
            } else {
                // 잠긴 상태
                binding.txtStatus.setTextColor(Color.parseColor("#4CAF50"))  // 초록색
                binding.btnUnlock.text = "문 열기 🔓"
                binding.btnUnlock.setBackgroundResource(R.drawable.gradient_button_background)
            }
        } else {
            binding.txtStatus.setTextColor(Color.parseColor("#888888"))  // 회색
        }
    }

    /**
     * 마지막 조작 정보 표시 (선택적)
     */
    private fun showMethodInfo(method: String, time: String) {
        if (method.isNotEmpty()) {
            val methodText = when (method.uppercase()) {
                "APP" -> "앱"
                "RFID" -> "RFID 카드"
                "KEYPAD" -> "키패드"
                "INSIDE_BTN" -> "내부 버튼"
                "OUTSIDE_BTN" -> "외부 버튼"
                "DOOR_BTN" -> "도어 버튼"
                "AUTO_LOCK" -> "자동 잠금"
                "BOOT", "INIT" -> "시스템"
                "DB_SYNC", "DB_POLL" -> "동기화"
                else -> method
            }
            Log.d(TAG, "Last action: $methodText at $time")
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 상태 재확인
        currentDoorlockId?.let {
            startRealtimeMonitoring(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 리스너 정리
        if (statusListener != null && statusRef != null) {
            statusRef?.removeEventListener(statusListener!!)
            Log.d(TAG, "Removed status listener")
        }

        _binding = null
    }
}