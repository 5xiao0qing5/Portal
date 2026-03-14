package moe.fuqiuluo.portal.ui.mock

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.android.widget.RockerView
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.databinding.FragmentMockBinding
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.drawOverOtherAppsEnabled
import moe.fuqiuluo.portal.ext.historicalLocations
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.needOpenSELinux
import moe.fuqiuluo.portal.ext.rawHistoricalLocations
import moe.fuqiuluo.portal.ext.selectLocation
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockViewModel
import moe.fuqiuluo.xposed.utils.FakeLoc
import java.math.BigDecimal

class MockFragment : Fragment() {
    private var _binding: FragmentMockBinding? = null
    private val binding get() = _binding!!

    private val mockViewModel by lazy { ViewModelProvider(this)[MockViewModel::class.java] }
    private val baiduMapViewModel by activityViewModels<BaiduMapViewModel>()
    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()
    private lateinit var historicalLocationAdapter: HistoricalLocationAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMockBinding.inflate(inflater, container, false)

        binding.fabMockLocation.setOnClickListener {
            if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
                Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        binding.fabMockLocation.setOnLongClickListener {
            Toast.makeText(requireContext(), "系统应用", Toast.LENGTH_SHORT).show()
            true
        }

        if (mockServiceViewModel.isServiceStart()) {
            binding.switchMock.text = "停止模拟"
            ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                binding.switchMock.icon = it
            }
        }

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
                    Toast.makeText(requireContext(), "閻庤鐭紞鍛村嫉瀹ュ懎顫ら柛鏃傚Ь濞村洤顕ｉ崒姘卞煑", Toast.LENGTH_SHORT).show()
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

