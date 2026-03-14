package moe.fuqiuluo.portal.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.databinding.FragmentSettingsBinding
import moe.fuqiuluo.portal.ext.PortalPrefs
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.theme.ThemePreset
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        bindUi()
        bindActions()
        return binding.root
    }

    private fun bindUi() {
        val config = PortalPrefs.readConfig(requireContext())
        binding.altitudeValue.text = "%.2f 米".format(config.altitude)
        binding.accuracyValue.text = "%.2f 米".format(config.accuracy)
        binding.themePresetValue.text = getString(ThemePreset.fromKey(config.themePresetKey).titleRes)
        binding.reportDurationValue.text = "${config.reportDuration}ms"
        binding.satelliteCountValue.text = "${config.minSatelliteCount} 颗"

        binding.selinuxSwitch.isChecked = config.needOpenSELinux
        binding.stableStaticLocationSwitch.isChecked = config.stableStaticLocation
        binding.debugSwitch.isChecked = config.debug
        binding.dgcSwitch.isChecked = config.disableGetCurrentLocation
        binding.rllSwitch.isChecked = config.disableRegisterLocationListener
        binding.dfusedSwitch.isChecked = config.disableFusedProvider
        binding.cdmaSwitch.isChecked = config.needDowngradeToCdma
        binding.cellMockSwitch.isChecked = config.enableCellMock
        binding.sensorHookSwitch.isChecked = config.hookSensor
        binding.disableWlanScanSwitch.isChecked = config.disableWifiScan
        binding.loopBroadcastLocationSwitch.isChecked = config.loopBroadcastLocation
    }

    private fun bindActions() {
        binding.selinuxSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(needOpenSELinux = isChecked) }
            showToast(if (isChecked) "已开启 SELinux" else "已关闭 SELinux")
        }

        binding.altitudeLayout.setOnClickListener {
            val config = PortalPrefs.readConfig(requireContext())
            showDialog("设置模拟海拔", config.altitude.toString()) { input ->
                val value = input.toDoubleOrNull()
                when {
                    value == null || value < 0.0 -> showToast("海拔高度不合法")
                    value > 10000.0 -> showToast("海拔高度不能超过 10000 米")
                    else -> {
                        updateConfig { it.copy(altitude = value) }
                        binding.altitudeValue.text = "%.2f 米".format(value)
                        updateRemoteConfig()
                    }
                }
            }
        }

        binding.accuracyLayout.setOnClickListener {
            val config = PortalPrefs.readConfig(requireContext())
            showDialog("设置定位精度", config.accuracy.toString()) { input ->
                val value = input.toFloatOrNull()
                when {
                    value == null || value < 0f -> showToast("定位精度不合法")
                    value > 1000f -> showToast("定位精度不能超过 1000 米")
                    else -> {
                        updateConfig { it.copy(accuracy = value) }
                        binding.accuracyValue.text = "%.2f 米".format(value)
                        updateRemoteConfig()
                    }
                }
            }
        }

        binding.themePresetLayout.setOnClickListener {
            val context = requireContext()
            val presets = ThemePreset.entries.toList()
            val labels = presets.map { getString(it.titleRes) }.toTypedArray()
            val current = ThemePreset.fromKey(PortalPrefs.readConfig(context).themePresetKey)
            val checkedIndex = presets.indexOf(current).coerceAtLeast(0)
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.theme_preset)
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    val preset = presets[which]
                    updateConfig { it.copy(themePresetKey = preset.key) }
                    binding.themePresetValue.text = labels[which]
                    dialog.dismiss()
                    requireActivity().recreate()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.stableStaticLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(stableStaticLocation = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已开启静止定位稳定模式" else "已关闭静止定位稳定模式")
        }

        binding.debugSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(debug = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已开启调试模式" else "已关闭调试模式")
        }

        binding.dgcSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(disableGetCurrentLocation = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已禁用 getCurrentLocation" else "已允许 getCurrentLocation")
        }

        binding.rllSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(disableRegisterLocationListener = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已禁用注册定位监听" else "已允许注册定位监听")
        }

        binding.dfusedSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(disableFusedProvider = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已禁用 Fused 定位" else "已启用 Fused 定位")
        }

        binding.cdmaSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(needDowngradeToCdma = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已开启网络降级" else "已关闭网络降级")
        }

        binding.cellMockSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(enableCellMock = isChecked) }
            updateRemoteConfig()
            showToast(if (isChecked) "已开启基站模拟" else "已关闭基站模拟")
        }

        binding.sensorHookSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(hookSensor = isChecked) }
            updateRemoteConfig()
            showToast("请重新启动模拟后生效")
        }

        binding.reportDurationLayout.setOnClickListener {
            val config = PortalPrefs.readConfig(requireContext())
            showDialog("设置位置上报间隔", config.reportDuration.toString()) { input ->
                val value = input.toIntOrNull()
                when {
                    value == null || value < 1 -> showToast("上报间隔不合法")
                    value > 1000 -> showToast("上报间隔不能大于 1000ms")
                    else -> {
                        updateConfig { it.copy(reportDuration = value) }
                        binding.reportDurationValue.text = "${value}ms"
                        showToast("已保存位置上报间隔")
                    }
                }
            }
        }

        binding.satelliteCountLayout.setOnClickListener {
            val config = PortalPrefs.readConfig(requireContext())
            showDialog("设置最小模拟卫星数量", config.minSatelliteCount.toString()) { input ->
                val value = input.toIntOrNull()
                when {
                    value == null || value < 0 -> showToast("卫星数量不合法")
                    value > 35 -> showToast("卫星数量不能超过 35")
                    else -> {
                        updateConfig { it.copy(minSatelliteCount = value) }
                        binding.satelliteCountValue.text = "${value} 颗"
                        updateRemoteConfig()
                        showToast("已同步最小模拟卫星数量")
                    }
                }
            }
        }

        binding.disableWlanScanSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(disableWifiScan = isChecked) }
            val locationManager = mockServiceViewModel.locationManager
            if (locationManager == null) {
                showToast("系统服务未初始化")
                return@setOnCheckedChangeListener
            }
            val success = if (isChecked) {
                MockServiceHelper.startWifiMock(locationManager)
            } else {
                MockServiceHelper.stopWifiMock(locationManager)
            }
            if (!success) {
                showToast(if (isChecked) "禁用 WLAN 扫描失败" else "恢复 WLAN 扫描失败")
            }
        }

        binding.loopBroadcastLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateConfig { it.copy(loopBroadcastLocation = isChecked) }
            showToast(if (isChecked) "已开启反定位拉回" else "已关闭反定位拉回")
        }
    }

    private fun updateConfig(transform: (moe.fuqiuluo.portal.ext.PortalConfig) -> moe.fuqiuluo.portal.ext.PortalConfig) {
        PortalPrefs.updateConfig(requireContext(), transform)
    }

    private fun showToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRemoteConfig() {
        val locationManager = mockServiceViewModel.locationManager
        if (locationManager == null) {
            showToast("系统服务未初始化")
            return
        }
        if (!MockServiceHelper.putConfig(locationManager, requireContext())) {
            showToast("同步远程配置失败")
        }
    }

    private fun showDialog(titleText: String, valueText: String, handler: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_input, null)
        dialogView.findViewById<TextView>(R.id.title).text = titleText
        val value = dialogView.findViewById<TextInputEditText>(R.id.value)
        value.setText(valueText)

        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                handler(value.text?.toString().orEmpty())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
