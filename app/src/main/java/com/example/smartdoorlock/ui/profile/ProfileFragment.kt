package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentProfileBinding
import com.google.firebase.database.FirebaseDatabase

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // private lateinit var profileViewModel: ProfileViewModel <-- 변수 선언 제거

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "비로그인") ?: "비로그인"
        val userName = prefs.getString("user_name", "이름 없음") ?: "이름 없음"

        binding.textUserId.text = "아이디: $userId"
        binding.textUserName.text = "이름: $userName"

        // ✅ 내 정보 수정 (이름 + 비밀번호)
        binding.buttonEditProfile.setOnClickListener {
            findNavController().navigate(R.id.navigation_user_update)
        }

        // ✅ 로그아웃
        binding.buttonLogout.setOnClickListener {
            prefs.edit().putBoolean("auto_login", false).apply()
            Toast.makeText(context, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.navigation_login)
        }

        // ✅ 계정 삭제 (미구현)
        binding.buttonDeleteAccount.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("⚠ 계정 삭제 확인")
                .setMessage("정말로 계정을 삭제하시겠습니까?\n삭제하면 복구할 수 없습니다.")
                .setPositiveButton("삭제") { _, _ ->
                    val userId = prefs.getString("saved_id", null)
                    if (userId != null) {
                        val database = FirebaseDatabase.getInstance().getReference("users")
                        database.child(userId).removeValue()
                            .addOnSuccessListener {
                                prefs.edit().clear().apply() // 로그인 정보 제거
                                Toast.makeText(context, "계정이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.navigation_login)
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "계정 삭제 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "저장된 계정 정보가 없습니다", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

