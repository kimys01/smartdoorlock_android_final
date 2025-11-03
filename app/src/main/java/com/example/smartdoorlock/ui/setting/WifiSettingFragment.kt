package com.example.smartdoorlock.ui.setting

import android.Manifest
import android.content.pm.PackageManager
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

    // TODO: 이전 화면(BLE 스캔)에서 선택한 도어락의 MAC 주소를 받아와야 합니다.
    // 예시: private val deviceAddress: String by lazy { arguments?.getString("DEVICE_ADDRESS") ?: "" }
    // 여기서는 테스트를 위해 임시 주소를 사용합니다. 실제로는 arguments에서 받아오세요.
    private val TEST_DEVICE_ADDRESS = "00:11:22:33:AA:BB"

    // BLE 권한 요청 런처
    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // 권한이 모두 승인되면 연결 시도
            startProvisioning()
        } else {
            Toast.makeText(requireContext(), "BLE 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ViewModel 초기화
        viewModel = ViewModelProvider(this).get(WifiSettingViewModel::class.java)

        // ViewModel의 상태(Status) 메시지를 관찰하여 UI(textViewStatus)에 반영
        viewModel.provisioningStatus.observe(viewLifecycleOwner) { status ->
            binding.textViewStatus.text = status
            // 연결 버튼 활성화/비활성화 로직
            binding.buttonConnectWifi.isEnabled = !(status.contains("중") || status.contains("완료"))
        }

        // '연결하기' 버튼 클릭 리스너
        binding.buttonConnectWifi.setOnClickListener {
            if (checkBlePermissions()) {
                startProvisioning()
            } else {
                requestBlePermissions.launch(getRequiredBlePermissions())
            }
        }
    }

    private fun startProvisioning() {
        val ssid = binding.editTextSsid.text.toString()
        val password = binding.editTextPassword.text.toString()

        if (ssid.isEmpty()) {
            binding.editTextSsid.error = "SSID를 입력하세요."
            return
        }

        // TODO: 실제 deviceAddress를 viewModel로 전달해야 합니다.
        // viewModel.startProvisioning(deviceAddress, ssid, password)

        // --- 테스트용 ---
        if (TEST_DEVICE_ADDRESS.isEmpty()) {
            Toast.makeText(requireContext(), "도어락 주소가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.startProvisioning(TEST_DEVICE_ADDRESS, ssid, password)
        // --- 테스트용 끝 ---
    }

    private fun checkBlePermissions(): Boolean {
        return getRequiredBlePermissions().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        // 화면이 중지되면 BLE 연결을 해제하도록 ViewModel에 알림
        viewModel.disconnect()
    }
}
