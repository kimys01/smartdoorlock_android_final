package com.example.smartdoorlock.ui.setting

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.smartdoorlock.databinding.FragmentWifiSettingBinding

class WifiSettingFragment : Fragment() {

    private var _binding: FragmentWifiSettingBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: WifiSettingViewModel
    private var targetDeviceAddress: String = ""

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.connectToDevice(targetDeviceAddress)
        } else {
            Toast.makeText(requireContext(), "권한 필요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWifiSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("DEVICE_ADDRESS")?.let { targetDeviceAddress = it }
        viewModel = ViewModelProvider(this).get(WifiSettingViewModel::class.java)

        // 1. 화면 시작 시 연결 시도
        if (targetDeviceAddress.isNotEmpty()) {
            if (checkBlePermissions()) viewModel.connectToDevice(targetDeviceAddress)
            else requestBlePermissions.launch(getRequiredBlePermissions())
        }

        // 2. 상태 메시지 관찰
        viewModel.statusText.observe(viewLifecycleOwner) { status ->
            binding.textViewStatus.text = status
        }

        // [핵심] 3. 블루투스 연결 상태 관찰 -> 버튼 활성/비활성화
        viewModel.isBleConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                // 연결 성공 시: 버튼 활성화 및 안내 메시지
                binding.buttonLoginApp.isEnabled = true
                binding.buttonConnectWifi.isEnabled = true
                // (옵션) 토스트 띄우기
                // Toast.makeText(context, "연결 완료! 설정을 시작하세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 연결 안 됨: 버튼 비활성화 (누르지 못하게 막음)
                binding.buttonLoginApp.isEnabled = false
                binding.buttonConnectWifi.isEnabled = false
            }
        }

        // 4. 단계별 UI 전환
        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            updateUiStep(step)
            if (step == 2) fetchCurrentWifiSsid()
        }

        // --- 버튼 리스너 ---

        // 앱 로그인 (연결된 상태에서만 동작)
        binding.buttonLoginApp.setOnClickListener {
            val id = binding.editTextUserId.text.toString().trim()
            val pw = binding.editTextUserPw.text.toString().trim()
            if (id.isNotEmpty() && pw.isNotEmpty()) viewModel.verifyAppAdmin(id, pw)
            else Toast.makeText(context, "정보를 입력하세요", Toast.LENGTH_SHORT).show()
        }

        // 와이파이 설정 (연결된 상태에서만 동작)
        binding.buttonConnectWifi.setOnClickListener {
            val ssid = binding.editTextSsid.text.toString()
            val pw = binding.editTextPassword.text.toString()
            if (ssid.isNotEmpty() && pw.isNotEmpty()) viewModel.sendWifiSettings(ssid, pw)
            else Toast.makeText(context, "입력 필요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCurrentWifiSsid() {
        try {
            if (binding.editTextSsid.text.toString().isNotEmpty()) return
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            if (info != null && info.ssid != null && info.ssid != "<unknown ssid>") {
                binding.editTextSsid.setText(info.ssid.replace("\"", ""))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateUiStep(step: Int) {
        binding.layoutLoginSection.visibility = View.GONE
        binding.layoutWifiSection.visibility = View.GONE

        when (step) {
            0 -> binding.layoutLoginSection.visibility = View.VISIBLE
            2 -> binding.layoutWifiSection.visibility = View.VISIBLE
        }
    }

    private fun checkBlePermissions(): Boolean {
        return getRequiredBlePermissions().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.disconnect()
    }
}