            rocker.setRockerListener(object: RockerView.Companion.OnMoveListener {
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
            rocker.setOnSpeedChangedListener { speed ->
                FakeLoc.speed = speed
                val manager = locationManager ?: return@setOnSpeedChangedListener
                if (isServiceStart()) {
                    MockServiceHelper.setSpeed(manager, speed.toFloat())
                }
            }
        }

        requireContext().selectLocation?.let {
            applySelectedLocation(it)
        }

        val locations = requireContext().historicalLocations

        binding.mockLocationCard.setOnClickListener {
            val location = MockServiceHelper.getLocation(mockServiceViewModel.locationManager!!)
            Toast.makeText(requireContext(), "Location$location, ListenerSize: ${MockServiceHelper.getLocationListenerSize(mockServiceViewModel.locationManager!!)}", Toast.LENGTH_SHORT).show()
        }

        // 2024.10.10: sort historical locations
        historicalLocationAdapter = HistoricalLocationAdapter(locations.sortedBy { it.name }.toMutableList()) { loc, isLongClick ->
            if (isLongClick) {
                showEditHistoricalLocationDialog(loc)
            } else {
                applySelectedLocation(loc)
                showCellConfigIdleStatus()

                if (mockServiceViewModel.locationManager == null) {
                    Toast.makeText(requireContext(), "閻庤鐭紞鍛村嫉瀹ュ懎顫ら柛鏃傚Ь濞村洤顕ｉ崒姘卞煑", Toast.LENGTH_SHORT).show()
                    CrashReport.postCatchedException(RuntimeException("MockServiceViewModel.locationManager is null when selecting history location"))
                    return@HistoricalLocationAdapter
                }

                if (MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                    if (MockServiceHelper.setLocation(mockServiceViewModel.locationManager!!, loc.lat, loc.lon)) {
                        Toast.makeText(requireContext(), "位置更新成功", Toast.LENGTH_SHORT).show()
                        refreshCellConfig(loc.lat, loc.lon, forceRefresh = false)
                    } else {
                        Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val recyclerView = binding.historicalLocationList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historicalLocationAdapter
        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val location = historicalLocationAdapter[position]
                with(requireContext()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("删除位置")
                        .setMessage("确定要删除位置(${location.name})吗？")
                        .setPositiveButton("删除") { _, _ ->
                            historicalLocationAdapter.removeItem(position)
                            rawHistoricalLocations = rawHistoricalLocations.toMutableSet().apply {
                                remove(location.toString())
                            }
                            if (selectLocation?.toString() == location.toString()) {
                                selectLocation = null
                                mockServiceViewModel.selectedLocation = null
                            }
                            showToast("已删除位置")
                        }
                        .setNegativeButton("取消", { _, _ ->
                            historicalLocationAdapter.notifyItemChanged(position)
                        })
                        .show()
                }
            }
        }).attachToRecyclerView(recyclerView)

        binding.root.post {
            if (mockServiceViewModel.pendingHistoricalLocationMapPick &&
                !mockServiceViewModel.reopenHistoricalLocationEditDialog
            ) {
                clearPendingHistoricalLocationEditState()
            }
            reopenPendingHistoricalLocationEditorIfNeeded()
        }

        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun applySelectedLocation(location: HistoricalLocation) {
        binding.mockLocationName.text = location.name
        binding.mockLocationAddress.text = location.address
        binding.mockLocationLatlon.text = formatPreviewLatLon(location.lat, location.lon)
        mockServiceViewModel.selectedLocation = location
        requireContext().selectLocation = location
    }

    private fun reopenPendingHistoricalLocationEditorIfNeeded() {
        if (!mockServiceViewModel.reopenHistoricalLocationEditDialog) {
            return
        }
        mockServiceViewModel.reopenHistoricalLocationEditDialog = false
        val draft = mockServiceViewModel.pendingHistoricalLocationEditDraft ?: return
        val originalLocation = requireContext().historicalLocations.firstOrNull {
            it.toString() == draft.originalSerializedLocation
        }
        if (originalLocation == null) {
            clearPendingHistoricalLocationEditState()
            showToast("原始历史位置不存在")
            refreshHistoricalLocationList()
            return
        }
        showEditHistoricalLocationDialog(originalLocation, draft)
    }

    private fun showEditHistoricalLocationDialog(
        originalLocation: HistoricalLocation,
        draft: HistoricalLocationEditDraft? = null,
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_history_location, null)
        val nameLayout = dialogView.findViewById<TextInputLayout>(R.id.layoutHistoryLocationName)
        val latitudeLayout = dialogView.findViewById<TextInputLayout>(R.id.layoutHistoryLocationLatitude)
        val longitudeLayout = dialogView.findViewById<TextInputLayout>(R.id.layoutHistoryLocationLongitude)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.editHistoryLocationName)
        val latitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editHistoryLocationLatitude)
        val longitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editHistoryLocationLongitude)
        val editOnMapButton = dialogView.findViewById<MaterialButton>(R.id.buttonEditHistoryLocationOnMap)

        val initialDraft = draft ?: HistoricalLocationEditDraft(
            originalSerializedLocation = originalLocation.toString(),
            name = originalLocation.name,
            latitudeText = formatPlainCoordinate(originalLocation.lat),
            longitudeText = formatPlainCoordinate(originalLocation.lon),
            address = originalLocation.address,
        )

        nameEditText.setText(initialDraft.name)
        latitudeEditText.setText(initialDraft.latitudeText)
        longitudeEditText.setText(initialDraft.longitudeText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_history_location)
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消") { _, _ ->
                clearPendingHistoricalLocationEditState()
            }
            .create()

        dialog.setOnDismissListener {
            if (!mockServiceViewModel.pendingHistoricalLocationMapPick) {
                clearPendingHistoricalLocationEditState()
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val editedLocation = buildEditedHistoricalLocation(
                    originalLocation = originalLocation,
                    fallbackAddress = initialDraft.address,
                    nameLayout = nameLayout,
                    latitudeLayout = latitudeLayout,
                    longitudeLayout = longitudeLayout,
                    nameEditText = nameEditText,
                    latitudeEditText = latitudeEditText,
                    longitudeEditText = longitudeEditText,
                ) ?: return@setOnClickListener

                saveEditedHistoricalLocation(originalLocation, editedLocation)
                clearPendingHistoricalLocationEditState()
                dialog.dismiss()
            }

            editOnMapButton.setOnClickListener {
                openHistoricalLocationMapEditor(
                    originalLocation = originalLocation,
                    draft = HistoricalLocationEditDraft(
                        originalSerializedLocation = originalLocation.toString(),
                        name = nameEditText.text?.toString()?.trim().orEmpty().ifBlank { originalLocation.name },
                        latitudeText = latitudeEditText.text?.toString()?.trim().orEmpty()
                            .ifBlank { formatPlainCoordinate(originalLocation.lat) },
                        longitudeText = longitudeEditText.text?.toString()?.trim().orEmpty()
                            .ifBlank { formatPlainCoordinate(originalLocation.lon) },
                        address = initialDraft.address,
                    ),
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun buildEditedHistoricalLocation(
        originalLocation: HistoricalLocation,
        fallbackAddress: String,
        nameLayout: TextInputLayout,
        latitudeLayout: TextInputLayout,
        longitudeLayout: TextInputLayout,
        nameEditText: TextInputEditText,
        latitudeEditText: TextInputEditText,
        longitudeEditText: TextInputEditText,
    ): HistoricalLocation? {
        nameLayout.error = null
        latitudeLayout.error = null
        longitudeLayout.error = null

        val name = nameEditText.text?.toString()?.trim().orEmpty()
        val latitude = latitudeEditText.text?.toString()?.trim().orEmpty().toDoubleOrNull()
        val longitude = longitudeEditText.text?.toString()?.trim().orEmpty().toDoubleOrNull()
        var hasError = false

        if (name.isBlank()) {
            nameLayout.error = "名称不能为空"
            hasError = true
        }
        if (latitude == null || latitude !in -90.0..90.0) {
            latitudeLayout.error = "纬度格式错误"
            hasError = true
        }
        if (longitude == null || longitude !in -180.0..180.0) {
            longitudeLayout.error = "经度格式错误"
            hasError = true
        }

        val hasDuplicateName = requireContext().historicalLocations.any {
            it.toString() != originalLocation.toString() && it.name == name
        }
        if (!hasError && hasDuplicateName) {
            nameLayout.error = "位置名称已存在"
            hasError = true
        }

        if (hasError) {
            return null
        }

        return HistoricalLocation(
            name = name,
            address = fallbackAddress,
            lat = latitude!!,
            lon = longitude!!,
        )
    }

    private fun openHistoricalLocationMapEditor(
        originalLocation: HistoricalLocation,
        draft: HistoricalLocationEditDraft,
    ) {
        mockServiceViewModel.pendingHistoricalLocationEditDraft = draft
        mockServiceViewModel.pendingHistoricalLocationMapPick = true
        mockServiceViewModel.reopenHistoricalLocationEditDialog = false

        val latitude = draft.latitudeText.toDoubleOrNull() ?: originalLocation.lat
        val longitude = draft.longitudeText.toDoubleOrNull() ?: originalLocation.lon
        baiduMapViewModel.markedLoc = latitude to longitude
        baiduMapViewModel.markName = draft.address

        if (findNavController().currentDestination?.id != R.id.nav_home) {
            findNavController().navigate(R.id.nav_home)
        }
        showToast("请在地图中单击或长按选择新位置")
    }

    private fun saveEditedHistoricalLocation(
        originalLocation: HistoricalLocation,
        editedLocation: HistoricalLocation,
    ) {
        val originalSerialized = originalLocation.toString()
        val shouldUpdateSelection = mockServiceViewModel.selectedLocation?.toString() == originalSerialized ||
            requireContext().selectLocation?.toString() == originalSerialized

        with(requireContext()) {
            rawHistoricalLocations = rawHistoricalLocations.toMutableSet().apply {
                remove(originalSerialized)
                add(editedLocation.toString())
            }
        }

        refreshHistoricalLocationList()

        if (shouldUpdateSelection) {
            applySelectedLocation(editedLocation)
            if (mockServiceViewModel.isServiceStart()) {
                val locationManager = mockServiceViewModel.locationManager
                if (locationManager != null && MockServiceHelper.setLocation(
                        locationManager,
                        editedLocation.lat,
                        editedLocation.lon,
                    )
                ) {
                    refreshCellConfig(editedLocation.lat, editedLocation.lon, forceRefresh = false)
                    showToast("位置已更新")
                } else {
                    showToast("更新位置失败")
                }
            } else {
                showCellConfigIdleStatus()
                showToast("位置已更新")
            }
        } else {
            showToast("位置已更新")
        }
    }

    private fun refreshHistoricalLocationList() {
        if (!::historicalLocationAdapter.isInitialized) {
            return
        }
        historicalLocationAdapter.replaceAll(
            requireContext().historicalLocations.sortedBy { it.name }
        )
    }

    private fun clearPendingHistoricalLocationEditState() {
        mockServiceViewModel.pendingHistoricalLocationEditDraft = null
        mockServiceViewModel.pendingHistoricalLocationMapPick = false
        mockServiceViewModel.reopenHistoricalLocationEditDialog = false
    }

    private fun formatPlainCoordinate(value: Double): String {
        return BigDecimal.valueOf(value).toPlainString()
    }

    private fun formatPreviewLatLon(lat: Double, lon: Double): String {
        return "${formatPlainCoordinate(lat).take(8)}, ${formatPlainCoordinate(lon).take(8)}"
    }

    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            showToast("请授权悬浮窗权限")
            return
        }

        val selectedLocation = mockServiceViewModel.selectedLocation ?: run {
            showToast("请选择一个位置")
            return
        }

        if (mockServiceViewModel.locationManager == null) {
            showToast("閻庤鐭紞鍛村嫉瀹ュ懎顫ら柛鏃傚Ь濞村洤顕ｉ崒姘卞煑")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            val context = requireContext()
            val speed = context.speed
            val altitude = context.altitude
            val accuracy = FakeLoc.accuracy

            button.isClickable = false
            showCellConfigLoadingStatus()
            try {
                withContext(Dispatchers.IO) {
                    mockServiceViewModel.resetRouteMockState(disableAutoPlay = true)
                    mockServiceViewModel.locationManager!!.let {
                        if (MockServiceHelper.tryOpenMock(it, speed, altitude, accuracy)) {
                            updateMockButtonState(button, "停止模拟", R.drawable.rounded_play_disabled_24)
                        } else {
                            showToast("婵☆垪鍓濈€氭瑩寮靛鍛潳闁告凹鍨版慨鈺傚緞鏉堫偉袝")
                            return@withContext
                        }

                        if (!MockServiceHelper.setLocation(it, selectedLocation.lat, selectedLocation.lon)) {
                            showToast("更新位置失败")
                            return@let
                        }
                        val cellRefresh = MockServiceHelper.refreshCellConfigByOpenCellId(
                            it,
                            context,
                            selectedLocation.lat,
                            selectedLocation.lon
                        )
                        reportCellRefreshResult(cellRefresh, forceRefresh = false)

                        if (MockServiceHelper.broadcastLocation(it)) {
                            showToast("更新位置成功")
                        } else {
                            showToast("更新位置失败")
                        }
                    }
                }
            } finally {
                button.isClickable = true
            }
        }


    }

    private fun tryCloseService(button: MaterialButton) {
        if (mockServiceViewModel.locationManager == null) {
            showToast("閻庤鐭紞鍛村嫉瀹ュ懎顫ら柛鏃傚Ь濞村洤顕ｉ崒姘卞煑")
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
                    if (!MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                        showToast("模拟服务未启动")
                        return@withContext false
                    }

                    if (MockServiceHelper.tryCloseMock(mockServiceViewModel.locationManager!!)) {
                        updateMockButtonState(button, "开始模拟", R.drawable.rounded_play_arrow_24)
                        return@withContext true
                    } else {
                        showToast("婵☆垪鍓濈€氭瑩寮靛鍛潳闁稿绮嶉娑欏緞鏉堫偉袝")
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
        val selectedLocation = mockServiceViewModel.selectedLocation ?: run {
            showToast("请选择一个位置")
            return
        }
        refreshCellConfig(selectedLocation.lat, selectedLocation.lon, forceRefresh)
    }

    private fun refreshCellConfig(lat: Double, lon: Double, forceRefresh: Boolean) {
        val locationManager = mockServiceViewModel.locationManager ?: run {
            showToast("閻庤鐭紞鍛村嫉瀹ュ懎顫ら柛鏃傚Ь濞村洤顕ｉ崒姘卞煑")
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

    private fun updateMockButtonState(button: MaterialButton, text: String, iconRes: Int) = lifecycleScope.launch(Dispatchers.Main) {
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
} //
