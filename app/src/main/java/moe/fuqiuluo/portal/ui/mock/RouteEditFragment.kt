package moe.fuqiuluo.portal.ui.mock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.LogoPosition
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import moe.fuqiuluo.portal.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.Portal
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.bdmap.Poi
import moe.fuqiuluo.portal.bdmap.locateMe
import moe.fuqiuluo.portal.bdmap.setMapConfig
import moe.fuqiuluo.portal.bdmap.toPoi
import moe.fuqiuluo.portal.databinding.FragmentRouteEditBinding
import moe.fuqiuluo.portal.ext.gcj02
import moe.fuqiuluo.portal.ext.jsonHistoricalRoutes
import moe.fuqiuluo.portal.ext.mapType
import moe.fuqiuluo.portal.ext.wgs84
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import java.math.BigDecimal
import java.util.List


class RouteEditFragment : Fragment() {
    private var _binding: FragmentRouteEditBinding? = null
    private val binding get() = _binding!!

    private val routeEditViewModel by viewModels<HomeViewModel>()
    private lateinit var mLocationClient: LocationClient
    private val baiduMapViewModel by activityViewModels<BaiduMapViewModel>()

    private var mPoints: ArrayList<Pair<Double, Double>> = arrayListOf()
    private var isDrawing = false
    private var hasAutoCenteredToCurrentLocation = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteEditBinding.inflate(inflater, container, false)

        with(baiduMapViewModel) {
            isExists = true
            baiduMap = binding.bmapView.map
        }

        with(binding.bmapView) {
            showZoomControls(true)
            showScaleControl(true)
            logoPosition = LogoPosition.logoPostionRightTop
        }

        with(binding.bmapView.map) {
            setMapStatus(MapStatusUpdateFactory.zoomTo(19f))

            mapType = context?.mapType ?: BaiduMap.MAP_TYPE_NORMAL
            compassPosition = Point(50, 50)
            setCompassEnable(true)
            uiSettings.isCompassEnabled = true
            uiSettings.isOverlookingGesturesEnabled = true
            isMyLocationEnabled = true

            setMapConfig(
                baiduMapViewModel.perspectiveState,
                null
            )

            setOnMapClickListener(object : BaiduMap.OnMapClickListener {
                override fun onMapClick(loc: LatLng) {
                    if (isDrawing) {
                        addRoutePoint(loc.wgs84)
                        return
                    }

                    // 默认获取的gcj02坐标，需要转换一下
                    baiduMapViewModel.markedLoc = loc.wgs84

                    lifecycleScope.launch {
                        baiduMapViewModel.showDetailView = false
                        baiduMapViewModel.mGeoCoder?.reverseGeoCode(
                            ReverseGeoCodeOption().location(
                                loc
                            )
                        )
                    }

                    // Fixed the issue that getting geolocation information was stuck
                    lifecycleScope.launch {
                        markMap()
                    }
                }

                override fun onMapPoiClick(poi: MapPoi) {}
            })

            setOnMapLongClickListener { loc ->
                if (loc == null) return@setOnMapLongClickListener

                // 默认获取的gcj02坐标，需要转换一下
                baiduMapViewModel.markedLoc = loc.wgs84
                lifecycleScope.launch {
                    baiduMapViewModel.showDetailView = true
                    baiduMapViewModel.mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(loc))
                }
                lifecycleScope.launch {
                    markMap()
                }
            }

            binding.mapTypeGroup.check(
                when (mapType) {
                    BaiduMap.MAP_TYPE_NORMAL -> moe.fuqiuluo.portal.R.id.map_type_normal
                    BaiduMap.MAP_TYPE_SATELLITE -> moe.fuqiuluo.portal.R.id.map_type_satellite
                    else -> moe.fuqiuluo.portal.R.id.map_type_normal
                }
            )

