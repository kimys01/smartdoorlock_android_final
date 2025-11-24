package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AppLogItem
import com.example.smartdoorlock.databinding.FragmentAuthMethodBinding
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class AuthMethodFragment : Fragment() {

    private var _binding: FragmentAuthMethodBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuthMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) return

        val userRef = database.getReference("users").child(userId)

        userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val method = snapshot.getValue(String::class.java)
                when (method) {
                    "BLE" -> binding.radioBle.isChecked = true
                    "UWB" -> binding.radioUwb.isChecked = true
                    "RFID" -> binding.radioRfid.isChecked = true
                    "Password" -> binding.radioPassword.isChecked = true
                }
            }
        }

        binding.buttonUpdateAuthMethod.setOnClickListener {
            val newMethod = when {
                binding.radioBle.isChecked -> "BLE"
                binding.radioUwb.isChecked -> "UWB"
                binding.radioRfid.isChecked -> "RFID"
                binding.radioPassword.isChecked -> "Password"
                else -> ""
            }

            if (newMethod.isEmpty()) return@setOnClickListener

            userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
                val currentMethod = snapshot.getValue(String::class.java) ?: "None"

                if (currentMethod == newMethod) {
                    Toast.makeText(context, "변경 사항 없음", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // [핵심] 로그 리스트 추가
                val logMessage = "인증방식 변경: $currentMethod->$newMethod"
                val logItem = AppLogItem(message = logMessage, timestamp = timestamp)

                userRef.child("authMethod").setValue(newMethod)
                userRef.child("app_logs").push().setValue(logItem)
                    .addOnSuccessListener {
                        Toast.makeText(context, "인증 방식 변경 완료", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}