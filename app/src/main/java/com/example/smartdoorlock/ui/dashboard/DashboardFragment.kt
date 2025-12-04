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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentDoorlockId: String? = null
    private var lastState: String = "UNKNOWN"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 초기 UI 상태
        binding.txtStatus.text = "연결 중..."
        binding.btnUnlock.isEnabled = false

        // 내 도어락 ID 가져오기
        fetchMyDoorlockId()

        // 문 제어 버튼 클릭 리스너
        binding.btnUnlock.setOnClickListener {
            toggleDoorLock()
        }

        // 기기 추가 화면 이동
        binding.btnAddDevice.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_scan)
        }
    }

    private fun fetchMyDoorlockId() {
        val userId = auth.currentUser?.uid ?: return

        // 사용자 DB에서 등록된 첫 번째 도어락 가져오기
        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    currentDoorlockId = snapshot.children.first().key
                    startRealtimeMonitoring()
                } else {
                    binding.txtStatus.text = "등록된 기기가 없습니다."
                    binding.txtLastUpdated.text = "기기 추가를 눌러주세요"
                }
            }
    }

    private fun startRealtimeMonitoring() {
        val id = currentDoorlockId ?: return

        // [실시간] 상태 리스너 등록 (status 경로 감시)
        database.getReference("doorlocks").child(id).child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return

                    val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                    val time = snapshot.child("last_time").getValue(String::class.java) ?: ""

                    lastState = state
                    updateDashboardUI(state, time)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "연결 끊김", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateDashboardUI(state: String, time: String) {
        // UI 업데이트
        binding.btnUnlock.isEnabled = true
        binding.btnUnlock.alpha = 1.0f

        if (state == "UNLOCK") {
            binding.txtStatus.text = "문이 열려 있습니다 🔓"
            binding.txtStatus.setTextColor(Color.parseColor("#2196F3")) // 파란색
            binding.tvUnlockLabel.text = "문 잠그기"
            binding.imgStatusIcon.setImageResource(R.drawable.ic_lock_open)
        } else {
            binding.txtStatus.text = "문이 잠겨 있습니다 🔒"
            binding.txtStatus.setTextColor(Color.parseColor("#4CAF50")) // 초록색
            binding.tvUnlockLabel.text = "문 열기"
            binding.imgStatusIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)
        }

        binding.txtLastUpdated.text = "마지막 동작: $time"
    }

    private fun toggleDoorLock() {
        val id = currentDoorlockId ?: return

        // [중요] 버튼을 누르면 즉시 '처리 중' 상태로 변경 (아두이노 랙 방지용 UI 처리)
        binding.btnUnlock.isEnabled = false
        binding.btnUnlock.alpha = 0.5f
        binding.txtStatus.text = "명령 전송 중..."

        // 현재 상태의 반대로 명령 전송
        val nextCommand = if (lastState == "UNLOCK") "LOCK" else "UNLOCK"

        // Firebase에 명령 쓰기
        database.getReference("doorlocks").child(id).child("command")
            .setValue(nextCommand)
            .addOnFailureListener {
                Toast.makeText(context, "명령 전송 실패", Toast.LENGTH_SHORT).show()
                binding.btnUnlock.isEnabled = true // 실패 시 다시 활성화
            }
        // 성공 리스너는 따로 필요 없음 (status 변경 감지하여 UI 업데이트됨)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}