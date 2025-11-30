package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.Context
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
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // [수정] var -> val로 변경하여 UUID가 변하지 않도록 고정
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
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetAddress: String = ""

    private fun getSavedUserId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        return prefs.getString("saved_id", null)
    }

    fun connectToDevice(address: String) {
        targetAddress = address
        _statusText.value = "도어락에 연결을 시도합니다..."
        connectGatt(address)
    }

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

    fun sendWifiSettingsWithLocation(ssid: String, pw: String, lat: Double, lon: Double, alt: Double) {
        if (bluetoothGatt == null) {
            _statusText.value = "BLE 연결 상태를 확인해주세요."
            return
        }

        //val randomId = UUID.randomUUID().toString()
        val randomId = "test1"

        if (lat == 0.0 && lon == 0.0) {
            getCurrentLocationAndRegister(targetAddress, randomId, ssid, pw)
        } else {
            registerSharedDoorlock(targetAddress, randomId, ssid, pw, lat, lon, alt)
            sendBlePayload(ssid, pw, randomId)
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

                Log.d("WifiSetting", "Fetched Location: $lat, $lon")

                registerSharedDoorlock(mac, doorlockId, ssid, pass, lat, lon, alt)
                sendBlePayload(ssid, pass, doorlockId)
            }
            .addOnFailureListener {
                Log.e("WifiSetting", "Location fetch failed", it)
                registerSharedDoorlock(mac, doorlockId, ssid, pass, 0.0, 0.0, 0.0)
                sendBlePayload(ssid, pass, doorlockId)
            }
    }

    private fun sendBlePayload(ssid: String, pw: String, id: String) {
        val payload = "ssid:$ssid,password:$pw,id:$id"

        Log.d("BLE", "Sending data: $payload")
        _statusText.postValue("설정값 전송 시도...")

        val result = writeCharacteristic(WIFI_CTRL_UUID, payload)
        if (!result) {
            _statusText.postValue("전송 실패: 도어락 서비스를 찾을 수 없습니다.")
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

        Log.d("DB_SHARE", "신규 도어락 생성 (ID: $doorlockId, Loc: $lat, $lon)")

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

        // 도어락 기본 정보 저장
        doorlocksRef.setValue(newLock).addOnSuccessListener {
            Log.d("DB_SHARE", "도어락 기본 정보 저장 완료")

            // command 경로 초기화
            doorlocksRef.child("command").setValue("INIT").addOnSuccessListener {
                Log.d("DB_SHARE", "command 경로 초기화 완료: INIT")
            }

            // status 경로 초기화
            val initialStatus = mapOf(
                "state" to "LOCK",
                "last_method" to "INIT",
                "last_time" to currentTime,
                "door_closed" to true
            )

            doorlocksRef.child("status").setValue(initialStatus).addOnSuccessListener {
                Log.d("DB_SHARE", "status 경로 초기화 완료")
            }

        }.addOnFailureListener { e ->
            Log.e("DB_SHARE", "도어락 정보 저장 실패", e)
        }

        // 사용자의 내 도어락 목록에 추가
        userDoorlocksRef.child(doorlockId).setValue(true)
    }

    private fun connectGatt(address: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            bluetoothGatt?.close()
            bluetoothGatt = device?.connectGatt(getApplication(), false, gattCallback)
        } catch (e: Exception) {
            _statusText.value = "주소 오류: $address"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isBleConnected.postValue(true)
                _statusText.postValue("도어락 연결 성공! 서비스 탐색 중...")
                val success = gatt?.requestMtu(512) ?: false
                if (!success) gatt?.discoverServices()
            } else {
                _isBleConnected.postValue(false)
                _statusText.postValue("연결 끊어짐")
                closeGatt()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            gatt?.discoverServices()
        }

        // [핵심 수정] 서비스를 정확히 찾도록 로직 변경
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 기존의 루프 방식은 다른 서비스를 덮어쓰는 문제가 있었습니다.
                // 지정된 UUID(PROV_SERVICE_UUID)로 서비스를 직접 가져옵니다.
                val service = gatt?.getService(PROV_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(WIFI_CTRL_UUID)

                if (service != null && characteristic != null) {
                    Log.d("BLE", "Target Service & Characteristic Found!")
                    subscribeNotifications()
                } else {
                    Log.e("BLE", "Target Service NOT found. UUID Mismatch?")
                    _statusText.postValue("도어락 서비스(UUID)를 찾을 수 없습니다.")
                    // disconnect() // 필요시 연결 해제
                }
            } else {
                Log.w("BLE", "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?, s: Int) {
            if (s == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Write successful")
            } else {
                _statusText.postValue("전송 실패 (Error: $s)")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val response = String(value, Charsets.UTF_8)
            Log.d("BLE", "Received: $response")
            if (response == "SUCCESS") {
                _statusText.postValue("성공: 도어락 설정이 완료되었습니다!")
                closeGatt()
            } else if (response.startsWith("FAIL")) {
                _statusText.postValue("실패: 정보를 확인해주세요")
            } else {
                _statusText.postValue("상태: $response")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?) {
            c?.let { onCharacteristicChanged(gatt!!, it, it.value) }
        }
    }

    private fun subscribeNotifications() {
        val s = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        val c = s?.getCharacteristic(WIFI_CTRL_UUID)
        val d = c?.getDescriptor(CCCD_UUID)
        if (c != null && d != null) {
            bluetoothGatt?.setCharacteristicNotification(c, true)
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(d)
        }
    }

    private fun writeCharacteristic(uuid: UUID, value: String): Boolean {
        val service = bluetoothGatt?.getService(PROV_SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(uuid) ?: return false
        characteristic.value = value.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        return result
    }

    fun disconnect() = closeGatt()
    private fun closeGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isBleConnected.postValue(false)
    }
}