            baiduMapViewModel.currentLocation?.let {
                setMapStatus(MapStatusUpdateFactory.newLatLng(it.gcj02))
                hasAutoCenteredToCurrentLocation = true
            }
        }

        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(
                binding.fabStart,
                binding.fabRollback,
                binding.fabComplete
            )

            if (!routeEditViewModel.mFabOpened) {
                routeEditViewModel.mFabOpened = true

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
                routeEditViewModel.mFabOpened = false

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

        mLocationClient = LocationClient(requireContext())
        val option = LocationClientOption()
        option.isOpenGps = true
        option.enableSimulateGps = false
        option.setIsNeedAddress(true) /* 关掉这个无法获取当前城市 */
        option.setNeedDeviceDirect(true)
        option.isLocationNotify = true
        option.setIgnoreKillProcess(true)
        option.setIsNeedLocationDescribe(false)
        option.setIsNeedLocationPoiList(false)
        option.isOpenGnss = true
        option.setIsNeedAltitude(false)
        option.locationMode = LocationClientOption.LocationMode.Hight_Accuracy

        option.setCoorType(Portal.DEFAULT_COORD_STR)
        option.setScanSpan(1000)
        mLocationClient.locOption = option
        mLocationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(loc: BDLocation?) {
                if (loc == null) return
                val locData = MyLocationData.Builder()
                    .accuracy(loc.radius)
                    .direction(loc.direction)
                    .latitude(loc.latitude)
                    .longitude(loc.longitude)
                    .build()

                if (loc.city != null)
                    MainActivity.mCityString = loc.city

                with(baiduMapViewModel) {
                    currentLocation = loc.wgs84
                    baiduMap.setMyLocationData(locData)
                    if (!hasAutoCenteredToCurrentLocation) {
                        baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(currentLocation!!.gcj02))
                        hasAutoCenteredToCurrentLocation = true
                    }
                }
            }
        })
        baiduMapViewModel.mLocationClient = mLocationClient
        mLocationClient.enableLocInForeground(1, baiduMapViewModel.mNotification)
        mLocationClient.start()

        binding.mapTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                moe.fuqiuluo.portal.R.id.map_type_normal -> {
                    binding.bmapView.map.mapType = BaiduMap.MAP_TYPE_NORMAL
                }

                moe.fuqiuluo.portal.R.id.map_type_satellite -> {
                    binding.bmapView.map.mapType = BaiduMap.MAP_TYPE_SATELLITE
                }

                else -> {
                    Log.e("HomeFragment", "Unknown location view mode: $checkedId")
                }
            }
            context?.mapType = binding.bmapView.map.mapType
        }

        binding.fabStart.setOnClickListener {
            isDrawing = true
            mPoints = arrayListOf()
            refresh()
            Toast.makeText(requireContext(), "点击地图手动添加点，系统会自动连线", Toast.LENGTH_SHORT).show()
        }

        binding.fabRollback.setOnClickListener {
            // 撤回上一个点并且刷新地图
            if (mPoints.isNotEmpty()) {
                mPoints.removeAt(mPoints.size - 1)
                refresh()
            }
        }

        binding.fabComplete.setOnClickListener {
            isDrawing = false
            if (mPoints.size < 2) {
                Toast.makeText(requireContext(), "路线至少需要两个点", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!showAddRouteDialog()) {
                Toast.makeText(requireContext(), "选择路线异常", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            baiduMapViewModel.baiduMap.locateMe()
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        baiduMapViewModel.onPoiSelected = { poi ->
            val point = poi.latitude to poi.longitude
            baiduMapViewModel.markName = poi.name
            baiduMapViewModel.markedLoc = point
            val gcjLoc = point.gcj02
            baiduMapViewModel.baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(gcjLoc))
            if (isDrawing) {
                addRoutePoint(point)
                Toast.makeText(requireContext(), "已添加搜索点", Toast.LENGTH_SHORT).show()
            } else {
                markMap(moveEyes = true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (baiduMapViewModel.onPoiSelected != null) {
            baiduMapViewModel.onPoiSelected = null
        }
    }

    private fun refresh() {
        baiduMapViewModel.baiduMap.clear() // 清除之前的所有覆盖物

        mPoints.forEachIndexed { index, point ->
            baiduMapViewModel.baiduMap.addOverlay(
                MarkerOptions()
                    .position(point.gcj02)
                    .icon(createPointMarker(index + 1))
            )
        }

        // 绘制之前记录的点到点的线
        for (i in 0 until mPoints.size - 1) {
            baiduMapViewModel.baiduMap.addOverlay(
                PolylineOptions()
                    .color(Color.argb(178, 0, 78, 255))
                    .width(10)
                    .points(List.of<LatLng>(mPoints[i].gcj02, mPoints[i + 1].gcj02))
            )
        }
    }

    private fun addRoutePoint(point: Pair<Double, Double>) {
        mPoints.add(point)
        baiduMapViewModel.markedLoc = point
        refresh()
    }

    private fun createPointMarker(index: Int): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (28 * density).toInt().coerceAtLeast(56)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 0, 78, 255)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 12 * density
            isFakeBoldText = true
        }

        val radius = size / 2f - 2 * density
        val center = size / 2f
        canvas.drawCircle(center, center, radius, circlePaint)
        canvas.drawCircle(center, center, radius, borderPaint)

        val text = index.toString()
        val baseline = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, center, baseline, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun showSearchPlaceDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_poi, null)
        val editKeyword = dialogView.findViewById<TextInputEditText>(R.id.etSearchKeyword)
        val resultList = dialogView.findViewById<ListView>(R.id.searchResultList)
        val suggestionSearch = SuggestionSearch.newInstance()
        var searchJob: Job? = null

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("联网搜索地点")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .create()

        suggestionSearch.setOnGetSuggestionResultListener { suggestionResult ->
            if (!isAdded) {
                return@setOnGetSuggestionResultListener
            }
            val data = suggestionResult?.toPoi(baiduMapViewModel.currentLocation)
                ?.map { it.toMap() }
                .orEmpty()

            if (data.isEmpty()) {
                Toast.makeText(requireContext(), "未搜索到相关位置", Toast.LENGTH_SHORT).show()
                resultList.adapter = null
                return@setOnGetSuggestionResultListener
            }

            resultList.adapter = SimpleAdapter(
                requireContext(),
                data,
                R.layout.layout_search_poi_item,
                arrayOf(
                    Poi.KEY_NAME,
                    Poi.KEY_ADDRESS,
                    Poi.KEY_LONGITUDE_RAW,
                    Poi.KEY_LATITUDE_RAW,
                    Poi.KEY_TAG
                ),
                intArrayOf(
                    R.id.poi_name,
                    R.id.poi_address,
                    R.id.poi_longitude,
                    R.id.poi_latitude,
                    R.id.poi_tag
                )
            )
        }

        fun requestSearch(keyword: String) {
            val normalizedKeyword = keyword.trim()
            if (normalizedKeyword.length < 2) {
                resultList.adapter = null
                return
            }
            try {
                suggestionSearch.requestSuggestion(createSuggestionSearchOption(normalizedKeyword))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "搜索出错", Toast.LENGTH_SHORT).show()
                Log.e("RouteEditFragment", "Search error: ${e.stackTraceToString()}")
            }
        }

        editKeyword.addTextChangedListener {
            searchJob?.cancel()
            val keyword = it?.toString().orEmpty().trim()
            if (keyword.length < 2) {
                resultList.adapter = null
                return@addTextChangedListener
            }
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                if (!dialog.isShowing) {
                    return@launch
                }
                requestSearch(keyword)
            }
        }

        resultList.setOnItemClickListener { _, view, _, _ ->
            val lng = view.findViewById<android.widget.TextView>(R.id.poi_longitude).text.toString().toDouble()
            val lat = view.findViewById<android.widget.TextView>(R.id.poi_latitude).text.toString().toDouble()
            val point = lat to lng
            baiduMapViewModel.markedLoc = point
            baiduMapViewModel.markName = view.findViewById<android.widget.TextView>(R.id.poi_name).text.toString()

            val gcjLoc = point.gcj02
            baiduMapViewModel.baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(gcjLoc))

            if (isDrawing) {
                addRoutePoint(point)
                Toast.makeText(requireContext(), "已添加搜索点", Toast.LENGTH_SHORT).show()
            } else {
                markMap(moveEyes = true)
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            searchJob?.cancel()
            suggestionSearch.destroy()
        }
        dialog.show()
    }

    private fun createSuggestionSearchOption(keyword: String): SuggestionSearchOption {
        return SuggestionSearchOption()
            .keyword(keyword.trim())
            .city(MainActivity.mCityString ?: "")
            .citylimit(false)
            .apply {
                baiduMapViewModel.currentLocation?.let {
                    location(it.gcj02)
                }
            }
    }


    private fun markMap(moveEyes: Boolean = false) = with(baiduMapViewModel) {
        val loc = markedLoc!!.gcj02
        val ooA = MarkerOptions()
            .position(loc)
            .icon(mMapIndicator)
        baiduMap.clear()
        baiduMap.addOverlay(ooA)

        if (moveEyes) {
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(loc))
        }
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId", "MutatingSharedPrefs")
    private fun showAddRouteDialog(): Boolean {
        fun checkLatLon(lat: Double?, lon: Double?): Boolean {
            return (lat != null && lon != null) && lat in -90.0..90.0 && lon in -180.0..180.0
        }

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_add_route, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.etRouteName)
        editName.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editName.error = "名称不能为空"
            }
        }
        val editRoute = dialogView.findViewById<TextInputEditText>(R.id.etRouteSet)
        editRoute.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editRoute.error = "路线经纬度不能为空"
            } else {
                try {
                    val json = it.toString()
                    // 转为 LatLng 数组
                    val points = JSON.parseArray(json)
                    if (points.size < 2) {
                        editRoute.error = "路线经纬度至少需要两个点"
                    }
                    // 循环检查每个点的经纬度是否合法
                    for (point in points) {
                        val jsonObject = point as JSONObject
                        val latitude = jsonObject.getDouble("first")
                        val longitude = jsonObject.getDouble("second")
                        if (!checkLatLon(latitude, longitude)) {
                            editRoute.error = "路线经纬度格式错误"
                            return@addTextChangedListener
                        }
                    }
                } catch (e: Exception) {
                    editRoute.error = "路线经纬度json格式错误"
                }
            }
        }

        editRoute.setText(JSON.toJSONString(mPoints))

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(null)
        builder
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val routeJson = editRoute.text.toString()

                var name = editName.text?.toString()
                if (name.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val points = JSON.parseArray(routeJson)
                if (points.size < 2) {
                    Toast.makeText(requireContext(), "路线经纬度至少需要两个点", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                // 循环检查每个点的经纬度是否合法
                for (point in points) {
                    val jsonObject = point as JSONObject
                    val latitude = jsonObject.getDouble("first")
                    val longitude = jsonObject.getDouble("second")
                    if (!checkLatLon(latitude, longitude)) {
                        Toast.makeText(requireContext(), "路线经纬度格式错误", Toast.LENGTH_SHORT)
                            .show()
                        return@setPositiveButton
                    }
                }

                fun MutableSet<String>.addLocation(
                    name: String,
                    address: String,
                    lat: Double,
                    lon: Double
                ): Boolean {
                    if (any { it.split(",")[0] == name }) {
                        return false
                    }
                    add(
                        "$name,$address,${
                            BigDecimal.valueOf(lat).toPlainString()
                        },${BigDecimal.valueOf(lon).toPlainString()}"
                    )
                    return true
                }

                val route = JSON.toJSONString(points)
                with(requireContext()) {
                    val routes = jsonHistoricalRoutes
                    val jsonArray: JSONArray = if (routes.isNotEmpty()) {
                        JSON.parseArray(routes)
                    } else {
                        JSONArray()
                    }
                    val historicalRoute = HistoricalRoute(name, mPoints)
                    jsonArray.add(historicalRoute)
                    jsonArray.toJSONString().also {
                        jsonHistoricalRoutes = it
                    }
                }

                Toast.makeText(requireContext(), "路线已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()

        return true
    }
}
