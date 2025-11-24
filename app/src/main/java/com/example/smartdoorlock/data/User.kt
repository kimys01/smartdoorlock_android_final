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

    // [도어락 상태 및 로그]
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
    val logs: HashMap<String, DoorlockLog> = HashMap()
)

data class DoorlockStatus(
    val door_closed: Boolean = true,
    val last_method: String = "NONE",
    val last_time: String = "",
    val state: String = "LOCK"
)

data class DoorlockLog(
    val method: String = "",
    val state: String = "",
    val time: String = ""
)

// (하위 호환성을 위해 남겨둠, 신규 로직에서는 AppLogItem 사용)
data class AppLogs(val change: ChangeLogs = ChangeLogs())
data class ChangeLogs(
    val auth: AuthLog? = null,
    val name: NameLog? = null,
    val password: PasswordLog? = null,
    val detail: DetailLog? = null
)
data class AuthLog(val new_auth: String = "", val timestamp: String = "")
data class NameLog(val new_name: String = "", val timestamp: String = "")
data class PasswordLog(val new_pw: String = "", val timestamp: String = "")
data class DetailLog(val new_detail: String = "", val timestamp: String = "")