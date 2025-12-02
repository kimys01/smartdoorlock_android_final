package com.example.smartdoorlock.ui.profile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var selectedImageUri: Uri? = null

    // 갤러리 실행 결과 처리
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            selectedImageUri = data?.data
            if (selectedImageUri != null) {
                // 선택한 이미지 즉시 미리보기
                Glide.with(this).load(selectedImageUri).centerCrop().into(binding.imgProfile)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserInfo()

        // 1. 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // 2. 사진 변경 버튼 (카메라 아이콘 or 이미지 전체)
        val imageClickListener = View.OnClickListener {
            openGallery()
        }
        binding.btnChangeImage.setOnClickListener(imageClickListener)
        binding.cardProfileImageContainer.setOnClickListener(imageClickListener)

        // 3. 저장 버튼
        binding.btnSave.setOnClickListener {
            updateUserInfo()
        }

        // 4. 회원 탈퇴 버튼
        binding.btnWithdraw.setOnClickListener {
            showWithdrawDialog()
        }
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            // 이름
            binding.etName.setText(user.displayName)

            // 프로필 이미지
            if (user.photoUrl != null) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .centerCrop()
                    .into(binding.imgProfile)
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun updateUserInfo() {
        val user = auth.currentUser ?: return
        val newName = binding.etName.text.toString().trim()
        val newPw = binding.etPassword.text.toString().trim()
        val newPwConfirm = binding.etPasswordConfirm.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(context, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 비밀번호 변경 로직
        if (newPw.isNotEmpty()) {
            if (newPw != newPwConfirm) {
                Toast.makeText(context, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            if (newPw.length < 6) {
                Toast.makeText(context, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return
            }
            user.updatePassword(newPw).addOnFailureListener {
                Toast.makeText(context, "비밀번호 변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 이미지가 변경된 경우 업로드 후 프로필 업데이트
        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(user, newName)
        } else {
            // 이름만 변경된 경우
            saveProfileChanges(user, newName, null)
        }
    }

    private fun uploadImageAndSaveProfile(user: com.google.firebase.auth.FirebaseUser, name: String) {
        val ref = storage.reference.child("profile_images/${user.uid}.jpg")

        // 이미지 압축 (선택사항)
        val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, selectedImageUri)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()

        ref.putBytes(data)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    saveProfileChanges(user, name, uri)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                saveProfileChanges(user, name, null) // 실패해도 이름은 변경 시도
            }
    }

    private fun saveProfileChanges(user: com.google.firebase.auth.FirebaseUser, name: String, photoUri: Uri?) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)

        if (photoUri != null) {
            profileUpdates.setPhotoUri(photoUri)
        }

        user.updateProfile(profileUpdates.build())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // DB에도 이름 업데이트 (동기화)
                    val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                    val userId = prefs.getString("saved_id", null)
                    if (userId != null) {
                        database.getReference("users").child(userId).child("name").setValue(name)
                    }

                    Toast.makeText(context, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp() // 완료 후 뒤로가기
                } else {
                    Toast.makeText(context, "업데이트 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showWithdrawDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("회원 탈퇴")
            .setMessage("정말 탈퇴하시겠습니까? 모든 데이터가 삭제됩니다.")
            .setPositiveButton("탈퇴") { _, _ ->
                performWithdraw()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performWithdraw() {
        val user = auth.currentUser
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // DB 데이터 삭제 (선택사항)
                if (userId != null) {
                    database.getReference("users").child(userId).removeValue()
                }

                prefs.edit().clear().apply()
                Toast.makeText(context, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                // 로그인 화면 등으로 이동 (Global Action 사용 권장)
                // findNavController().navigate(R.id.action_global_login)
                activity?.finish() // 간단히 앱 종료 또는 재시작 처리
            } else {
                Toast.makeText(context, "탈퇴 실패: ${task.exception?.message}\n(재로그인 후 시도해주세요)", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}