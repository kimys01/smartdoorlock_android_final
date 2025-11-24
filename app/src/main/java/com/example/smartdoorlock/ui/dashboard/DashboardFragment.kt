package com.example.smartdoorlock.ui.dashboard

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
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // 리스너 관리를 위한 변수
    private var statusListener: ValueEventListener? = null
    private var myLocksRef: com.google.firebase.database.DatabaseReference? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. [+ 새 도어락 등록] 버튼
        binding.btnAddDevice.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_scan)
            } catch (e: Exception) {
                showSafeToast("이동 오류: 네비게이션을 확인하세요.")
            }
        }

        // 2. [잠금 해제] 버튼
        binding.btnUnlock.setOnClickListener {
            try {
                unlockDoor()
            } catch (e: Exception) {
                Log.e("Dashboard", "버튼 클릭 처리 중 오류", e)
                showSafeToast("오류가 발생했습니다: ${e.message}")
            }
        }

        // 3. [핵심] 도어락 상태 실시간 모니터링 시작
        monitorDoorlockStatus()
    }

    private fun monitorDoorlockStatus() {
        val uid = auth.currentUser?.uid ?: return

        // 내 도어락 목록 경로: users/{uid}/my_doorlocks
        myLocksRef = database.getReference("users").child(uid).child("my_doorlocks")

        // 이미 리스너가 있으면 제거 (중복 방지)
        if (statusListener != null) {
            myLocksRef?.removeEventListener(statusListener!!)
        }

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 화면이 살아있는지 확인 (팅김 방지)
                if (_binding == null || !isAdded) return

                if (snapshot.exists() && snapshot.hasChildren()) {
                    // 도어락이 하나라도 있으면 연결된 것으로 간주
                    binding.txtStatus.text = "도어락이 연결되었습니다"

                    // [수정됨] 색상 리소스 오류 해결: 직접 파싱하여 사용
                    binding.txtStatus.setTextColor(Color.parseColor("#2196F3"))

                    binding.btnUnlock.isEnabled = true // 버튼 활성화
                } else {
                    // 도어락이 없으면
                    binding.txtStatus.text = "등록된 도어락이 없습니다"
                    binding.txtStatus.setTextColor(Color.parseColor("#888888")) // 회색
                    binding.btnUnlock.isEnabled = false // 버튼 비활성화
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Dashboard", "DB 읽기 오류", error.toException())
            }
        }

        // 리스너 등록
        myLocksRef?.addValueEventListener(statusListener!!)
    }

    private fun unlockDoor() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showSafeToast("로그인이 필요합니다.")
            return
        }

        val myLocksRef = database.getReference("users").child(uid).child("my_doorlocks")

        myLocksRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded || _binding == null) return@addOnSuccessListener

            if (snapshot.exists() && snapshot.hasChildren()) {
                try {
                    // 첫 번째 도어락의 MAC 주소를 가져와서 명령 전송
                    val firstLockMac = snapshot.children.first().key
                    if (!firstLockMac.isNullOrEmpty()) {
                        sendUnlockCommand(firstLockMac)
                    } else {
                        showSafeToast("도어락 정보 오류")
                    }
                } catch (e: NoSuchElementException) {
                    showSafeToast("도어락 목록이 비어있습니다.")
                }
            } else {
                showSafeToast("등록된 도어락이 없습니다.")
            }
        }.addOnFailureListener { e ->
            showSafeToast("데이터 불러오기 실패: ${e.message}")
        }
    }

    private fun sendUnlockCommand(macAddress: String) {
        val statusRef = database.getReference("doorlocks").child(macAddress).child("status")
        val logsRef = database.getReference("doorlocks").child(macAddress).child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val updates = mapOf(
            "state" to "UNLOCK",
            "last_method" to "APP",
            "last_time" to currentTime,
            "door_closed" to false
        )

        statusRef.updateChildren(updates).addOnSuccessListener {
            showSafeToast("문 열림 신호를 보냈습니다.")
        }.addOnFailureListener {
            showSafeToast("명령 전송 실패: ${it.message}")
        }

        // 로그 기록
        val newLogKey = logsRef.push().key
        if (newLogKey != null) {
            val logData = mapOf(
                "method" to "APP",
                "state" to "UNLOCK",
                "time" to currentTime
            )
            logsRef.child(newLogKey).setValue(logData)
        }
    }

    private fun showSafeToast(message: String) {
        if (context != null && isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 리스너 해제 (메모리 누수 방지)
        if (statusListener != null && myLocksRef != null) {
            myLocksRef?.removeEventListener(statusListener!!)
        }
        _binding = null
    }
}