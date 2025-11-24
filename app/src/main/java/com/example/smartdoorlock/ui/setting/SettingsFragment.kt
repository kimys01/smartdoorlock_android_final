package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentSettingBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 각 버튼에 안전한 이동 적용
        binding.deviceScanFragment.setOnClickListener { safeNavigate(R.id.action_settings_to_scan) }
        binding.buttonAuthMethod.setOnClickListener { safeNavigate(R.id.navigation_auth_method) } // ID 주의 (action ID인지 dest ID인지 확인)
        binding.buttonDetailSetting.setOnClickListener { safeNavigate(R.id.navigation_detail_setting) }
        binding.buttonWifiSetting.setOnClickListener { safeNavigate(R.id.wifiSettingFragment) }
        binding.buttonHelp.setOnClickListener { safeNavigate(R.id.navigation_help) }
    }

    // [핵심] 팅김 방지: 현재 위치가 '설정'일 때만 이동 수행
    private fun safeNavigate(destinationId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.navigation_settings) {
            // destinationId가 액션 ID인지 프래그먼트 ID인지에 따라 처리
            // 여기서는 nav graph에 정의된 액션/목적지로 이동
            try {
                navController.navigate(destinationId)
            } catch (e: IllegalArgumentException) {
                // 네비게이션 경로가 없을 때 죽지 않도록 처리
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}