package com.example.smartdoorlock.ui.register

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.User
import com.example.smartdoorlock.databinding.FragmentRegisterBinding
import com.google.firebase.database.FirebaseDatabase

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("RegisterFragment", "onViewCreated 호출됨")

        // 회원가입 버튼 클릭 리스너 설정
        binding.buttonRegister.setOnClickListener {
            Log.d("RegisterFragment", "회원가입 버튼 클릭됨")

            val username = binding.editTextId.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            val name = binding.editTextName.text.toString().trim()

            // 빈 필드 체크
            if (username.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(context, "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비밀번호 길이 체크
            if (password.length < 6) {
                Toast.makeText(context, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 사용자 등록
            registerUser(username, password, name)
        }
    }

    private fun registerUser(username: String, password: String, name: String) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("users")
        val user = User(username, password, name)

        // username이 이미 존재하는지 확인
        usersRef.child(username).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // 동일한 username이 존재할 경우
                Toast.makeText(context, "이미 존재하는 사용자입니다.", Toast.LENGTH_SHORT).show()
            } else {
                // 새로운 사용자 저장
                usersRef.child(username).setValue(user)
                    .addOnSuccessListener {
                        Log.d("RegisterFragment", "회원가입 성공: $username")
                        Toast.makeText(context, "회원가입 완료", Toast.LENGTH_SHORT).show()

                        // 회원가입 후 로그인 화면으로 이동
                        findNavController().navigate(R.id.navigation_login)
                    }
                    .addOnFailureListener { exception ->
                        Log.e("RegisterFragment", "회원가입 실패: ${exception.message}")
                        Toast.makeText(context, "회원가입 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }.addOnFailureListener { exception ->
            Log.e("RegisterFragment", "Firebase 데이터 읽기 실패: ${exception.message}")
            Toast.makeText(context, "회원가입 실패: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
