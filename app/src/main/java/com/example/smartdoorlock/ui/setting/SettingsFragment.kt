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
        binding.buttonAuthMethod.setOnClickListener { safeNavigate(R.id.navigation_auth_method) }
        binding.buttonDetailSetting.setOnClickListener { safeNavigate(R.id.navigation_detail_setting) }
        binding.buttonWifiSetting.setOnClickListener { safeNavigate(R.id.wifiSettingFragment) }
        binding.buttonHelp.setOnClickListener { safeNavigate(R.id.navigation_help) }
        binding.buttonInviteMember.setOnClickListener { safeNavigate(R.id.navigation_add_member) }

        // [추가] 도어락 비밀번호 변경 버튼 클릭 리스너
        binding.buttonChangeDoorlockPin.setOnClickListener { safeNavigate(R.id.navigation_doorlock_password) }
    }

    private fun safeNavigate(destinationId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.navigation_settings) {
            try {
                navController.navigate(destinationId)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}