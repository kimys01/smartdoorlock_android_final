package com.example.smartdoorlock.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.database.FirebaseDatabase

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        // ✅ 이름 변경
        binding.buttonUpdateName.setOnClickListener {
            val newName = binding.editTextNewName.text.toString().trim()

            if (newName.isNotEmpty()) {
                userRef.child("name").setValue(newName)
                    .addOnSuccessListener {
                        prefs.edit().putString("user_name", newName).apply()
                        Toast.makeText(context, "이름이 변경되었습니다", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "이름 변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "새 이름을 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ 비밀번호 변경
        binding.buttonUpdatePassword.setOnClickListener {
            val currentPassword = binding.editTextCurrentPassword.text.toString()
            val newPassword = binding.editTextNewPassword.text.toString()

            if (currentPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                userRef.child("password").get().addOnSuccessListener { snapshot ->
                    val savedPassword = snapshot.getValue(String::class.java)
                    if (savedPassword == currentPassword) {
                        userRef.child("password").setValue(newPassword)
                            .addOnSuccessListener {
                                Toast.makeText(context, "비밀번호가 변경되었습니다", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "현재 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
