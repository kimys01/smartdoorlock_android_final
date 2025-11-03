package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*

// AndroidViewModel은 Application Context에 접근하기 위해 사용됩니다.
@SuppressLint("MissingPermission") // 권한 확인은 Fragment에서 수행
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    // --- 하드웨어(ESP32)에 정의된 UUID와 동일해야 함 ---
    companion object {
        // Wi-Fi 프로비저닝 서비스
        val PROV_SERVICE_UUID: UUID = UUID.fromString("19b20000-e8f2-537e-4f6c-d104768a1214")
        val WIFI_SSID_UUID: UUID = UUID.fromString("19b20001-e8f2-537e-4f6c-d104768a1214")
        val WIFI_PASS_UUID: UUID = UUID.fromString("19b20002-e8f2-537e-4f6c-d104768a1214")
        val WIFI_CTRL_UUID: UUID = UUID.fromString("19b20003-e8f2-537e-4f6c-d104768a1214")
        // 알림(Notify)을 위한 Descriptor UUID (표준)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val _provisioningStatus = MutableLiveData<String>()
    val provisioningStatus: LiveData<String> = _provisioningStatus

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var tempSsid: String = ""
    private var tempPass: String = ""

    // BLE 통신 상태 관리를 위한 간단한 상태 머신
    private var provisioningState = 0
    private val STATE_IDLE = 0
    private val STATE_WRITING_SSID = 1
    private val STATE_WRITING_PASS = 2
    private val STATE_WRITING_CONNECT = 3

    // BLE 통신 콜백
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("WifiVM", "BLE Connected. Discovering services...")
                    _provisioningStatus.postValue("도어락 연결 성공. 서비스 탐색 중...")
                    bluetoothGatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w("WifiVM", "BLE Disconnected.")
                    _provisioningStatus.postValue("도어락 연결 끊어짐.")
                    closeGatt()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("WifiVM", "Services Discovered. Subscribing to notifications...")
                _provisioningStatus.postValue("서비스 발견. 알림 구독 중...")
                subscribeToControlNotifications()
            } else {
                Log.w("WifiVM", "Service discovery failed: $status")
                _provisioningStatus.postValue("서비스 발견 실패.")
                closeGatt()
            }
        }

        // 알림 구독 결과
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (descriptor?.characteristic?.uuid == WIFI_CTRL_UUID) {
                Log.i("WifiVM", "Notification subscription complete. Writing SSID...")
                _provisioningStatus.postValue("알림 구독 완료. SSID 전송 중...")
                writeSsid()
            }
        }

        // BLE 'Write' 작업이 완료될 때마다 호출됨
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("WifiVM", "Write failed for ${characteristic?.uuid}")
                _provisioningStatus.postValue("데이터 전송 실패.")
                closeGatt()
                return
            }

            when (provisioningState) {
                STATE_WRITING_SSID -> {
                    Log.i("WifiVM", "SSID write success. Writing Password...")
                    _provisioningStatus.postValue("SSID 전송 완료. 비밀번호 전송 중...")
                    writePassword()
                }
                STATE_WRITING_PASS -> {
                    Log.i("WifiVM", "Password write success. Writing Connect Command...")
                    _provisioningStatus.postValue("비밀번호 전송 완료. 연결 명령 전송 중...")
                    writeConnectCommand()
                }
                STATE_WRITING_CONNECT -> {
                    Log.i("WifiVM", "Connect command sent. Waiting for response...")
                    _provisioningStatus.postValue("연결 명령 전송 완료. 도어락 응답 대기 중...")
                    provisioningState = STATE_IDLE // 대기 상태로 복귀
                }
            }
        }

        // 도어락(ESP32)에서 알림(Notify)이 왔을 때 호출됨

        // API 33 (Tiramisu) 미만을 위한 @Deprecated 콜백
        @Deprecated("Used for API < 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic == null) return
            handleCharacteristicChange(characteristic.uuid, characteristic.value)
        }

        // API 33 (Tiramisu) 이상을 위한 새로운 콜백
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // 신버전 API 처리 (characteristic.value 대신 value 파라미터를 사용)
            handleCharacteristicChange(characteristic.uuid, value)
        }

        /**
         * onCharacteristicChanged의 구버전/신버전 콜백을 모두 처리하는 공통 함수
         */
        private fun handleCharacteristicChange(uuid: UUID?, value: ByteArray?) {
            if (value == null) return

            if (uuid == WIFI_CTRL_UUID) {
                val response = value.toString(Charsets.UTF_8)
                Log.i("WifiVM", "Received response from lock: $response")

                when (response) {
                    "SUCCESS" -> _provisioningStatus.postValue("Wi-Fi 연결 성공! 도어락이 재부팅됩니다.")
                    "FAIL" -> _provisioningStatus.postValue("Wi-Fi 연결 실패. 비밀번호를 확인하세요.")
                    "CONNECTING..." -> _provisioningStatus.postValue("도어락이 Wi-Fi에 연결 중...")
                    else -> _provisioningStatus.postValue("도어락 응답: $response")
                }

                if(response == "SUCCESS" || response.startsWith("FAIL")) {
                    closeGatt()
                }
            }
        }
    }

    // --- Public 함수 (Fragment에서 호출) ---

    fun startProvisioning(address: String, ssid: String, pass: String) {
        if (bluetoothAdapter == null) {
            _provisioningStatus.postValue("블루투스를 사용할 수 없습니다.")
            return
        }
        tempSsid = ssid
        tempPass = pass
        provisioningState = STATE_IDLE

        try {
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            _provisioningStatus.postValue("도어락에 연결 중... (기기: $address)")
            // 기존 연결이 있다면 닫고 새로 연결
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(getApplication(), false, gattCallback)
        } catch (e: IllegalArgumentException) {
            Log.e("WifiVM", "Invalid device address.")
            _provisioningStatus.postValue("유효하지 않은 도어락 주소입니다.")
        }
    }

    fun disconnect() {
        closeGatt()
    }

    // --- Private BLE 작업 함수 ---

    private fun subscribeToControlNotifications() {
        val provService = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        val ctrlChar = provService?.getCharacteristic(WIFI_CTRL_UUID)
        val cccd = ctrlChar?.getDescriptor(CCCD_UUID)

        if (ctrlChar == null || cccd == null) {
            Log.e("WifiVM", "Control characteristic or CCCD not found.")
            _provisioningStatus.postValue("오류: 제어 채널을 찾을 수 없습니다.")
            closeGatt()
            return
        }

        // 1. 알림 활성화 요청
        bluetoothGatt?.setCharacteristicNotification(ctrlChar, true)
        // 2. Descriptor에 활성화 값 쓰기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            bluetoothGatt?.writeDescriptor(
                cccd,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        } else { // API 32 이하
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(cccd)
        }
    }

    private fun writeSsid() {
        provisioningState = STATE_WRITING_SSID
        val characteristic = bluetoothGatt?.getService(PROV_SERVICE_UUID)?.getCharacteristic(WIFI_SSID_UUID)
        if (characteristic == null) {
            _provisioningStatus.postValue("오류: SSID 채널을 찾을 수 없습니다.")
            closeGatt()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                tempSsid.toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            characteristic.value = tempSsid.toByteArray(Charsets.UTF_8)
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    private fun writePassword() {
        provisioningState = STATE_WRITING_PASS
        val characteristic = bluetoothGatt?.getService(PROV_SERVICE_UUID)?.getCharacteristic(WIFI_PASS_UUID)
        if (characteristic == null) {
            _provisioningStatus.postValue("오류: 비밀번호 채널을 찾을 수 없습니다.")
            closeGatt()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                tempPass.toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            characteristic.value = tempPass.toByteArray(Charsets.UTF_8)
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    private fun writeConnectCommand() {
        provisioningState = STATE_WRITING_CONNECT
        val characteristic = bluetoothGatt?.getService(PROV_SERVICE_UUID)?.getCharacteristic(WIFI_CTRL_UUID)
        if (characteristic == null) {
            _provisioningStatus.postValue("오류: 제어 채널을 찾을 수 없습니다.")
            closeGatt()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                "CONNECT".toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else { // API 32 이하
            characteristic.value = "CONNECT".toByteArray(Charsets.UTF_8)
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    private fun closeGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        provisioningState = STATE_IDLE
    }

    override fun onCleared() {
        super.onCleared()
        closeGatt()
    }
}

