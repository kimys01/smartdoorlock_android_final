package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.smartdoorlock.data.DetailSettings
import com.example.smartdoorlock.data.Doorlock
import com.example.smartdoorlock.data.FixedLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.nio.charset.Charset
import java.util.*

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    // 아두이노 코드의 UUID와 반드시 일치해야 함
    private val PROV_SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val WIFI_CTRL_UUID = UUID.fromString("abcd1234-5678-90ab-cdef-1234567890ab")

    private val _statusText = MutableLiveData<String>("연결 대기 중...")
    val statusText: LiveData<String> = _statusText

    private val _isBleConnected = MutableLiveData<Boolean>(false)
    val isBleConnected: LiveData<Boolean> = _isBleConnected

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private var bluetoothGatt: BluetoothGatt? = null
    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun connectToDevice(address: String) {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter.getRemoteDevice(address)

        _statusText.value = "기기에 연결 중..."
        // 자동 연결 false로 설정하여 즉시 연결 시도
        bluetoothGatt = device.connectGatt(getApplication(), false, gattCallback)
    }

    fun sendWifiSettings(ssid: String, pw: String) {
        if (bluetoothGatt == null) {
            _statusText.value = "기기와 연결되어 있지 않습니다."
            return
        }

        // 1. 도어락 식별용 랜덤 ID 생성 (6자리 대문자)
        val newDoorlockId = UUID.randomUUID().toString().substring(0, 6).uppercase()

        // 2. 아두이노 전송용 문자열 생성 (포맷 준수 중요!)
        val payload = "ssid:$ssid,password:$pw,id:$newDoorlockId"

        Log.d("BLE", "전송 Payload: $payload")
        _statusText.value = "설정 전송 중... (ID: $newDoorlockId)"

        // 3. Firebase에 기기 정보 등록
        registerDoorlockToFirebase(newDoorlockId, ssid, pw)

        // 4. BLE로 데이터 전송
        writeCharacteristic(payload)
    }

    private fun registerDoorlockToFirebase(id: String, ssid: String, pw: String) {
        val userId = auth.currentUser?.uid ?: return

        // 내 도어락 목록에 추가
        db.getReference("users").child(userId).child("my_doorlocks").child(id).setValue(true)

        // 도어락 공통 정보 생성
        val newLock = Doorlock(
            mac = bluetoothGatt?.device?.address ?: "",
            ssid = ssid,
            pw = pw, // 필요한 경우 저장
            detailSettings = DetailSettings(true, 5, true),
            location = FixedLocation(0.0, 0.0, 0.0) // 위치는 추후 업데이트 가능
        )
        // 공용 도어락 정보 저장
        db.getReference("doorlocks").child(id).setValue(newLock)
    }

    private fun writeCharacteristic(value: String) {
        val service = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(WIFI_CTRL_UUID)

        if (characteristic != null) {
            characteristic.value = value.toByteArray(Charsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(characteristic, value.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(characteristic)
            }
        } else {
            _statusText.postValue("오류: 지원하지 않는 기기입니다 (서비스 UUID 불일치).")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isBleConnected.postValue(true)
                _statusText.postValue("연결됨. 서비스 탐색 중...")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isBleConnected.postValue(false)
                _statusText.postValue("연결 끊김")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 서비스 발견 성공 시 즉시 로그인 단계로 진행 (혹은 바로 Wi-Fi 입력)
                _statusText.postValue("기기 인식 완료.")
            } else {
                _statusText.postValue("서비스 탐색 실패: $status")
            }
        }

        // 특성 쓰기 완료 콜백 (옵션)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _statusText.postValue("데이터 전송 성공. 기기 반응 대기 중...")
            }
        }
    }

    fun verifyAppAdmin(id: String, pw: String) {
        // 앱 로그인 확인 (이미 로그인된 상태라면 바로 통과)
        if (auth.currentUser != null) {
            _currentStep.value = 2 // Wi-Fi 입력 단계로 이동
        } else {
            _statusText.value = "먼저 앱에 로그인해주세요."
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}