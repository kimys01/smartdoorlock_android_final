package com.example.smartdoorlock.data

import java.util.HashMap

/**
 * Firebase Realtime Database 전체 구조
 * 경로: users/{username}
 */
data class User(
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val authMethod: String = "BLE",

    // [추가] 비밀번호 찾기용 휴대폰 번호
    val phoneNumber: String = "",

    // [도어락 상태 및 로그] (개인용 로그 저장소)
    val doorlock: UserDoorlock = UserDoorlock(),

    // [위치 로그 리스트]
    val location_logs: HashMap<String, LocationLog> = HashMap(),

    // [UWB 로그 리스트]
    val uwb_logs: HashMap<String, UwbLog> = HashMap(),

    // [상세 설정]
    val detailSettings: DetailSettings = DetailSettings(),

    // [앱 변경 로그 리스트]
    val app_logs: HashMap<String, AppLogItem> = HashMap(),

    val createdAt: Long = System.currentTimeMillis()
)

// --- 하위 데이터 모델 ---

data class AppLogItem(
    val message: String = "",
    val timestamp: String = ""
)

data class UwbLog(
    val front_distance: Double = 0.0,
    val back_distance: Double = 0.0,
    val timestamp: String = ""
)

data class LocationLog(
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: String = ""
)

data class DetailSettings(
    val autoLockEnabled: Boolean = true,
    val autoLockTime: Int = 5,
    val notifyOnLock: Boolean = true
)

data class UserDoorlock(
    val status: DoorlockStatus = DoorlockStatus(),
    // users/{id}/doorlock/logs 경로에 저장될 데이터
    val logs: HashMap<String, DoorlockLog> = HashMap()
)

data class DoorlockStatus(
    val door_closed: Boolean = true,
    val last_method: String = "NONE",
    val last_time: String = "",
    val state: String = "LOCK"
)

data class DoorlockLog(
    val method: String = "", // 예: APP, UWB_AUTO
    val state: String = "",  // 예: UNLOCK, LOCK
    val time: String = "",
    val user: String = ""    // [추가] 누가 열었는지 기록
)