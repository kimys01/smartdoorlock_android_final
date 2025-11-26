package com.example.smartdoorlock.ui.profile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.smartdoorlock.data.AppLogItem
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // 갤러리 이미지 선택 런처
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadProfileImage(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return
        val userRef = database.getReference("users").child(userId)

        // 초기화: 현재 프로필 사진 불러오기
        val currentPhotoUrl = auth.currentUser?.photoUrl
        if (currentPhotoUrl != null) {
            Glide.with(this).load(currentPhotoUrl).circleCrop().into(binding.imgProfilePreview)
        }

        // [1] 사진 변경 버튼 클릭
        binding.btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*") // 갤러리 열기
        }

        // [2] 이름 변경
        binding.buttonUpdateName.setOnClickListener {
            val newName = binding.editTextNewName.text.toString().trim()
            if (newName.isEmpty()) return@setOnClickListener

            userRef.child("name").get().addOnSuccessListener { snapshot ->
                val currentName = snapshot.getValue(String::class.java) ?: ""
                if (newName == currentName) return@addOnSuccessListener

                val timestamp = getTime()
                val logItem = AppLogItem("이름 변경: $currentName -> $newName", timestamp)

                userRef.child("name").setValue(newName)
                userRef.child("app_logs").push().setValue(logItem)

                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    prefs.edit().putString("user_name", newName).apply()
                    Toast.makeText(context, "이름 변경 완료", Toast.LENGTH_SHORT).show()

                    // [수정] Nullable 타입 처리 (?.) 추가
                    binding.editTextNewName.text?.clear()
                }
            }
        }

        // [3] 비밀번호 변경
        binding.buttonUpdatePassword.setOnClickListener {
            val currentPw = binding.editTextCurrentPassword.text.toString().trim()
            val newPw = binding.editTextNewPassword.text.toString().trim()

            if (currentPw.isEmpty() || newPw.isEmpty() || newPw.length < 6) return@setOnClickListener

            userRef.child("password").get().addOnSuccessListener { snapshot ->
                val dbPw = snapshot.getValue(String::class.java) ?: ""
                if (dbPw == currentPw) {
                    val timestamp = getTime()
                    val logItem = AppLogItem("비밀번호 변경 완료", timestamp)

                    userRef.child("password").setValue(newPw)
                    userRef.child("app_logs").push().setValue(logItem)

                    auth.currentUser?.updatePassword(newPw)?.addOnSuccessListener {
                        Toast.makeText(context, "비밀번호 변경 완료", Toast.LENGTH_SHORT).show()

                        // [수정] Nullable 타입 처리 (?.) 추가
                        binding.editTextCurrentPassword.text?.clear()
                        binding.editTextNewPassword.text?.clear()
                    }
                } else {
                    Toast.makeText(context, "현재 비밀번호 불일치", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 이미지 업로드 및 URL 저장 로직
    private fun uploadProfileImage(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val userId = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE).getString("saved_id", "") ?: ""

        // 파일 경로: profile_images/{uid}.jpg
        val storageRef = storage.reference.child("profile_images/$uid.jpg")

        Toast.makeText(context, "사진 업로드 중...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(uri)
            .addOnSuccessListener {
                // 업로드 성공 -> 다운로드 URL 가져오기
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    updateProfileImageUrl(downloadUri.toString(), userId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "업로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfileImageUrl(url: String, userId: String) {
        val timestamp = getTime()
        val userRef = database.getReference("users").child(userId)

        // 1. Firebase Auth 프로필 업데이트 (Photo URL)
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(Uri.parse(url))
            .build()

        auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // 2. Realtime Database 업데이트
                userRef.child("profileImageUrl").setValue(url)

                // 3. 로그 기록
                val logItem = AppLogItem("프로필 사진 변경됨", timestamp)
                userRef.child("app_logs").push().setValue(logItem)

                // 4. 화면 갱신
                Glide.with(this).load(url).circleCrop().into(binding.imgProfilePreview)
                Toast.makeText(context, "프로필 사진이 변경되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getTime() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}