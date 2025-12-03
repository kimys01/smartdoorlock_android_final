package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
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

    private lateinit var memberAdapter: MemberAdapter
    private val memberList = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        memberAdapter = MemberAdapter(memberList)
        binding.recyclerViewMembers.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewMembers.adapter = memberAdapter

        loadUserProfile()
        checkRegisteredDeviceAndMembers()

        // [복구] 직접 갤러리를 여는 대신, 정보 수정 화면으로 이동하도록 변경
        binding.btnEditProfile.setOnClickListener {
            safeNavigate(R.id.navigation_user_update)
        }

        binding.btnConnectDevice.setOnClickListener { safeNavigate(R.id.action_profile_to_scan) }
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

        binding.tvUserName.text = currentUser.displayName ?: "사용자"
        binding.tvUserId.text = "@$userId"

        // 실시간 DB 감시 (수정 화면에서 변경 후 돌아왔을 때 즉시 반영됨)
        database.getReference("users").child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                // 1. 이름 업데이트
                val name = snapshot.child("name").getValue(String::class.java)
                if (!name.isNullOrEmpty()) {
                    binding.tvUserName.text = name
                }

                // 2. 이미지 URL 업데이트
                val dbProfileImage = snapshot.child("profileImage").getValue(String::class.java)
                val targetUrl: Any? = if (!dbProfileImage.isNullOrEmpty()) dbProfileImage else currentUser.photoUrl

                if (targetUrl != null) {
                    if (isAdded && context != null) {
                        // Glide 캐시 전략 및 에러 처리
                        Glide.with(this@ProfileFragment)
                            .load(targetUrl)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .listener(object : RequestListener<Drawable> {
                                // [수정됨] 중복 제거 및 올바른 시그니처 (model: Any?, target: Target<Drawable>?)

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    if (_binding != null) binding.imgUserProfile.clearColorFilter()
                                    return false
                                }

                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable?>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    TODO("Not yet implemented")
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable?>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    TODO("Not yet implemented")
                                }
                            })
                            .placeholder(android.R.drawable.sym_def_app_icon)
                            .error(android.R.drawable.sym_def_app_icon)
                            .into(binding.imgUserProfile)
                    }
                } else {
                    binding.imgUserProfile.setImageResource(android.R.drawable.sym_def_app_icon)
                    binding.imgUserProfile.setColorFilter(Color.parseColor("#CCCCCC"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (currentUser.photoUrl != null && isAdded) {
                    Glide.with(this@ProfileFragment).load(currentUser.photoUrl).centerCrop().into(binding.imgUserProfile)
                    binding.imgUserProfile.clearColorFilter()
                }
            }
        })
    }

    private fun checkRegisteredDeviceAndMembers() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return

        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                if (snapshot.exists() && snapshot.hasChildren()) {
                    binding.cardViewRegistered.visibility = View.VISIBLE
                    binding.btnConnectDevice.visibility = View.GONE

                    val macOrId = snapshot.children.first().key ?: return@addOnSuccessListener

                    binding.tvRegisteredMac.text = "ID: $macOrId"

                    loadDoorlockMembers(macOrId)
                } else {
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
                memberAdapter.notifyDataSetChanged()

                for (child in snapshot.children) {
                    val memberId = child.key ?: continue
                    val role = child.getValue(String::class.java)

                    database.getReference("users").child(memberId).child("name").get()
                        .addOnSuccessListener { nameSnap ->
                            val name = nameSnap.getValue(String::class.java) ?: memberId
                            val displayName = if (role == "admin") "$name 👑" else name
                            if (!memberList.contains(displayName)) {
                                memberList.add(displayName)
                                memberAdapter.notifyDataSetChanged()
                            }
                        }
                        .addOnFailureListener {
                            val displayName = if (role == "admin") "$memberId 👑" else memberId
                            if (!memberList.contains(displayName)) {
                                memberList.add(displayName)
                                memberAdapter.notifyDataSetChanged()
                            }
                        }
                }
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
            holder.tvName.setTextColor(Color.parseColor("#4B5563"))
            holder.tvName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_open, 0, 0, 0)
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