package com.example.smartdoorlock.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.*
import com.example.smartdoorlock.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashMap

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        binding.buttonRegister.setOnClickListener {
            val id = binding.editTextId.text.toString().trim()
            val pw = binding.editTextPassword.text.toString().trim()
            val name = binding.editTextName.text.toString().trim()

            // 1. 빈 값 체크
            if (id.isEmpty() || pw.isEmpty() || name.isEmpty()) {
                Toast.makeText(context, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 아이디 유효성 검사 (영어, 숫자만 허용)
            if (!Pattern.matches("^[a-zA-Z0-9]*$", id)) {
                Toast.makeText(context, "아이디는 영어와 숫자만 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. 비밀번호 길이 체크
            if (pw.length < 6) {
                Toast.makeText(context, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(id, pw, name)
        }
    }

    private fun registerUser(username: String, password: String, name: String) {
        binding.buttonRegister.isEnabled = false

        // 아이디를 이메일 형식으로 변환 (Firebase Auth 요구사항)
        val fakeEmail = "$username@doorlock.com"

        auth.createUserWithEmailAndPassword(fakeEmail, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid

                if (uid != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    user.updateProfile(profileUpdates).addOnCompleteListener {
                        saveFullUserStructure(username, password, name)
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.buttonRegister.isEnabled = true
                // 에러 메시지 한국어 변환 (선택 사항)
                val errorMsg = when {
                    e.message?.contains("email") == true -> "이미 사용 중인 아이디입니다."
                    else -> "가입 실패: ${e.message}"
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFullUserStructure(username: String, password: String, name: String) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 초기 로그 생성
        val initialLogs = HashMap<String, AppLogItem>()
        val logKey = database.reference.push().key ?: "init_log"
        initialLogs[logKey] = AppLogItem("계정 생성: Initial Set", currentTime)

        val initialDoorlock = UserDoorlock(
            status = DoorlockStatus(true, "INIT", currentTime, "LOCK")
        )

        val newUser = User(
            username = username,
            password = password,
            name = name,
            authMethod = "BLE",
            detailSettings = DetailSettings(true, 5, true),
            app_logs = initialLogs,
            doorlock = initialDoorlock,
            uwb_logs = HashMap(),
            location_logs = HashMap()
        )

        database.getReference("users").child(username).setValue(newUser)
            .addOnSuccessListener {
                Toast.makeText(context, "회원가입 완료!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.navigation_login)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "DB 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.buttonRegister.isEnabled = true
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}