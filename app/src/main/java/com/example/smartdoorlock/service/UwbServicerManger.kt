package com.example.smartdoorlock.service

import android.content.Context
import android.util.Log
import androidx.core.uwb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs

class UwbServiceManager(private val context: Context) {

    private var uwbManager: UwbManager? = null
    private var uwbJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val frontAddress = UwbAddress(byteArrayOf(0x12, 0x34))
    private val backAddress = UwbAddress(byteArrayOf(0x56, 0x78))

    private var distFront: Double? = null
    private var distBack: Double? = null

    private var lastLogTime: Long = 0
    private val LOG_INTERVAL = 5000L // 5초

    var onLogUpdate: ((Double, Double) -> Unit)? = null
    var onUnlockRangeEntered: (() -> Unit)? = null

    fun init() {
        if (uwbManager == null) {
            try {
                uwbManager = UwbManager.createInstance(context)
            } catch (e: Exception) {
                Log.e("UWB", "UWB 미지원 기기", e)
            }
        }
    }

    fun startRanging() {
        if (uwbManager == null || uwbJob?.isActive == true) return

        Log.d("UWB", "🚀 UWB 거리 측정 시작")
        lastLogTime = 0

        uwbJob = scope.launch {
            try {
                val sessionScope = uwbManager!!.controllerSessionScope()
                val complexChannel = UwbComplexChannel(channel = 9, preambleIndex = 10)
                val peerDevices = listOf(UwbDevice(frontAddress), UwbDevice(backAddress))

                val params = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = 12345,
                    subSessionId = 0,
                    sessionKeyInfo = null,
                    subSessionKeyInfo = null,
                    complexChannel = complexChannel,
                    peerDevices = peerDevices,
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
                )

                sessionScope.prepareSession(params).collect { result ->
                    processRangingResult(result)
                }
            } catch (e: Exception) {
                Log.e("UWB", "Ranging 오류: ${e.message}", e)
            }
        }
    }

    private fun processRangingResult(result: RangingResult) {
        when (result) {
            is RangingResult.RangingResultPosition -> {
                val distance = result.position.distance?.value?.toDouble() ?: return
                val address = result.device.address

                if (address == frontAddress) distFront = distance
                else if (address == backAddress) distBack = distance

                if (distFront != null && distBack != null) {
                    checkPositionAndUnlock()

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime >= LOG_INTERVAL) {
                        onLogUpdate?.invoke(distFront!!, distBack!!)
                        lastLogTime = currentTime
                    }
                }
            }
            is RangingResult.RangingResultPeerDisconnected -> {
                Log.d("UWB", "장치 연결 끊김")
            }
        }
    }

    private fun checkPositionAndUnlock() {
        val front = distFront ?: return
        val back = distBack ?: return

        if (front < back) {
            if (front <= 3.0) {
                Log.i("UWB", "🔓 실외 3m 진입 (앞:$front < 뒤:$back)")
                onUnlockRangeEntered?.invoke()
                stopRanging()
                resetDistances()
            }
        }
    }

    private fun resetDistances() {
        distFront = null
        distBack = null
    }

    fun stopRanging() {
        if (uwbJob?.isActive == true) {
            Log.d("UWB", "🛑 UWB 거리 측정 중지")
            uwbJob?.cancel()
            uwbJob = null
            resetDistances()
        }
    }
}