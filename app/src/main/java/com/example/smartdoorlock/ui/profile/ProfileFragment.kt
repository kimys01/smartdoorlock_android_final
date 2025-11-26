package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide // Glide 추가
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

        loadUserProfile()
        checkRegisteredDevice()

        binding.btnEditProfile.setOnClickListener { safeNavigate(R.id.navigation_user_update) }
        binding.btnConnectDevice.setOnClickListener { safeNavigate(R.id.action_profile_to_scan) }
        binding.btnLogout.setOnClickListener { showLogoutConfirmationDialog() }
    }

    private fun loadUserProfile() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)
        val currentUser = auth.currentUser

        if (userId == null || currentUser == null) {
            binding.tvUserName.text = "게스트"
            binding.tvUserId.text = "로그인 정보 없음"
            return
        }

        // 1. 이름 및 아이디 설정
        // Auth에서 displayName을 먼저 시도하고, 없으면 DB에서 가져옴
        binding.tvUserName.text = currentUser.displayName ?: "사용자"
        binding.tvUserId.text = "ID: $userId"

        // 2. [핵심] 프로필 이미지 로드 (Glide 사용)
        val photoUrl = currentUser.photoUrl
        if (photoUrl != null) {
            // 이미지가 있으면 로드
            Glide.with(this)
                .load(photoUrl)
                .circleCrop()
                .into(binding.imgUserProfile) // XML ID 확인 필요
        } else {
            // 없으면 기본 이미지
            binding.imgUserProfile.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // DB에서 최신 정보 동기화 (이름 등)
        database.getReference("users").child(userId).child("name").get().addOnSuccessListener {
            val name = it.getValue(String::class.java)
            if (!name.isNullOrEmpty()) binding.tvUserName.text = name
        }
    }

    // ... (checkRegisteredDevice, showLogoutConfirmationDialog, safeNavigate 등 기존 코드 유지) ...
    private fun checkRegisteredDevice() { /* 기존 코드 */ }
    private fun showLogoutConfirmationDialog() { /* 기존 코드 */ }
    private fun performLogout() { /* 기존 코드 */ }
    private fun safeNavigate(id: Int) { /* 기존 코드 */ }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}