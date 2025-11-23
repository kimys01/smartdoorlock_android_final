package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // [자동 변경 대상] 초기값은 임시로 두어도, 연결되면 자동으로 바뀝니다.
        var PROV_SERVICE_UUID: UUID = UUID.fromString("19b20000-e8f2-537e-4f6c-d104768a1214")
        var WIFI_CTRL_UUID: UUID = UUID.fromString("19b20003-e8f2-537e-4f6c-d104768a1214")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

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

    fun connectToDevice(address: String) {
        targetAddress = address
        _statusText.value = "도어락에 연결을 시도합니다..."
        connectGatt(address)
    }

    // 관리자 로그인
    fun verifyAppAdmin(inputId: String, inputPw: String) {
        val trimId = inputId.trim()
        val trimPw = inputPw.trim()

        if (trimId == "123456" && trimPw == "1234qwer") {
            _statusText.value = "테스트 계정 승인. 설정 진행..."
            _currentStep.value = 2
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _statusText.value = "오류: 앱 로그인 정보 없음"
            return
        }

        _statusText.value = "서버 정보 확인 중..."

        db.getReference("users").child(currentUser.uid).get()
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

    fun sendWifiSettings(ssid: String, pass: String) {
        if (_isBleConnected.value != true) {
            _statusText.value = "오류: 도어락 연결 끊김. 다시 연결해주세요."
            return
        }

        saveToRealtimeDB(targetAddress, ssid, pass)

        // [수정됨] 세미콜론 제거 (ssid:...,password:...)
        val payload = "ssid:$ssid,password:$pass"

        Log.d("BLE_CHECK", "🚀 [전송 요청] $payload (Target UUID: $WIFI_CTRL_UUID)")
        _statusText.value = "설정값 전송 시도..."

        // 자동 업데이트된 UUID로 전송 시도
        val result = writeCharacteristic(WIFI_CTRL_UUID, payload)
        if (!result) {
            _statusText.value = "전송 실패: UUID를 찾을 수 없습니다."
        }
    }

    // --- BLE 내부 로직 ---

    private fun connectGatt(address: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            bluetoothGatt?.close()
            bluetoothGatt = device?.connectGatt(getApplication(), false, gattCallback)
        } catch (e: Exception) {
            _statusText.value = "주소 오류: $address"
        }
    }

    private fun saveToRealtimeDB(mac: String, ssid: String, pass: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "mac" to mac,
            "ssid" to ssid,
            "pw" to pass,
            "date" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        db.getReference("users").child(uid).child("doorlock").setValue(data)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isBleConnected.postValue(true)
                _statusText.postValue("도어락 연결 성공! UUID 탐색 중...")
                Log.d("BLE_CHECK", "🔗 BLE 연결 성공. 서비스 탐색 시작...")
                val success = gatt?.requestMtu(512) ?: false
                if (!success) gatt?.discoverServices()
            } else {
                _isBleConnected.postValue(false)
                _statusText.postValue("연결 끊어짐. 다시 시도해주세요.")
                Log.d("BLE_CHECK", "🔌 BLE 연결 끊어짐. Status: $status")
                closeGatt()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("BLE_CHECK", "📏 MTU 변경됨: $mtu byte")
            gatt?.discoverServices()
        }

        // [핵심] UUID 자동 감지 및 업데이트 로직
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_CHECK", "🔎 서비스 발견 완료. UUID 분석 시작...")

                var foundWritableUuid = false

                // 모든 서비스를 순회하며 쓰기 가능한 특성을 찾습니다.
                gatt?.services?.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        val props = characteristic.properties

                        // 쓰기(Write) 또는 응답 없는 쓰기(Write No Response) 권한이 있는지 확인
                        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {

                            // 찾았다! UUID 자동 업데이트
                            PROV_SERVICE_UUID = service.uuid
                            WIFI_CTRL_UUID = characteristic.uuid
                            foundWritableUuid = true

                            Log.w("BLE_AUTO", "✅ [자동 설정] 쓰기 가능한 UUID 발견!")
                            Log.w("BLE_AUTO", "   Service: $PROV_SERVICE_UUID")
                            Log.w("BLE_AUTO", "   Characteristic: $WIFI_CTRL_UUID")

                            // 발견 즉시 루프 종료 (첫 번째 발견된 것 사용)
                            return@forEach
                        }
                    }
                    if (foundWritableUuid) return@forEach
                }

                if (!foundWritableUuid) {
                    Log.e("BLE_AUTO", "⚠️ 쓰기 가능한 UUID를 찾지 못했습니다. 기본값을 사용합니다.")
                }

                subscribeNotifications()
            } else {
                Log.e("BLE_CHECK", "❌ 서비스 발견 실패. Status: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?, s: Int) {
            if (s == BluetoothGatt.GATT_SUCCESS) {
                val sentData = String(c?.value ?: byteArrayOf(), Charsets.UTF_8)

                // [수정됨] 전송 성공 조건 변경 (세미콜론 제거 반영)
                if (sentData.contains("ssid:") && sentData.contains("password:")) {
                    Log.d("BLE_CHECK", "✅ [전송 완료] 성공적으로 전송됨: $sentData")
                    _statusText.postValue("전송 완료! 도어락 응답 대기 중...")
                }
            } else {
                Log.e("BLE_CHECK", "❌ [전송 실패] GATT Error Status: $s")
                _statusText.postValue("전송 실패 (Error: $s)")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val response = String(value, Charsets.UTF_8)
            Log.d("BLE_CHECK", "📩 [응답 수신] $response")

            if (response == "SUCCESS") {
                _statusText.postValue("성공: 도어락이 Wi-Fi에 연결되었습니다!")
                closeGatt()
            } else if (response.startsWith("FAIL")) {
                _statusText.postValue("실패: 와이파이 정보 확인 필요")
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
            Log.d("BLE_CHECK", "🔔 알림 구독 요청 보냄")
        } else {
            // 자동 감지 실패 시 로그
            Log.e("BLE_CHECK", "❌ 알림 구독 실패: UUID를 찾을 수 없음. (자동 감지 실패)")
        }
    }

    private fun writeCharacteristic(uuid: UUID, value: String): Boolean {
        // 자동 업데이트된 UUID 사용
        val service = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        if (service == null) {
            Log.e("BLE_CHECK", "❌ 서비스($PROV_SERVICE_UUID)를 찾을 수 없음.")
            return false
        }

        val characteristic = service.getCharacteristic(uuid)
        if (characteristic == null) {
            Log.e("BLE_CHECK", "❌ 특성($uuid)을 찾을 수 없음.")
            return false
        }

        characteristic.value = value.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val result = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        Log.d("BLE_CHECK", "📤 writeCharacteristic 호출 결과: $result")
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