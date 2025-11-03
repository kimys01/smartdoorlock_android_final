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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonAuth.setOnClickListener {
            findNavController().navigate(R.id.navigation_auth)
        }

        binding.buttonAuthMethod.setOnClickListener {
            findNavController().navigate(R.id.navigation_auth_method)
        }

        binding.buttonDetailSetting.setOnClickListener {
            findNavController().navigate(R.id.navigation_detail_setting)
        }

        binding.buttonHelp.setOnClickListener {
            findNavController().navigate(R.id.navigation_help)
        }

        // --- 여기(Here)에 Wi-Fi 설정 버튼 클릭 리스너를 추가했습니다 ---
        // 이 ID는 'fragment_setting.xml' 파일에 정의되어 있어야 합니다.
        binding.buttonWifiSetting.setOnClickListener {
            // 이 ID(navigation_wifi_setting)는 'res/navigation/nav_graph.xml'에
            // SettingsFragment에서 WifiSettingFragment로 가는 Action으로 정의되어 있어야 합니다.
            findNavController().navigate(R.id.navigation_wifi_setting)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
