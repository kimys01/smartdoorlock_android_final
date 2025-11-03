package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentDetailSettingBinding
import com.google.firebase.database.*

class DetailSettingFragment : Fragment() {

    private var _binding: FragmentDetailSettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var userId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        userId = prefs.getString("saved_id", "") ?: ""
        database = FirebaseDatabase.getInstance().getReference("users").child(userId).child("detailSettings")

        // 기존 설정 불러오기
        loadDetailSettings()

        // 저장 버튼 클릭
        binding.buttonSaveDetailSettings.setOnClickListener {
            val autoLockEnabled = binding.switchAutoLock.isChecked
            val notifyOnLock = binding.switchNotifyOnLock.isChecked

            val settings = mapOf(
                "autoLockEnabled" to autoLockEnabled,
                "autoLockTime" to if (autoLockEnabled) 5 else 0, // 기본 5초
                "notifyOnLock" to notifyOnLock
            )

            database.setValue(settings)
                .addOnSuccessListener {
                    Toast.makeText(context, "설정 저장 완료", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadDetailSettings() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.switchAutoLock.isChecked =
                    snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false

                binding.switchNotifyOnLock.isChecked =
                    snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "설정 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
