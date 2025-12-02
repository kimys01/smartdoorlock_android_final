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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUnifiedSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return
        val userRef = database.getReference("users").child(userId)

        // 초기 데이터 로드
        loadSettings(userRef)

        // 저장 버튼 클릭 리스너
        binding.btnSaveSettings.setOnClickListener {
            saveSettings(userRef)
        }
    }

    private fun loadSettings(userRef: DatabaseReference) {
        // 1. 상세 설정 로드
        userRef.child("detailSettings").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false
                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify
            }
        }

        // 2. 인증 방식 로드 (auth_config 경로 사용)
        userRef.child("auth_config").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                binding.checkBle.isChecked = snapshot.child("ble").getValue(Boolean::class.java) ?: false
                binding.checkUwb.isChecked = snapshot.child("uwb").getValue(Boolean::class.java) ?: false
                binding.checkPhysical.isChecked = snapshot.child("physical").getValue(Boolean::class.java) ?: false
                binding.checkApp.isChecked = snapshot.child("app").getValue(Boolean::class.java) ?: false
            } else {
                // 데이터가 없으면 기본값 (예: BLE만 켜기)
                binding.checkBle.isChecked = true
            }
        }
    }

    private fun saveSettings(userRef: DatabaseReference) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. 상세 설정 저장 데이터
        val autoLock = binding.switchAutoLock.isChecked
        val notify = binding.switchNotifyOnLock.isChecked

        userRef.child("detailSettings/autoLockEnabled").setValue(autoLock)
        userRef.child("detailSettings/autoLockTime").setValue(if (autoLock) 5 else 0)
        userRef.child("detailSettings/notifyOnLock").setValue(notify)

        // 2. 인증 방식 저장 데이터 (Map 구조)
        val authConfig = mapOf(
            "ble" to binding.checkBle.isChecked,
            "uwb" to binding.checkUwb.isChecked,
            "physical" to binding.checkPhysical.isChecked,
            "app" to binding.checkApp.isChecked
        )
        userRef.child("auth_config").setValue(authConfig)

        // 3. 로그 저장
        val activeMethods = authConfig.filterValues { it }.keys.joinToString(", ")
        val logMsg = "설정 변경 완료 (인증: $activeMethods | 자동잠금: $autoLock)"
        val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

        userRef.child("app_logs").push().setValue(logItem)
            .addOnSuccessListener {
                Toast.makeText(context, "모든 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
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