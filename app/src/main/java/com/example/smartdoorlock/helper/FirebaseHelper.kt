package com.example.smartdoorlock.helper

import android.util.Log
import com.example.smartdoorlock.data.DoorLockLog
import com.google.firebase.database.FirebaseDatabase

object FirebaseHelper {

    fun saveDoorLockLog(log: DoorLockLog) {
        val db = FirebaseDatabase.getInstance()
        val ref = db.getReference("doorlock_logs")

        // 고유 키 생성 (예: kimys_20250619_031022)
        val key = "${log.user_id}_${log.timestamp.replace(":", "").replace(" ", "_")}"

        ref.child(key).setValue(log)
            .addOnSuccessListener {
                Log.d("Firebase", "로그 저장 성공")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "저장 실패: ${e.message}")
            }
    }
}
