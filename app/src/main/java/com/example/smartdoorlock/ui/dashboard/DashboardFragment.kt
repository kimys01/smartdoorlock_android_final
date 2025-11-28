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

    // 실시간 상태 감지를 위한 리스너
    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null

    // 현재 도어락 ID (랜덤 ID 사용)
    private var currentDoorlockId: String? = null

    // 인증 방식 (BLE, UWB 등)
    private var currentAuthMethod: String = "BLE"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 새 도어락 등록 버튼
        binding.btnAddDevice.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_scan)
            } catch (e: Exception) {
                showSafeToast("이동 오류")
            }
        }

        // 문 열기/잠그기 버튼
        binding.btnUnlock.setOnClickListener {
            toggleDoorLock()
        }

        // 초기화: 도어락 확인 및 실시간 감시 시작
        checkAndMonitorDoorlock()

        // 인증 방식 감시 (필요시)
        monitorAuthMethod()
    }

    /**
     * 등록된 도어락이 있는지 확인하고 실시간 감시 시작
     */
    private fun checkAndMonitorDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            updateDashboardUI("로그인이 필요합니다", false)
            return
        }

        // 사용자의 도어락 목록에서 첫 번째 도어락 가져오기
        val myLocksRef = database.getReference("users").child(userId).child("my_doorlocks")

        myLocksRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            if (snapshot.exists() && snapshot.childrenCount > 0) {
                // 도어락 ID 가져오기 (랜덤 ID)
                currentDoorlockId = snapshot.children.first().key

                if (currentDoorlockId != null) {
                    Log.d("Dashboard", "도어락 ID: $currentDoorlockId")
                    // 실시간 상태 감시 시작
                    startRealtimeMonitoring(currentDoorlockId!!)
                }
            } else {
                updateDashboardUI("등록된 도어락이 없습니다", false)
            }
        }.addOnFailureListener {
            updateDashboardUI("데이터 로드 실패", false)
            Log.e("Dashboard", "도어락 조회 실패: ${it.message}")
        }
    }

    /**
     * 인증 방식 실시간 감시 (BLE, UWB 등)
     */
    private fun monitorAuthMethod() {
        val userId = auth.currentUser?.uid ?: return
        val authMethodRef = database.getReference("users").child(userId).child("authMethod")

        authMethodRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAuthMethod = snapshot.getValue(String::class.java) ?: "BLE"
                Log.d("Dashboard", "현재 인증 방식: $currentAuthMethod")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Dashboard", "인증 방식 조회 실패: ${error.message}")
            }
        })
    }

    /**
     * 실시간 도어락 상태 감시
     * Firebase의 /doorlocks/{doorlockId}/status 경로를 실시간으로 감시합니다.
     */
    private fun startRealtimeMonitoring(doorlockId: String) {
        // 기존 리스너 제거 (중복 방지)
        if (statusRef != null && statusListener != null) {
            statusRef?.removeEventListener(statusListener!!)
        }

        // 상태 경로 설정
        statusRef = database.getReference("doorlocks").child(doorlockId).child("status")

        // 실시간 리스너 등록
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                if (snapshot.exists()) {
                    val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                    val lastMethod = snapshot.child("last_method").getValue(String::class.java) ?: ""
                    val lastTime = snapshot.child("last_time").getValue(String::class.java) ?: ""

                    Log.d("Dashboard", "상태 업데이트: $state (방법: $lastMethod, 시간: $lastTime)")

                    // UI 업데이트
                    if (state == "UNLOCK") {
                        updateDashboardUI("문이 열려 있습니다 🔓", true, true)
                    } else {
                        updateDashboardUI("문이 잠겨 있습니다 🔒", true, false)
                    }
                } else {
                    updateDashboardUI("도어락 연결됨 (대기 중)", true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Dashboard", "실시간 감시 실패: ${error.message}")
                showSafeToast("연결 오류: ${error.message}")
            }
        }

        // 리스너 등록
        statusRef?.addValueEventListener(statusListener!!)
        Log.d("Dashboard", "실시간 감시 시작: /doorlocks/$doorlockId/status")
    }

    /**
     * 대시보드 UI 업데이트
     * @param statusText 상태 텍스트
     * @param isEnabled 버튼 활성화 여부
     * @param isUnlocked 현재 잠금 해제 상태인지 여부
     */
    private fun updateDashboardUI(statusText: String, isEnabled: Boolean, isUnlocked: Boolean = false) {
        if (_binding == null) return

        binding.txtStatus.text = statusText
        binding.btnUnlock.isEnabled = isEnabled

        if (isEnabled) {
            if (isUnlocked) {
                // 문이 열려있음
                binding.txtStatus.setTextColor(Color.parseColor("#2196F3")) // 파란색
                binding.btnUnlock.text = "문 잠그기 🔒"
                binding.btnUnlock.alpha = 1.0f
            } else {
                // 문이 잠겨있음
                binding.txtStatus.setTextColor(Color.parseColor("#4CAF50")) // 초록색
                binding.btnUnlock.text = "문 열기 🔓"
                binding.btnUnlock.alpha = 1.0f
            }
        } else {
            // 비활성화
            binding.txtStatus.setTextColor(Color.parseColor("#888888")) // 회색
            binding.btnUnlock.text = "도어락 연결 필요"
            binding.btnUnlock.alpha = 0.5f
        }
    }

    /**
     * 도어락 제어 (열기/잠그기)
     * Firebase의 command 경로에 명령을 전송하면 ESP32가 실시간으로 감지하여 실행합니다.
     */
    private fun toggleDoorLock() {
        if (currentDoorlockId == null) {
            showSafeToast("도어락 정보를 불러오는 중입니다.")
            return
        }

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "UnknownUser") ?: "UnknownUser"

        // [핵심 1] ESP32가 감지할 명령 경로
        val commandRef = database.getReference("doorlocks").child(currentDoorlockId!!).child("command")

        // [핵심 2] 앱 UI 및 로그용 경로
        val statusRef = database.getReference("doorlocks").child(currentDoorlockId!!).child("status")
        val sharedLogsRef = database.getReference("doorlocks").child(currentDoorlockId!!).child("logs")
        val userLogsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 현재 상태 확인
        statusRef.get().addOnSuccessListener { snapshot ->
            val currentState = snapshot.child("state").getValue(String::class.java)

            // 현재 상태의 반대로 명령 설정
            val newState = if (currentState == "UNLOCK") "LOCK" else "UNLOCK"
            val method = "APP" // 원격 제어

            Log.d("Dashboard", "명령 전송: $newState (현재 상태: $currentState)")

            // [핵심 3] ESP32로 원격 명령 전송
            commandRef.setValue(newState).addOnSuccessListener {
                val action = if (newState == "UNLOCK") "열림" else "잠김"
                showSafeToast("원격으로 문 $action 명령을 보냈습니다.")
                Log.d("Dashboard", "명령 전송 성공: $newState")
            }.addOnFailureListener { e ->
                showSafeToast("명령 전송 실패: ${e.message}")
                Log.e("Dashboard", "명령 전송 실패", e)
            }

            // [핵심 4] DB 상태값 직접 업데이트 (앱 UI 반응성)
            val updates = mapOf(
                "state" to newState,
                "last_method" to method,
                "last_time" to currentTime,
                "door_closed" to (newState == "LOCK")
            )

            statusRef.updateChildren(updates).addOnSuccessListener {
                Log.d("Dashboard", "상태 업데이트 성공")
            }.addOnFailureListener { e ->
                Log.e("Dashboard", "상태 업데이트 실패", e)
            }

            // [핵심 5] 로그 저장
            val logData = DoorlockLog(
                method = method,
                state = newState,
                time = currentTime,
                user = userId
            )

            sharedLogsRef.push().setValue(logData)
            userLogsRef.push().setValue(logData)

        }.addOnFailureListener { e ->
            showSafeToast("상태 조회 실패: ${e.message}")
            Log.e("Dashboard", "상태 조회 실패", e)
        }
    }

    /**
     * 안전한 Toast 메시지 표시 (Fragment가 활성 상태일 때만)
     */
    private fun showSafeToast(message: String) {
        if (context != null && isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 리스너 해제 (메모리 누수 방지)
        if (statusListener != null && statusRef != null) {
            statusRef?.removeEventListener(statusListener!!)
            Log.d("Dashboard", "실시간 감시 종료")
        }

        _binding = null
    }
}