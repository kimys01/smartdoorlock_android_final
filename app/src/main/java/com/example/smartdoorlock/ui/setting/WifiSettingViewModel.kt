package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.smartdoorlock.data.DetailSettings
import com.example.smartdoorlock.data.Doorlock
import com.example.smartdoorlock.data.FixedLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // 아두이노 v5.0과 일치하는 UUID
        val PROV_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        val WIFI_CTRL_UUID: UUID = UUID.fromString("abcd1234-5678-90ab-cdef-1234567890ab")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    private val _statusText = MutableLiveData<String>("기기 연결 대기 중...")
    val statusText: LiveData<String> = _statusText

    private val _isBleConnected = MutableLiveData<Boolean>(false)
    val isBleConnected: LiveData<Boolean> = _isBleConnected

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetAddress: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getSavedUserId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        return prefs.getString("saved_id", null)
    }

    // 1. 기기 연결 시도
    fun connectToDevice(address: String) {
        targetAddress = address
        _statusText.value = "도어락에 연결을 시도합니다..."
        connectGatt(address)
    }

    // 2. 앱 관리자 인증 (테스트용 123456 / 1234qwer 포함)
    fun verifyAppAdmin(inputId: String, inputPw: String) {
        val trimId = inputId.trim()
        val trimPw = inputPw.trim()

        if (trimId == "123456" && trimPw == "1234qwer") {
            _statusText.value = "테스트 계정 승인. 설정 진행..."
            _currentStep.value = 2
            return
        }

        val userId = getSavedUserId()
        if (userId == null) {
            _statusText.value = "오류: 앱 로그인 정보 없음. 다시 로그인하세요."
            return
        }

        _statusText.value = "서버 정보 확인 중..."

        db.getReference("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val dbId = snapshot.child("username").getValue(String::class.java)?.trim() ?: ""
                    val dbPw = snapshot.child("password").getValue(String::class.java)?.trim() ?: ""

                    if (dbId == trimId && dbPw == trimPw) {
                        _statusText.value = "본인 확인 완료. Wi-Fi 설정 이동."
                        _currentStep.value = 2
                    } else {
                        _statusText.value = "인증 실패: 정보 불일치"
                    }
                } else {
                    _statusText.value = "오류: 회원 정보를 찾을 수 없습니다."
                }
            }
            .addOnFailureListener { e ->
                _statusText.value = "서버 연결 실패: ${e.message}"
            }
    }

    // 3. 설정값 전송 (위치 정보 포함)
    fun sendWifiSettingsWithLocation(ssid: String, pw: String, lat: Double, lon: Double, alt: Double) {
        if (bluetoothGatt == null || _isBleConnected.value == false) {
            _statusText.value = "BLE 연결이 끊어졌습니다. 다시 연결해주세요."
            return
        }

        _statusText.value = "기기 등록 상태 확인 중..."

        // 이미 등록된 기기인지 확인
        findExistingDoorlockId(targetAddress) { existingId ->
            val finalId: String

            if (existingId != null) {
                finalId = existingId
                Log.d("WifiSetting", "기존 ID 유지: $finalId")
                _statusText.value = "기존 설정 유지하며 Wi-Fi 변경 중..."
            } else {
                finalId = generateShortId()
                Log.d("WifiSetting", "새 ID 생성: $finalId")
                _statusText.value = "새 도어락 등록 중..."
            }

            // 위치 정보와 함께 등록
            if (lat == 0.0 && lon == 0.0) {
                getCurrentLocationAndRegister(targetAddress, finalId, ssid, pw)
            } else {
                registerSharedDoorlock(targetAddress, finalId, ssid, pw, lat, lon, alt)
                sendBlePayload(ssid, pw, finalId)
            }
        }
    }

    private fun generateShortId(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { charset.random() }.joinToString("")
    }

    private fun findExistingDoorlockId(targetMac: String, onResult: (String?) -> Unit) {
        val userId = getSavedUserId()
        if (userId == null) {
            onResult(null)
            return
        }

        db.getReference("users").child(userId).child("my_doorlocks")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onResult(null)
                        return
                    }
                    val doorlockIds = snapshot.children.mapNotNull { it.key }
                    checkMacAddressRecursive(doorlockIds, 0, targetMac, onResult)
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(null)
                }
            })
    }

    private fun checkMacAddressRecursive(ids: List<String>, index: Int, targetMac: String, onResult: (String?) -> Unit) {
        if (index >= ids.size) {
            onResult(null)
            return
        }

        val currentId = ids[index]
        db.getReference("doorlocks").child(currentId).child("mac").get()
            .addOnSuccessListener { macSnapshot ->
                val dbMac = macSnapshot.getValue(String::class.java)
                if (dbMac == targetMac) {
                    onResult(currentId)
                } else {
                    checkMacAddressRecursive(ids, index + 1, targetMac, onResult)
                }
            }
            .addOnFailureListener {
                checkMacAddressRecursive(ids, index + 1, targetMac, onResult)
            }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndRegister(mac: String, doorlockId: String, ssid: String, pass: String) {
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lon = location?.longitude ?: 0.0
                val alt = location?.altitude ?: 0.0
                registerSharedDoorlock(mac, doorlockId, ssid, pass, lat, lon, alt)
                sendBlePayload(ssid, pass, doorlockId)
            }
            .addOnFailureListener {
                registerSharedDoorlock(mac, doorlockId, ssid, pass, 0.0, 0.0, 0.0)
                sendBlePayload(ssid, pass, doorlockId)
            }
    }

    // [핵심] 아두이노로 데이터 전송 (포맷: ssid:...,password:...,id:...)
    private fun sendBlePayload(ssid: String, pw: String, id: String) {
        val payload = "ssid:$ssid,password:$pw,id:$id"
        Log.d("BLE", "Sending: $payload")

        _statusText.postValue("설정 전송 중... (ID: $id)")

        val result = writeCharacteristic(WIFI_CTRL_UUID, payload)
        if (!result) {
            _statusText.postValue("전송 실패: 기기 연결 상태를 확인하세요.")
        }
    }

    fun sendWifiSettings(ssid: String, pass: String) {
        sendWifiSettingsWithLocation(ssid, pass, 0.0, 0.0, 0.0)
    }

    private fun registerSharedDoorlock(mac: String, doorlockId: String, ssid: String, pass: String, lat: Double, lon: Double, alt: Double) {
        val userId = getSavedUserId() ?: return
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val doorlocksRef = db.getReference("doorlocks").child(doorlockId)
        val userDoorlocksRef = db.getReference("users").child(userId).child("my_doorlocks")

        val fixedLocation = FixedLocation(latitude = lat, longitude = lon, altitude = alt)
        val members = HashMap<String, String>()
        members[userId] = "admin"

        val newLock = Doorlock(
            mac = mac,
            ssid = ssid,
            pw = pass,
            detailSettings = DetailSettings(true, 5, true),
            members = members,
            location = fixedLocation,
            lastUpdated = currentTime
        )

        doorlocksRef.setValue(newLock).addOnSuccessListener {
            doorlocksRef.child("command").setValue("INIT")
            doorlocksRef.child("status").get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val initialStatus = mapOf(
                        "state" to "LOCK",
                        "last_method" to "INIT",
                        "last_time" to currentTime,
                        "door_closed" to true
                    )
                    doorlocksRef.child("status").setValue(initialStatus)
                }
            }
        }
        userDoorlocksRef.child(doorlockId).setValue(true)
    }

    private fun connectGatt(address: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()

            // [Fix 1] Android 6.0(M) 이상에서는 TRANSPORT_LE를 강제해야 연결이 안정적임
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device?.connectGatt(getApplication(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                bluetoothGatt = device?.connectGatt(getApplication(), false, gattCallback)
            }
        } catch (e: Exception) {
            _statusText.value = "MAC 주소 오류: $address"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _statusText.postValue("기기 연결됨. 통신 설정 중...")

                // [Fix 2] 연결 직후 바로 서비스 탐색을 하면 실패하는 경우가 있어 약간의 딜레이를 줌
                mainHandler.postDelayed({
                    // [Fix 3] MTU(데이터 크기)를 517바이트로 요청 (긴 비밀번호 전송용)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Log.d("BLE", "Requesting MTU 517...")
                        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        gatt?.requestMtu(517)
                    } else {
                        gatt?.discoverServices()
                    }
                }, 600) // 0.6초 딜레이

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isBleConnected.postValue(false)
                _statusText.postValue("연결 끊어짐 (Status: $status)")
                closeGatt()
            }
        }

        // MTU 변경 성공 시 서비스 탐색 시작
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("BLE", "MTU Changed: $mtu, Status: $status")
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(PROV_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(WIFI_CTRL_UUID)
                if (service != null && characteristic != null) {
                    _isBleConnected.postValue(true)
                    _statusText.postValue("연결 및 설정 완료! 로그인하세요.")
                    subscribeNotifications()
                } else {
                    _statusText.postValue("오류: 지원하지 않는 기기입니다.")
                    disconnect()
                }
            } else {
                _statusText.postValue("서비스 탐색 실패: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleResponse(String(value, Charsets.UTF_8))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?) {
            c?.let { handleResponse(String(it.value, Charsets.UTF_8)) }
        }

        private fun handleResponse(response: String) {
            Log.d("BLE", "Response: $response")
            if (response == "WIFI_TRYING") {
                _statusText.postValue("도어락이 Wi-Fi 연결을 시도합니다...")
            } else if (response == "WIFI_OK") {
                _statusText.postValue("설정 성공! 기기가 재부팅됩니다.")
                closeGatt()
            } else if (response == "WIFI_FAIL") {
                _statusText.postValue("실패: Wi-Fi 연결 불가 (비밀번호 확인)")
            } else {
                _statusText.postValue("응답: $response")
            }
        }
    }

    private fun subscribeNotifications() {
        val s = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        val c = s?.getCharacteristic(WIFI_CTRL_UUID)
        val d = c?.getDescriptor(CCCD_UUID)

        if (c != null) {
            bluetoothGatt?.setCharacteristicNotification(c, true)
            // Descriptor 쓰기 (안드로이드 알림 활성화 필수)
            if (d != null) {
                val payload = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeDescriptor(d, payload)
                } else {
                    @Suppress("DEPRECATION")
                    d.value = payload
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeDescriptor(d)
                }
            }
        }
    }

    private fun writeCharacteristic(uuid: UUID, value: String): Boolean {
        val service = bluetoothGatt?.getService(PROV_SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(uuid) ?: return false
        val payload = value.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = bluetoothGatt?.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            return res == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            return bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        }
    }

    fun disconnect() = closeGatt()
    private fun closeGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isBleConnected.postValue(false)
    }
}