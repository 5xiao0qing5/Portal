package moe.fuqiuluo.portal.ui.step

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.databinding.FragmentStepDebugBinding
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import java.util.Locale
import androidx.lifecycle.Lifecycle

class StepDebugFragment : Fragment() {
    private var _binding: FragmentStepDebugBinding? = null
    private val binding get() = _binding!!

    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStepDebugBinding.inflate(inflater, container, false)
        renderUnavailable()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshStepStats()
                    delay(500)
                }
            }
        }
    }

    private suspend fun refreshStepStats() {
        val manager = mockServiceViewModel.locationManager
        if (manager == null) {
            renderUnavailable()
            return
        }

        val info = withContext(Dispatchers.IO) {
            MockServiceHelper.getStepDebugInfo(manager)
        }

        if (!isAdded || _binding == null) {
            return
        }

        if (info == null) {
            renderUnavailable()
            return
        }

        binding.textStatus.visibility = View.GONE
        binding.textStepCount.text = String.format(Locale.US, "%.1f", info.simulatedStepCount)
        binding.textDistance.text = String.format(Locale.US, "%.1f m", info.simulatedDistanceMeters)
        binding.textStepLength.text = String.format(Locale.US, "%.2f m", info.estimatedStepLengthMeters)
        binding.textMotion.text = if (info.sensorMotionActive) "运动中" else "未运动"
        binding.textMockEnabled.text = if (info.enableMock) "已开启" else "已关闭"
        binding.textSensorEnabled.text = if (info.enableSensorMock) "已开启" else "已关闭"
    }

    private fun renderUnavailable() {
        if (_binding == null) {
            return
        }
        binding.textStatus.visibility = View.VISIBLE
        binding.textStepCount.text = "0.0"
        binding.textDistance.text = "0.0 m"
        binding.textStepLength.text = "0.00 m"
        binding.textMotion.text = "未运动"
        binding.textMockEnabled.text = "已关闭"
        binding.textSensorEnabled.text = "已关闭"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
