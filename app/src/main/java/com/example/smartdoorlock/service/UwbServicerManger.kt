package com.example.smartdoorlock.service

import android.content.Context
import android.util.Log
import androidx.core.uwb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UwbServiceManager(private val context: Context) {

    private var uwbManager: UwbManager? = null
    private var uwbJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 도어락의 UWB 주소 (하드웨어와 약속된 주소)
    private val deviceAddress = UwbAddress(byteArrayOf(0x12, 0x34))

    fun init() {
        if (uwbManager == null) {
            try {
                uwbManager = UwbManager.createInstance(context)
            } catch (e: Exception) {
                Log.e("UWB", "이 기기는 UWB를 지원하지 않습니다.", e)
            }
        }
    }

    fun startRanging() {
        if (uwbManager == null) {
            Log.w("UWB", "UWB 매니저가 초기화되지 않았거나 지원하지 않음.")
            return
        }
        if (uwbJob?.isActive == true) return

        Log.d("UWB", "🚀 UWB 거리 측정 시작")

        uwbJob = scope.launch {
            try {
                val sessionScope = uwbManager!!.controllerSessionScope()

                // 2. 설정 파라미터
                val complexChannel = UwbComplexChannel(channel = 9, preambleIndex = 10)

                // [수정 1] UwbDevice 객체로 래핑
                val peerDevices = listOf(UwbDevice(deviceAddress))

                // [수정 2] 최신 API에 맞춘 파라미터 (CONFIG_ID 변경, subSession 추가)
                val params = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR, // 이름 변경됨
                    sessionId = 12345,
                    subSessionId = 0, // [추가] 서브 세션 ID (미사용 시 0)
                    sessionKeyInfo = null,
                    subSessionKeyInfo = null, // [추가] 서브 세션 키 (미사용 시 null)
                    complexChannel = complexChannel,
                    peerDevices = peerDevices, // List<UwbDevice> 타입
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
                )

                // 3. 거리 측정
                sessionScope.prepareSession(params).collect { result ->
                    when (result) {
                        is RangingResult.RangingResultPosition -> {
                            val distance = result.position.distance
                            distance?.let {
                                Log.d("UWB", "📏 거리: ${it.value}m")
                                if (it.value < 1.0) {
                                    Log.i("UWB", "🚪 문 열림 신호 전송!")
                                }
                            }
                        }
                        is RangingResult.RangingResultPeerDisconnected -> {
                            Log.d("UWB", "연결 끊김")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UWB", "Ranging 오류: ${e.message}", e)
            }
        }
    }

    fun stopRanging() {
        if (uwbJob?.isActive == true) {
            Log.d("UWB", "🛑 UWB 거리 측정 중지")
            uwbJob?.cancel()
            uwbJob = null
        }
    }
}