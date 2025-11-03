package com.example.smartdoorlock.ui.login

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentLoginBinding
import com.google.firebase.database.*

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Firebase DB 초기화
        database = FirebaseDatabase.getInstance().getReference("users")

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("saved_id", "")
        val autoLogin = prefs.getBoolean("auto_login", false)

        binding.editTextId.setText(savedId)
        binding.checkboxSaveId.isChecked = !savedId.isNullOrEmpty()
        binding.checkboxAutoLogin.isChecked = autoLogin

        // ✅ 자동 로그인 처리
        if (autoLogin && !savedId.isNullOrEmpty()) {
            Toast.makeText(context, "자동 로그인 중...", Toast.LENGTH_SHORT).show()
            Log.d("LoginFragment", "자동 로그인: $savedId")
            navigateToDashboard()
            return
        }

        // ✅ 로그인 버튼 클릭
        binding.buttonLogin.setOnClickListener {
            val userId = binding.editTextId.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "아이디와 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(context, "로그인 시도 중...", Toast.LENGTH_SHORT).show()
            Log.d("LoginFragment", "로그인 시도: $userId")

            database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val dbPassword = snapshot.child("password").getValue(String::class.java)
                        val userName = snapshot.child("name").getValue(String::class.java)

                        if (dbPassword == password) {
                            val editor = prefs.edit()
                            editor.putString("user_name", userName ?: "")
                            editor.putString("saved_id", userId)
                            editor.putBoolean("auto_login", binding.checkboxAutoLogin.isChecked)
                            editor.apply()

                            Toast.makeText(context, "로그인 성공", Toast.LENGTH_SHORT).show()
                            Log.d("LoginFragment", "로그인 성공: $userId")
                            navigateToDashboard()
                        } else {
                            Toast.makeText(context, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                            Log.d("LoginFragment", "비밀번호 불일치")
                        }
                    } else {
                        Toast.makeText(context, "존재하지 않는 사용자입니다.", Toast.LENGTH_SHORT).show()
                        Log.d("LoginFragment", "사용자 없음: $userId")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "데이터베이스 오류: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("LoginFragment", "DB 오류: ${error.message}")
                }
            })
        }

        // ✅ 회원가입 버튼 클릭
        binding.buttonSignUp.setOnClickListener {
            Log.d("LoginFragment", "회원가입 버튼 클릭됨")
            findNavController().navigate(R.id.navigation_register)
        }
    }

    private fun navigateToDashboard() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_login, true)
            .build()
        findNavController().navigate(R.id.navigation_dashboard, null, navOptions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
