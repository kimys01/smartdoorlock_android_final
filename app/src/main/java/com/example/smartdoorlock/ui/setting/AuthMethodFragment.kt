package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentAuthMethodBinding
import com.google.firebase.database.*

class AuthMethodFragment : Fragment() {

    private var _binding: FragmentAuthMethodBinding? = null
    private val binding get() = _binding!!

    private lateinit var userId: String
    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 사용자 ID 로드
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        userId = prefs.getString("saved_id", "") ?: ""

        database = FirebaseDatabase.getInstance().getReference("users").child(userId)

        // ✅ 기존 인증 방식 불러오기
        loadAuthMethod()

        // ✅ 저장 버튼 클릭 시 인증 방식 저장
        binding.buttonUpdateAuthMethod.setOnClickListener {
            val selectedMethod = when {
                binding.radioBle.isChecked -> "BLE"
                binding.radioRfid.isChecked -> "RFID"
                binding.radioPassword.isChecked -> "Password"
                else -> ""
            }

            if (selectedMethod.isNotEmpty()) {
                database.child("authMethod").setValue(selectedMethod)
                    .addOnSuccessListener {
                        Toast.makeText(context, "인증 방식 저장 완료", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "인증 방식을 선택하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAuthMethod() {
        database.child("authMethod").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val method = snapshot.getValue(String::class.java)
                when (method) {
                    "BLE" -> binding.radioBle.isChecked = true
                    "RFID" -> binding.radioRfid.isChecked = true
                    "Password" -> binding.radioPassword.isChecked = true
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "데이터 불러오기 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
