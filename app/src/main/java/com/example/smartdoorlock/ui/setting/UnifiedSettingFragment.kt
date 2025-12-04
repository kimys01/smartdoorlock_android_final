package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AppLogItem
import com.example.smartdoorlock.databinding.FragmentUnifiedSettingBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class UnifiedSettingFragment : Fragment() {

    private var _binding: FragmentUnifiedSettingBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()

    // 도어락 명령 전송을 위한 ID 변수
    private var currentDoorlockId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUnifiedSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return
        val userRef = database.getReference("users").child(userId)

        // 0. 도어락 ID 가져오기 (명령 전송용)
        fetchDoorlockId(userId)

        // 1. 초기 데이터 로드
        loadSettings(userRef)

        // 2. 저장 버튼 클릭 리스너
        binding.btnSaveSettings.setOnClickListener {
            saveSettings(userRef)
        }
    }

    private fun fetchDoorlockId(userId: String) {
        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    currentDoorlockId = snapshot.children.first().key
                }
            }
    }

    private fun loadSettings(userRef: DatabaseReference) {
        // 1. 상세 설정 로드
        userRef.child("detailSettings").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false
                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify
            }
        }

        // 2. 인증 방식 로드
        userRef.child("auth_config").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            if (snapshot.exists()) {
                binding.checkBle.isChecked = snapshot.child("ble").getValue(Boolean::class.java) ?: true
                binding.checkUwb.isChecked = snapshot.child("uwb").getValue(Boolean::class.java) ?: true

                // 앱 인증 상태 로드 (항상 켜져있음 표시)
                binding.checkApp.isChecked = true
                binding.checkApp.isEnabled = false // 사용자가 끌 수 없도록 비활성화
            } else {
                // 데이터 없음 기본값
                binding.checkBle.isChecked = true
                binding.checkUwb.isChecked = true
                binding.checkApp.isChecked = true
                binding.checkApp.isEnabled = false
            }
        }
    }

    private fun saveSettings(userRef: DatabaseReference) {
        if (_binding == null) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. UI 상태 가져오기
        val isBleOn = binding.checkBle.isChecked
        val isUwbOn = binding.checkUwb.isChecked
        val isAppOn = true // 앱 제어는 항상 가능

        // 물리 인증(키패드/RFID)은 항상 True로 고정
        val isPhysicalOn = true

        val autoLock = binding.switchAutoLock.isChecked
        val notify = binding.switchNotifyOnLock.isChecked

        // 2. Firebase User DB 저장 (앱 설정 동기화용)
        userRef.child("detailSettings/autoLockEnabled").setValue(autoLock)
        userRef.child("detailSettings/autoLockTime").setValue(if (autoLock) 5 else 0)
        userRef.child("detailSettings/notifyOnLock").setValue(notify)

        val authConfig = mapOf(
            "ble" to isBleOn,
            "uwb" to isUwbOn,
            "physical" to isPhysicalOn,
            "app" to isAppOn
        )
        userRef.child("auth_config").setValue(authConfig)

        // 3. 아두이노(ESP32)로 SET_AUTH 명령 전송
        // [중요 수정] 하드웨어 코드(Hybrid System)에 맞춰 BLE와 UWB 2개 값만 전송합니다.
        // 형식: SET_AUTH:1,0 (BLE=ON, UWB=OFF)
        if (currentDoorlockId != null) {
            val bleVal = if (isBleOn) "1" else "0"
            val uwbVal = if (isUwbOn) "1" else "0"

            // 앱 제어와 물리키는 하드웨어에서 항상 허용되므로 설정 명령에서 제외하거나 무시됩니다.
            val commandStr = "SET_AUTH:$bleVal,$uwbVal"

            database.getReference("doorlocks").child(currentDoorlockId!!).child("command")
                .setValue(commandStr)
                .addOnSuccessListener {
                    Toast.makeText(context, "설정이 도어락에 적용되었습니다.", Toast.LENGTH_SHORT).show()
                }
        }

        // 4. 로그 저장
        val activeMethods = authConfig.filterValues { it }.keys.joinToString(", ")
        val logMsg = "설정 변경 (인증: $activeMethods | 자동잠금: $autoLock)"
        val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

        userRef.child("app_logs").push().setValue(logItem)
            .addOnSuccessListener {
                // Toast 중복 표시 방지
            }
            .addOnFailureListener {
                Toast.makeText(context, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}