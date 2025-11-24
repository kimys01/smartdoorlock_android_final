package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog // 다이얼로그 import
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 등록된 기기 확인 로직
        checkRegisteredDevice()

        // 1. 정보 수정
        binding.btnEditProfile.setOnClickListener {
            safeNavigate(R.id.navigation_user_update)
        }

        // 2. 기기 연결 (새 기기 추가)
        binding.btnConnectDevice.setOnClickListener {
            safeNavigate(R.id.action_profile_to_scan)
        }

        // 3. [핵심 수정] 로그아웃 버튼 클릭 시 확인 팝업 띄우기
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    // 로그아웃 확인 다이얼로그 표시 함수
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                // 확인 버튼을 눌렀을 때만 로그아웃 수행
                performLogout()
            }
            .setNegativeButton("취소", null) // 취소 시 아무것도 안 함
            .show()
    }

    // 실제 로그아웃 로직
    private fun performLogout() {
        // 1. Firebase 로그아웃
        FirebaseAuth.getInstance().signOut()

        // 2. 자동 로그인 정보 삭제 (SharedPreferences)
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // 3. 로그인 화면으로 이동 (스택 비우기)
        findNavController().navigate(R.id.action_global_login)
    }

    // DB에서 내 도어락 확인
    private fun checkRegisteredDevice() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return

        val myLocksRef = database.getReference("users").child(userId).child("my_doorlocks")

        myLocksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    binding.cardViewRegistered.visibility = View.VISIBLE
                    val firstMac = snapshot.children.first().key
                    binding.tvRegisteredMac.text = "MAC: $firstMac"
                } else {
                    binding.cardViewRegistered.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) binding.cardViewRegistered.visibility = View.GONE
            }
        })
    }

    private fun safeNavigate(actionId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.navigation_profile) {
            try {
                navController.navigate(actionId)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}