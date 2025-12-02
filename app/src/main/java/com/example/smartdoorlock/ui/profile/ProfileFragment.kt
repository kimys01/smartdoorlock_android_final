package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
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
    private val memberList = ArrayList<String>()

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

        // 데이터 로드
        loadUserProfile()
        checkRegisteredDeviceAndMembers()

        // 클릭 리스너 설정 (새로운 ID에 맞게 연결)
        // 1. 프로필 수정 (카메라 버튼)
        binding.btnEditProfile.setOnClickListener { safeNavigate(R.id.navigation_user_update) }

        // 2. 기기 등록 (기기 없을 때 버튼)
        binding.btnConnectDevice.setOnClickListener { safeNavigate(R.id.action_profile_to_scan) }

        // 3. 로그아웃 (상단 우측 설정 아이콘)
        binding.btnLogout.setOnClickListener { showLogoutConfirmationDialog() }
    }

    private fun loadUserProfile() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)
        val currentUser = auth.currentUser

        if (userId == null || currentUser == null) {
            binding.tvUserName.text = "게스트"
            binding.tvUserId.text = "로그인이 필요합니다"
            return
        }

        // 이름 및 아이디 설정
        binding.tvUserName.text = currentUser.displayName ?: "사용자"
        binding.tvUserId.text = "@$userId" // ID 앞에 @ 붙여서 스타일링

        // 프로필 이미지 로드
        val photoUrl = currentUser.photoUrl
        if (photoUrl != null) {
            Glide.with(this)
                .load(photoUrl)
                .centerCrop() // 이미지를 꽉 채우도록
                .into(binding.imgUserProfile)
        } else {
            // 기본 이미지 (배경색 흰색, 아이콘 회색)
            binding.imgUserProfile.setImageResource(android.R.drawable.sym_def_app_icon)
            binding.imgUserProfile.setColorFilter(Color.parseColor("#CCCCCC"))
        }

        // DB에서 최신 이름 가져오기 (동기화)
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
                if (_binding == null) return@addOnSuccessListener

                if (snapshot.exists() && snapshot.hasChildren()) {
                    // 기기 있음 -> 카드 표시, 추가 버튼 숨김
                    binding.cardViewRegistered.visibility = View.VISIBLE
                    binding.btnConnectDevice.visibility = View.GONE

                    val macOrId = snapshot.children.first().key ?: return@addOnSuccessListener

                    // 도어락 ID로 실제 MAC 주소 가져오기 (표시용)
                    database.getReference("doorlocks").child(macOrId).child("mac").get()
                        .addOnSuccessListener { macSnap ->
                            val realMac = macSnap.getValue(String::class.java) ?: macOrId
                            binding.tvRegisteredMac.text = "ID: $realMac"
                        }

                    // 2. 해당 도어락의 멤버 목록 가져오기
                    loadDoorlockMembers(macOrId)
                } else {
                    // 기기 없음 -> 카드 숨김, 추가 버튼 표시
                    binding.cardViewRegistered.visibility = View.GONE
                    binding.btnConnectDevice.visibility = View.VISIBLE
                }
            }
    }

    private fun loadDoorlockMembers(doorlockId: String) {
        val membersRef = database.getReference("doorlocks").child(doorlockId).child("members")
        membersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                memberList.clear()
                for (child in snapshot.children) {
                    val memberId = child.key
                    val role = child.getValue(String::class.java) // "admin" or "member"
                    if (memberId != null) {
                        // 관리자는 왕관 표시, 일반 멤버는 그냥 이름
                        val displayName = if (role == "admin") "$memberId 👑" else memberId
                        memberList.add(displayName)
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
            .setPositiveButton("로그아웃") { _, _ -> performLogout() }
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

    // --- 내부 어댑터 클래스 (디자인 개선) ---
    class MemberAdapter(private val members: List<String>) : RecyclerView.Adapter<MemberAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvName.text = members[position]
            holder.tvName.textSize = 14f
            holder.tvName.setTextColor(Color.parseColor("#4B5563")) // 회색 텍스트
            // 아이콘 추가 (선택사항)
            holder.tvName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_home_black_24dp, 0, 0, 0)
            holder.tvName.compoundDrawablePadding = 24
            holder.tvName.compoundDrawables[0]?.setTint(Color.parseColor("#9CA3AF"))
        }

        override fun getItemCount() = members.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}