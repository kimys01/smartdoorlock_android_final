package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentAuthBinding

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRequestAuth.setOnClickListener {
            val userId = binding.editTextUserId.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "아이디와 비밀번호를 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 실제 인증 로직은 서버와 연동 예정
            if (userId == "admin" && password == "1234") {
                binding.textAuthStatus.text = "인증 상태: 성공"
                binding.textAuthStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            } else {
                binding.textAuthStatus.text = "인증 상태: 실패"
                binding.textAuthStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
