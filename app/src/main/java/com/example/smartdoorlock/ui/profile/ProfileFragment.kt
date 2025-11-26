package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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

    // 멤버 리스트 어댑터
    private lateinit var memberAdapter: MemberAdapter
    private val memberList = ArrayList<String>() // 멤버 ID 목록

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 리사이클러뷰 설정
        memberAdapter = MemberAdapter(memberList)
        binding.recyclerViewMembers.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewMembers.adapter = memberAdapter

        loadUserProfile()
        checkRegisteredDeviceAndMembers()

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

        binding.tvUserName.text = currentUser.displayName ?: "사용자"
        binding.tvUserId.text = "ID: $userId"

        val photoUrl = currentUser.photoUrl
        if (photoUrl != null) {
            Glide.with(this).load(photoUrl).circleCrop().into(binding.imgUserProfile)
        } else {
            binding.imgUserProfile.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        database.getReference("users").child(userId).child("name").get().addOnSuccessListener {
            val name = it.getValue(String::class.java)
            if (!name.isNullOrEmpty()) binding.tvUserName.text = name
        }
    }

    private fun checkRegisteredDeviceAndMembers() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return

        // 1. 내 도어락 목록 확인
        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    binding.cardViewRegistered.visibility = View.VISIBLE
                    binding.btnConnectDevice.visibility = View.GONE

                    val mac = snapshot.children.first().key ?: return@addOnSuccessListener
                    binding.tvRegisteredMac.text = "MAC: $mac"

                    // 2. 해당 도어락의 멤버 목록 가져오기
                    loadDoorlockMembers(mac)
                } else {
                    binding.cardViewRegistered.visibility = View.GONE
                    binding.btnConnectDevice.visibility = View.VISIBLE
                }
            }
    }

    private fun loadDoorlockMembers(mac: String) {
        val membersRef = database.getReference("doorlocks").child(mac).child("members")
        membersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                memberList.clear()
                for (child in snapshot.children) {
                    val memberId = child.key
                    val role = child.getValue(String::class.java) // "admin" or "member"
                    if (memberId != null) {
                        // 표시 형식: "아이디 (권한)"
                        memberList.add("$memberId ($role)")
                    }
                }
                memberAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠습니까?")
            .setPositiveButton("확인") { _, _ -> performLogout() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        safeNavigate(R.id.action_global_login)
    }

    private fun safeNavigate(id: Int) {
        try {
            findNavController().navigate(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 내부 어댑터 클래스 ---
    class MemberAdapter(private val members: List<String>) : RecyclerView.Adapter<MemberAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // 안드로이드 기본 레이아웃 사용 (simple_list_item_1)
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvName.text = members[position]
            holder.tvName.textSize = 14f
            holder.tvName.setTextColor(android.graphics.Color.parseColor("#374151"))
        }

        override fun getItemCount() = members.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}