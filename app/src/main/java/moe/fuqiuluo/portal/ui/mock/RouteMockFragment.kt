package moe.fuqiuluo.portal.ui.mock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.fastjson2.JSON
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.widget.RockerView
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.databinding.FragmentRouteMockBinding
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.drawOverOtherAppsEnabled
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.jsonHistoricalRoutes
import moe.fuqiuluo.portal.ext.routeMockLoopCount
import moe.fuqiuluo.portal.ext.routeMockLoopEnabled
import moe.fuqiuluo.portal.ext.routeMockLoopIntervalSeconds
import moe.fuqiuluo.portal.ext.routeMockSpeed
import moe.fuqiuluo.portal.ext.routeMockSpeedFluctuationEnabled
import moe.fuqiuluo.portal.ext.routeMockStepFrequencyEnabled
import moe.fuqiuluo.portal.ext.selectRoute
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.xposed.utils.FakeLoc
import kotlin.math.roundToInt

class RouteMockFragment : Fragment() {
    private var _binding: FragmentRouteMockBinding? = null
    private val binding get() = _binding!!

    private val routeMockViewModel by viewModels<HomeViewModel>()
    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteMockBinding.inflate(inflater, container, false)

        routeMockViewModel.mFabOpened = false

        if (mockServiceViewModel.isServiceStart()) {
            binding.switchMock.text = "停止模拟"
            ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                binding.switchMock.icon = it
            }
        }

        initRouteMockConfigUI()

        binding.switchMock.setOnClickListener {
            if (mockServiceViewModel.isServiceStart()) {
                tryCloseService(it as MaterialButton)
            } else {
                tryOpenService(it as MaterialButton)
            }
        }

        binding.buttonRefreshCellConfig.setOnClickListener {
            refreshSelectedCellConfig(forceRefresh = true)
        }

        showCellConfigIdleStatus()

        with(mockServiceViewModel) {
            if (rocker.isStart) {
                binding.rocker.toggle()
            }
            binding.rocker.setOnClickListener {
                if (locationManager == null) {
                    Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!isServiceStart()) {
                    Toast.makeText(requireContext(), "请先启动模拟", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!requireContext().drawOverOtherAppsEnabled()) {
                    Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val checkedTextView = it as CheckedTextView
                checkedTextView.toggle()

                lifecycleScope.launch(Dispatchers.Main) {
                    if (checkedTextView.isChecked) {
                        rocker.show()
                    } else {
                        rocker.hide()
                        rockerCoroutineController.pause()
                        locationManager?.let { manager ->
                            MockServiceHelper.clearMotion(manager)
                        }
                    }
                }
            }
            rocker.setRockerListener(object : RockerView.Companion.OnMoveListener {
                override fun onAngle(angle: Double) {
                    MockServiceHelper.setBearing(locationManager!!, angle)
                    FakeLoc.bearing = angle
                    FakeLoc.hasBearings = true
                }

                override fun onLockChanged(isLocked: Boolean) {
                    isRockerLocked = isLocked
                }

                override fun onFinished() {
                    if (!isRockerLocked) {
                        rockerCoroutineController.pause()
                        locationManager?.let { manager ->
                            MockServiceHelper.clearMotion(manager)
                        }
                    }
                }

                override fun onStarted() {
                    rockerCoroutineController.resume()
                }
            })
            rocker.setRockerAutoListener(object : Rocker.Companion.OnAutoListener {
                override fun onAutoPlay(isPlay: Boolean) {
                    isRouteStart = isPlay
                    if (isPlay) {
                        routeMockCoroutine.resume()
                    } else {
                        routeMockCoroutine.pause()
                        locationManager?.let { manager ->
                            MockServiceHelper.clearMotion(manager)
                        }
                    }
                }

                override fun onAutoLock(isLock: Boolean) = Unit
            })
            rocker.setOnSpeedChangedListener { speed ->
                FakeLoc.speed = speed
                val manager = locationManager ?: return@setOnSpeedChangedListener
                if (isServiceStart()) {
                    MockServiceHelper.setSpeed(manager, speed.toFloat())
                }
            }
        }

        requireContext().selectRoute?.let {
            if (it.isValidForMock()) {
                binding.mockRouteName.text = it.name
                mockServiceViewModel.selectedRoute = it
            } else {
                requireContext().selectRoute = null
            }
        }

        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(binding.fabAddRoute)

            if (!routeMockViewModel.mFabOpened) {
                routeMockViewModel.mFabOpened = true

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 0f, 90f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    fab.visibility = View.VISIBLE
                    fab.alpha = 1f
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                    val translationX =
                        ObjectAnimator.ofFloat(fab, "translationX", 0f, 20f + index * 8f)
                    translationX.duration = 200
                    animators.add(translationX)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            } else {
                routeMockViewModel.mFabOpened = false

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 90f, 0f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    val transX = ObjectAnimator.ofFloat(fab, "translationX", 0f, -20f - index * 8f)
                    transX.duration = 150
                    val scaleX = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
                    scaleX.duration = 200
                    val scaleY = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
                    scaleY.duration = 200
                    val alpha = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)
                    alpha.duration = 200
                    animators.add(transX)
                    animators.add(scaleX)
                    animators.add(scaleY)
                    animators.add(alpha)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        subFabList.forEach { it.visibility = View.GONE }
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            }
        }

        binding.fabAddRoute.setOnClickListener {
            activity?.findNavController(R.id.nav_host_fragment_content_main)
                ?.navigate(R.id.nav_route_edit)
        }

        var routesJson = requireContext().jsonHistoricalRoutes
        if (routesJson.isBlank()) {
            val defaultRoute = HistoricalRoute(
                "天安门短路线",
                listOf(Pair(39.908822, 116.397465), Pair(39.907951, 116.397500))
            )
            requireContext().jsonHistoricalRoutes = JSON.toJSONString(listOf(defaultRoute))
            routesJson = requireContext().jsonHistoricalRoutes
        }
        val routes = JSON.parseArray(routesJson, HistoricalRoute::class.java)

        val historicalRouteAdapter = HistoricalRouteAdapter(
            routes.sortedBy { it.name }.toMutableList()
        ) { route, isLongClick ->
            if (isLongClick) {
                Toast.makeText(requireContext(), "长按", Toast.LENGTH_SHORT).show()
                return@HistoricalRouteAdapter
            }
            if (!route.isValidForMock()) {
                Toast.makeText(requireContext(), "路线至少需要两个点", Toast.LENGTH_SHORT).show()
                return@HistoricalRouteAdapter
            }

            binding.mockRouteName.text = route.name
            mockServiceViewModel.selectedRoute = route
            mockServiceViewModel.resetRouteMockState(disableAutoPlay = true)
            requireContext().selectRoute = route
            showCellConfigIdleStatus()

            val locationManager = mockServiceViewModel.locationManager
            if (locationManager == null) {
                Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                return@HistoricalRouteAdapter
            }

            if (MockServiceHelper.isMockStart(locationManager)) {
                val first = route.route.first()
                if (MockServiceHelper.setLocation(locationManager, first.first, first.second)) {
                    Toast.makeText(requireContext(), "路线起点已更新", Toast.LENGTH_SHORT).show()
                    refreshCellConfig(first.first, first.second, forceRefresh = false)
                } else {
                    Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val recyclerView = binding.historicalRouteList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historicalRouteAdapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val route = historicalRouteAdapter[position]
                with(requireContext()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("删除路线")
                        .setMessage("确定要删除路线(${route.name})吗？")
                        .setPositiveButton("删除") { _, _ ->
                            historicalRouteAdapter.removeItem(position)
                            JSON.parseArray(jsonHistoricalRoutes, HistoricalRoute::class.java)
                                .toMutableList()
                                .apply {
                                    removeIf { it.name == route.name }
                                }
                                .let {
                                    jsonHistoricalRoutes = JSON.toJSONString(it)
                                }
                            if (mockServiceViewModel.selectedRoute?.name == route.name) {
                                mockServiceViewModel.selectedRoute = null
                                mockServiceViewModel.resetRouteMockState(disableAutoPlay = true)
                                selectRoute = null
                                binding.mockRouteName.text = getString(R.string.none_route)
                            }
                            showToast("已删除路线")
                        }
                        .setNegativeButton("取消") { _, _ ->
                            historicalRouteAdapter.notifyItemChanged(position)
                        }
                        .show()
                }
            }
        }).attachToRecyclerView(recyclerView)

        return binding.root
    }

    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            showToast("请授权悬浮窗权限")
            return
        }

        val selectedRoute = mockServiceViewModel.selectedRoute ?: run {
            showToast("请选择一条路线")
            return
        }
        if (!selectedRoute.isValidForMock()) {
            showToast("路线至少需要两个点")
            return
        }

        val locationManager = mockServiceViewModel.locationManager ?: run {
            showToast("定位服务加载异常")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            val context = requireContext()
            val speed = context.routeMockSpeed.toDouble()
            val altitude = context.altitude
            val accuracy = FakeLoc.accuracy

            button.isClickable = false
            showCellConfigLoadingStatus()
            try {
                withContext(Dispatchers.IO) {
                    mockServiceViewModel.resetRouteMockState(disableAutoPlay = true)
                    mockServiceViewModel.routeMockSpeed = speed
                    mockServiceViewModel.routeMockLoopEnabled = context.routeMockLoopEnabled
                    mockServiceViewModel.routeMockLoopCount = context.routeMockLoopCount
                    mockServiceViewModel.routeMockLoopIntervalSeconds = context.routeMockLoopIntervalSeconds
                    if (MockServiceHelper.tryOpenMock(locationManager, speed, altitude, accuracy)) {
                        updateMockButtonState(button, "停止模拟", R.drawable.rounded_play_disabled_24)
                        mockServiceViewModel.routeStage = 0
                    } else {
                        showToast("模拟服务启动失败")
                        return@withContext
                    }

                    context.hookSensor = context.routeMockStepFrequencyEnabled
                    FakeLoc.speedAmplitude = if (context.routeMockSpeedFluctuationEnabled) 1.0 else 0.0
                    MockServiceHelper.putConfig(locationManager, context)
                    MockServiceHelper.setSpeed(locationManager, context.routeMockSpeed)
                    MockServiceHelper.setSpeedAmplitude(locationManager, FakeLoc.speedAmplitude)

                    val first = selectedRoute.route.first()
                    if (!MockServiceHelper.setLocation(locationManager, first.first, first.second)) {
                        showToast("更新位置失败")
                        return@withContext
                    }
                    val cellRefresh = MockServiceHelper.refreshCellConfigByOpenCellId(
                        locationManager,
                        context,
                        first.first,
                        first.second
                    )
                    reportCellRefreshResult(cellRefresh, forceRefresh = false)
                    showToast("路线起点已更新")
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    private fun tryCloseService(button: MaterialButton) {
        val locationManager = mockServiceViewModel.locationManager ?: run {
            showToast("定位服务加载异常")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                val isClosed = withContext(Dispatchers.IO) {
                    if (!MockServiceHelper.isMockStart(locationManager)) {
                        showToast("模拟服务未启动")
                        return@withContext false
                    }

                    if (MockServiceHelper.tryCloseMock(locationManager)) {
                        updateMockButtonState(button, "开始模拟", R.drawable.rounded_play_arrow_24)
                        return@withContext true
                    } else {
                        showToast("模拟服务停止失败")
                        return@withContext false
                    }
                }
                if (isClosed) {
                    mockServiceViewModel.resetRouteMockState(disableAutoPlay = true)
                }
                if (isClosed && mockServiceViewModel.rocker.isStart) {
                    binding.rocker.isClickable = false
                    binding.rocker.toggle()
                    mockServiceViewModel.rocker.hide()
                    mockServiceViewModel.rockerCoroutineController.pause()
                    binding.rocker.isClickable = true
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    private fun refreshSelectedCellConfig(forceRefresh: Boolean) {
        val selectedRoute = mockServiceViewModel.selectedRoute ?: run {
            showToast("请选择一条路线")
            return
        }
        if (!selectedRoute.isValidForMock()) {
            showToast("路线至少需要两个点")
            return
        }
        val first = selectedRoute.route.first()
        refreshCellConfig(first.first, first.second, forceRefresh)
    }

    private fun refreshCellConfig(lat: Double, lon: Double, forceRefresh: Boolean) {
        val locationManager = mockServiceViewModel.locationManager ?: run {
            showToast("定位服务加载异常")
            return
        }
        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            binding.buttonRefreshCellConfig.isEnabled = false
            showCellConfigLoadingStatus()
            try {
                val result = withContext(Dispatchers.IO) {
                    MockServiceHelper.refreshCellConfigByOpenCellId(
                        locationManager,
                        requireContext(),
                        lat,
                        lon,
                        forceRefresh = forceRefresh
                    )
                }
                reportCellRefreshResult(result, forceRefresh)
            } finally {
                binding.buttonRefreshCellConfig.isEnabled = true
            }
        }
    }

    private fun reportCellRefreshResult(
        result: MockServiceHelper.CellRefreshResult,
        forceRefresh: Boolean,
    ) {
        if (!result.success) {
            showCellConfigFailedStatus(result.message)
            showToast("基站配置拉取失败: ${result.message}")
            return
        }
        when {
            result.message == "cell mock disabled" -> {
                showCellConfigDisabledStatus()
                showToast("基站模拟已关闭")
            }
            result.fromCache -> {
                showCellConfigCacheStatus()
                showToast("基站配置已从缓存应用")
            }
            forceRefresh -> {
                showCellConfigNetworkStatus()
                showToast("基站配置已重新拉取")
            }
            else -> {
                showCellConfigNetworkStatus()
                showToast("基站配置已拉取并缓存")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showToast(message: String) = lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateMockButtonState(button: MaterialButton, text: String, iconRes: Int) =
        lifecycleScope.launch(Dispatchers.Main) {
            button.text = text
            ContextCompat.getDrawable(requireContext(), iconRes)?.let {
                button.icon = it
            }
        }

    private fun showCellConfigIdleStatus() {
        updateCellConfigStatus(R.string.cell_config_status_default, R.attr.portalMiniTitleColor)
    }

    private fun showCellConfigLoadingStatus() {
        updateCellConfigStatus(R.string.cell_config_status_loading, R.attr.portalMiniTitleColor)
    }

    private fun showCellConfigCacheStatus() {
        updateCellConfigStatus(R.string.cell_config_status_cache, R.attr.portalMockOffColor)
    }

    private fun showCellConfigNetworkStatus() {
        updateCellConfigStatus(R.string.cell_config_status_network, R.attr.portalAppBarColorCenter)
    }

    private fun showCellConfigDisabledStatus() {
        updateCellConfigStatus(R.string.cell_config_status_disabled, R.attr.portalTextColor)
    }

    private fun showCellConfigFailedStatus(message: String) {
        updateCellConfigStatus(
            getString(R.string.cell_config_status_failed, message),
            com.google.android.material.R.attr.colorError,
        )
    }

    private fun updateCellConfigStatus(textRes: Int, @AttrRes colorAttr: Int) {
        updateCellConfigStatus(getString(textRes), colorAttr)
    }

    private fun updateCellConfigStatus(text: String, @AttrRes colorAttr: Int) = lifecycleScope.launch(Dispatchers.Main) {
        val currentBinding = _binding ?: return@launch
        currentBinding.textCellConfigStatus.text = text
        currentBinding.textCellConfigStatus.setTextColor(
            MaterialColors.getColor(currentBinding.root, colorAttr),
        )
    }

    @SuppressLint("SetTextI18n")
    private fun initRouteMockConfigUI() {
        val context = requireContext()
        val initialSpeed = normalizeRouteMockSpeed(context.routeMockSpeed)
        context.routeMockSpeed = initialSpeed
        mockServiceViewModel.routeMockSpeed = initialSpeed.toDouble()
        mockServiceViewModel.routeMockLoopEnabled = context.routeMockLoopEnabled
        mockServiceViewModel.routeMockLoopCount = context.routeMockLoopCount
        mockServiceViewModel.routeMockLoopIntervalSeconds = context.routeMockLoopIntervalSeconds

        binding.routeSpeedSlider.value = initialSpeed
        binding.routeSpeedValue.text = String.format("%.1f m/s", initialSpeed)
        binding.routeSpeedSlider.addOnChangeListener { _, value, _ ->
            context.routeMockSpeed = value
            mockServiceViewModel.routeMockSpeed = value.toDouble()
            binding.routeSpeedValue.text = String.format("%.1f m/s", value)
            syncRouteMockRuntimeConfig()
        }

        binding.routeSpeedFluctuationSwitch.isChecked = context.routeMockSpeedFluctuationEnabled
        binding.routeSpeedFluctuationSwitch.setOnCheckedChangeListener { _, isChecked ->
            context.routeMockSpeedFluctuationEnabled = isChecked
            syncRouteMockRuntimeConfig()
        }

        binding.routeStepFrequencySwitch.isChecked = context.routeMockStepFrequencyEnabled
        binding.routeStepFrequencySwitch.setOnCheckedChangeListener { _, isChecked ->
            context.routeMockStepFrequencyEnabled = isChecked
            context.hookSensor = isChecked
            syncRouteMockRuntimeConfig()
        }

        binding.routeLoopSwitch.isChecked = context.routeMockLoopEnabled
        binding.routeLoopSwitch.setOnCheckedChangeListener { _, isChecked ->
            context.routeMockLoopEnabled = isChecked
            mockServiceViewModel.routeMockLoopEnabled = isChecked
            refreshRouteLoopConfigState()
        }

        binding.routeLoopCountInput.setText(context.routeMockLoopCount.toString())
        binding.routeLoopCountInput.addTextChangedListener {
            val count = it?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            context.routeMockLoopCount = count
            mockServiceViewModel.routeMockLoopCount = count
        }

        binding.routeLoopIntervalInput.setText(context.routeMockLoopIntervalSeconds.toString())
        binding.routeLoopIntervalInput.addTextChangedListener {
            val interval = it?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            context.routeMockLoopIntervalSeconds = interval
            mockServiceViewModel.routeMockLoopIntervalSeconds = interval
        }

        refreshRouteLoopConfigState()
    }

    private fun refreshRouteLoopConfigState() {
        val enabled = binding.routeLoopSwitch.isChecked
        binding.routeLoopConfigGroup.alpha = if (enabled) 1f else 0.5f
        binding.routeLoopCountInput.isEnabled = enabled
        binding.routeLoopIntervalInput.isEnabled = enabled
    }

    private fun syncRouteMockRuntimeConfig() {
        val context = context ?: return
        val locationManager = mockServiceViewModel.locationManager ?: return
        if (!mockServiceViewModel.isServiceStart()) {
            return
        }
        mockServiceViewModel.routeMockSpeed = context.routeMockSpeed.toDouble()
        FakeLoc.speedAmplitude = if (context.routeMockSpeedFluctuationEnabled) 1.0 else 0.0
        context.hookSensor = context.routeMockStepFrequencyEnabled
        MockServiceHelper.putConfig(locationManager, context)
        MockServiceHelper.setSpeed(locationManager, context.routeMockSpeed)
        MockServiceHelper.setSpeedAmplitude(locationManager, FakeLoc.speedAmplitude)
    }

    private fun normalizeRouteMockSpeed(value: Float): Float {
        val clamped = value.coerceIn(0.5f, 20.0f)
        val steps = ((clamped - 0.5f) / 0.5f).roundToInt()
        return 0.5f + steps * 0.5f
    }

    private fun HistoricalRoute.isValidForMock(): Boolean {
        return route.size >= 2
    }
}
