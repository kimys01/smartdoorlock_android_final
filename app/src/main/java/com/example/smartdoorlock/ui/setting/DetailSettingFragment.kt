package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AppLogItem
import com.example.smartdoorlock.databinding.FragmentDetailSettingBinding
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class DetailSettingFragment : Fragment() {

    private var _binding: FragmentDetailSettingBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return
        val userRef = database.getReference("users").child(userId)

        userRef.child("detailSettings").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false

                binding.switchAutoLock.setOnCheckedChangeListener(null)
                binding.switchNotifyOnLock.setOnCheckedChangeListener(null)
                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify

                setupListeners(userRef)
            } else {
                setupListeners(userRef)
            }
        }
    }

    private fun setupListeners(userRef: com.google.firebase.database.DatabaseReference) {
        if (_binding == null) return

        // 자동 잠금
        binding.switchAutoLock.setOnCheckedChangeListener { _, isChecked ->
            val timestamp = getTime()
            val statusText = if (isChecked) "ON" else "OFF"

            // [핵심] 로그 리스트 추가
            val logMsg = "상세설정 변경(자동잠금): $statusText"
            val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

            userRef.child("detailSettings/autoLockEnabled").setValue(isChecked)
            userRef.child("detailSettings/autoLockTime").setValue(if (isChecked) 5 else 0)
            userRef.child("app_logs").push().setValue(logItem)
        }

        // 잠금 알림
        binding.switchNotifyOnLock.setOnCheckedChangeListener { _, isChecked ->
            val timestamp = getTime()
            val statusText = if (isChecked) "ON" else "OFF"

            val logMsg = "상세설정 변경(잠금알림): $statusText"
            val logItem = AppLogItem(message = logMsg, timestamp = timestamp)

            userRef.child("detailSettings/notifyOnLock").setValue(isChecked)
            userRef.child("app_logs").push().setValue(logItem)
        }
    }

    private fun getTime() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}