package com.example.smartdoorlock.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AppLogItem
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return
        val userRef = database.getReference("users").child(userId)

        // 이름 변경
        binding.buttonUpdateName.setOnClickListener {
            val newName = binding.editTextNewName.text.toString().trim()
            if (newName.isEmpty()) return@setOnClickListener

            userRef.child("name").get().addOnSuccessListener { snapshot ->
                val currentName = snapshot.getValue(String::class.java) ?: ""
                if (newName == currentName) return@addOnSuccessListener

                val timestamp = getTime()
                // [핵심] 리스트 형태로 로그 추가 (push 사용)
                val logMsg = "이름 변경: $currentName->$newName"
                val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

                userRef.child("name").setValue(newName)
                userRef.child("app_logs").push().setValue(logItem)

                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    prefs.edit().putString("user_name", newName).apply()
                    Toast.makeText(context, "이름 변경 완료", Toast.LENGTH_SHORT).show()
                    binding.editTextNewName.text.clear()
                }
            }
        }

        // 비밀번호 변경
        binding.buttonUpdatePassword.setOnClickListener {
            val currentPw = binding.editTextCurrentPassword.text.toString().trim()
            val newPw = binding.editTextNewPassword.text.toString().trim()

            if (currentPw.isEmpty() || newPw.isEmpty() || newPw.length < 6) return@setOnClickListener

            userRef.child("password").get().addOnSuccessListener { snapshot ->
                val dbPw = snapshot.getValue(String::class.java) ?: ""
                if (dbPw == currentPw) {
                    val timestamp = getTime()
                    val logMsg = "비밀번호 변경: $dbPw->$newPw"
                    val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

                    userRef.child("password").setValue(newPw)
                    userRef.child("app_logs").push().setValue(logItem)

                    auth.currentUser?.updatePassword(newPw)?.addOnSuccessListener {
                        Toast.makeText(context, "비밀번호 변경 완료", Toast.LENGTH_SHORT).show()
                        binding.editTextCurrentPassword.text.clear()
                        binding.editTextNewPassword.text.clear()
                    }
                } else {
                    Toast.makeText(context, "현재 비밀번호 불일치", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getTime() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}