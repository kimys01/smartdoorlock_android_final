package com.example.smartdoorlock.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentDeviceScanBinding

class DeviceScanFragment : Fragment() {

    private var _binding: FragmentDeviceScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Pair<기기, 이름> 형태로 저장
    private val scanResults = ArrayList<Pair<BluetoothDevice, String>>()
    private lateinit var deviceAdapter: DeviceAdapter // 수정된 Adapter 사용

    // [설정] 스캔 시간 10초
    private val SCAN_PERIOD: Long = 10000

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            startScan()
        } else {
            Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Toast.makeText(context, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
            return
        }
        bluetoothAdapter = bluetoothManager.adapter

        // DeviceAdapter 연결
        deviceAdapter = DeviceAdapter(scanResults) { device, _ ->
            if (isScanning) stopScan()
            navigateToWifiSetting(device)
        }

        binding.recyclerViewDevices.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewDevices.adapter = deviceAdapter

        binding.btnStartScan.setOnClickListener {
            if (checkPermissions()) startScan()
            else requestPermissionLauncher.launch(getRequiredPermissions())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning) return

        scanResults.clear()
        deviceAdapter.notifyDataSetChanged()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        binding.progressBarScanning.visibility = View.VISIBLE
        binding.tvScanStatus.text = "주변 도어락 찾는 중..."
        binding.btnStartScan.isEnabled = false

        try {
            bluetoothAdapter.bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
            Log.d("DeviceScan", "스캔 시작")
            handler.postDelayed({ stopScan() }, SCAN_PERIOD)
        } catch (e: Exception) {
            Log.e("DeviceScan", "스캔 실패: ${e.message}")
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(leScanCallback)
        } catch (e: Exception) {
            Log.e("DeviceScan", "스캔 중지 오류: ${e.message}")
        }
        binding.progressBarScanning.visibility = View.INVISIBLE
        binding.tvScanStatus.text = if (scanResults.isEmpty()) "기기를 찾지 못했습니다." else "스캔 완료"
        binding.btnStartScan.isEnabled = true
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // 스캔 레코드에서 이름을 가져오거나, 기기 자체 이름을 사용
            val deviceName = result.scanRecord?.deviceName ?: device.name ?: "이름 없음"

            // 중복 방지 로직
            val alreadyExists = scanResults.any { it.first.address == device.address }
            if (!alreadyExists) {
                // [필터링 옵션] 필요한 경우 아래 주석을 해제하여 "SmartDoorlock"만 표시
                // if (deviceName.contains("SmartDoorlock")) {
                scanResults.add(Pair(device, deviceName))
                deviceAdapter.notifyItemInserted(scanResults.size - 1)
                // }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("DeviceScan", "스캔 실패 에러코드: $errorCode")
        }
    }

    private fun navigateToWifiSetting(device: BluetoothDevice) {
        val bundle = Bundle().apply { putString("DEVICE_ADDRESS", device.address) }
        findNavController().navigate(R.id.wifiSettingFragment, bundle)
    }

    private fun checkPermissions(): Boolean {
        return getRequiredPermissions().all {
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions.toTypedArray()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopScan()
        _binding = null
    }
}