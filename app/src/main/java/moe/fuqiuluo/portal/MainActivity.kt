package moe.fuqiuluo.portal

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS
import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.Manifest.permission.ACCESS_WIFI_STATE
import android.Manifest.permission.CHANGE_WIFI_STATE
import android.Manifest.permission.FOREGROUND_SERVICE
import android.Manifest.permission.INTERNET
import android.Manifest.permission.READ_PHONE_STATE
import android.Manifest.permission.VIBRATE
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.ImageView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.InfoWindow
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.google.android.material.navigation.NavigationView
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.permission.RequestPermissions
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.bdmap.Poi
import moe.fuqiuluo.portal.bdmap.toPoi
import moe.fuqiuluo.portal.databinding.ActivityMainBinding
import moe.fuqiuluo.portal.ext.gcj02
import moe.fuqiuluo.portal.ext.resolveThemeColor
import moe.fuqiuluo.portal.ext.themePreset
import moe.fuqiuluo.portal.ext.wgs84
import moe.fuqiuluo.portal.ui.notification.NotificationUtils
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    /* Permission */
    private val requestMultiplePermissions = RequestPermissions(this)

    /* BaiduMap */
    private var mSuggestionSearch: SuggestionSearch? = null
    private var suggestionSearchJob: Job? = null
    private val baiduMapViewModel by viewModels<BaiduMapViewModel>()
    private val mockServiceViewModel by viewModels<MockServiceViewModel>()

    private fun getRequiredPermissions(): MutableSet<String> {
        val permissions = mutableSetOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION,
            ACCESS_LOCATION_EXTRA_COMMANDS,
            ACCESS_WIFI_STATE,
            CHANGE_WIFI_STATE,
            READ_PHONE_STATE,
            INTERNET,
            ACCESS_NETWORK_STATE,
            VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(FOREGROUND_SERVICE)
        }
        return permissions
    }

    private fun handleDeniedPermissions(denied: Set<String>) {
        denied.forEach { permission ->
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showPermissionDeniedToast(permission)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSIONS_CODE)
            }
        }

        if (denied.isEmpty()) {
            requireFloatWindows()
        }
    }

    private fun showPermissionDeniedToast(permission: String) {
        val message = when (permission) {
            ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION -> "Portal needs location permission"
            ACCESS_LOCATION_EXTRA_COMMANDS -> "Portal needs extra location command permission"
            CHANGE_WIFI_STATE, ACCESS_WIFI_STATE -> "Portal needs Wi-Fi state access"
            READ_PHONE_STATE -> "Portal needs device info permission"
            ACCESS_NETWORK_STATE, INTERNET -> "Portal needs network access"
            VIBRATE -> "Portal needs vibration permission"
            else -> "Permission required: $permission"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private suspend fun checkPermission(): Boolean {
        val permissions = getRequiredPermissions()
        val (_, denied) = requestMultiplePermissions.request(permissions)
        handleDeniedPermissions(denied)
        return denied.isEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themePreset.styleRes)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = themePreset.isLightTheme
        controller.isAppearanceLightNavigationBars = themePreset.isLightTheme // 闂傚倷鑳剁划顖炩€﹂崼銉ユ槬闁哄稁鍘奸悞鍨亜閹达絾纭堕柛鏂跨Ч閺岋綁寮介弶鎴炵亾闂侀€炲苯澧紒瀣浮婵＄敻鎮欓悽鐢殿槸闁诲函缍嗛崑鎾舵閿濆棛绡€濠电姴鍊归ˉ婊勩亜?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resolveThemeColor(R.attr.portalAppBarColor)
        }

        CrashReport.setUserSceneTag(this, 261771)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        if (!ShellUtils.hasRoot()) {
            Toast.makeText(this, "Root is unavailable; some hooks may not work", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if(checkPermission()) {
                    mockServiceViewModel.locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                }

                initNotification()

                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)

                setSupportActionBar(binding.appBarMain.toolbar)

                binding.appBarMain.toolbar.setBackgroundColor(resolveThemeColor(R.attr.portalAppBarColor))
                binding.appBarMain.toolbar.setTitleTextColor(resolveThemeColor(R.attr.portalAppBarTextColor))
                binding.appBarMain.toolbar.setSubtitleTextColor(resolveThemeColor(R.attr.portalAppBarTextColor))
                binding.appBarMain.searchLinear.setBackgroundColor(resolveThemeColor(R.attr.portalCardColor))
                val drawerLayout: DrawerLayout = binding.drawerLayout
                val navView: NavigationView = binding.navView
                navView.itemIconTintList = createNavigationColorStateList()
                navView.itemTextColor = createNavigationColorStateList()
                val navController = findNavController(R.id.nav_host_fragment_content_main)

                // Passing each menu ID as a set of Ids because each
                // menu should be considered as top level destinations.
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.nav_home, R.id.nav_mock, R.id.nav_gnss_mock, R.id.nav_step_debug, R.id.nav_route_gallery, R.id.nav_settings
                    ), drawerLayout
                )

                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)

                binding.appBarMain.toolbar.navigationIcon?.colorFilter = PorterDuffColorFilter(
                    resolveThemeColor(R.attr.portalAppBarIconColor), PorterDuff.Mode.SRC_IN
                )

                navController.addOnDestinationChangedListener(object: OnDestinationChangedListener {
                    val destinationsWithSearch = setOf(
                        R.id.nav_home,
                        R.id.nav_route_edit,
                    )

                    override fun onDestinationChanged(
                        controller: NavController,
                        destination: NavDestination,
                        arguments: Bundle?
                    ) {
                        val menu = binding.appBarMain.toolbar.menu
                        menu.findItem(R.id.action_search)?.isVisible = destination.id in destinationsWithSearch
                    }
                })
            }
        }

        baiduMapViewModel.mGeoCoder = GeoCoder.newInstance()
        baiduMapViewModel.mGeoCoder?.setOnGetGeoCodeResultListener(object: OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult) {}

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult) {
                if (reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    Log.e("MainActivity", "Reverse GeoCode error: ${reverseGeoCodeResult.error}")
                } else with(baiduMapViewModel) {
                    markName = reverseGeoCodeResult.address.toString()

                    if (showDetailView) {
                        showDetailInfo(reverseGeoCodeResult.location.wgs84, reverseGeoCodeResult.location)
                    }
                }
            }
        })

        mockServiceViewModel.initRocker(this)
    }

    private fun initNotification() {
        with(baiduMapViewModel) {
            mNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationUtils = NotificationUtils(this@MainActivity)
                val builder = notificationUtils.getAndroidChannelNotification(
                    "Portal 后台定位服务",
                    "正在后台定位"
                )
                builder.build()
            } else {
                val builder = Notification.Builder(this@MainActivity)
                val nfIntent = Intent(this@MainActivity, MainActivity::class.java)
                builder.setContentIntent(
                    PendingIntent.getActivity(this@MainActivity, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE)
                )
                    .setContentTitle("Portal 后台定位服务")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentText("正在后台定位")
                    .setWhen(System.currentTimeMillis())
                    .build()
            }
            mNotification?.defaults = Notification.DEFAULT_SOUND
        }
    }

    private fun requireFloatWindows(): Boolean {
        fun requestSettingCanDrawOverlays() {
            kotlin.runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }.onFailure {
                Log.e("MainActivity", "requestSettingCanDrawOverlays: ", it)
                Toast.makeText(this, "Failed to open overlay settings", Toast.LENGTH_LONG).show()
            }
            finish()
        }

        if (!OverlayUtils.hasOverlayPermissions(this)) {
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            requestSettingCanDrawOverlays()
            return false
        }

        return true
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            val denied = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (denied.isEmpty()) {
                mockServiceViewModel.locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                return
            }

            for (permission in denied) {
                val message = when (permission) {
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION -> "Portal needs location permission"
                    ACCESS_LOCATION_EXTRA_COMMANDS -> "Portal needs extra location command permission"
                    CHANGE_WIFI_STATE, ACCESS_WIFI_STATE -> "Portal needs Wi-Fi state access"
                    READ_PHONE_STATE -> "Portal needs device info permission"
                    ACCESS_NETWORK_STATE, INTERNET -> "Portal needs network access"
                    VIBRATE -> "Portal needs vibration permission"
                    else -> "Permission required: $permission"
                }
                Toast.makeText(this, "$message. Please grant it manually.", Toast.LENGTH_SHORT).show()
            }

            setContentView(R.layout.activity_no_permission)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val searchItem: MenuItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.onActionViewExpanded()

        val searchClose = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)
        val voiceBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_voice_btn)
        val searchText = searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
        val iconColor = ColorStateList.valueOf(resolveThemeColor(R.attr.portalAppBarIconColor))
        val textColor = resolveThemeColor(R.attr.portalTextColor)
        val hintColor = resolveThemeColor(R.attr.portalHintColor)
        ImageViewCompat.setImageTintList(searchClose, iconColor)
        ImageViewCompat.setImageTintList(searchBack, iconColor)
        ImageViewCompat.setImageTintList(voiceBack, iconColor)
        searchText.setTextColor(textColor)
        searchText.setHintTextColor(hintColor)

        val searchList = binding.appBarMain.searchListView
        searchList.onItemClickListener = OnItemClickListener { _, view, _, _ ->
            val lngText = (view.findViewById<View>(R.id.poi_longitude) as TextView).text.toString()
            val latText = (view.findViewById<View>(R.id.poi_latitude) as TextView).text.toString()
            val selectedPoi = Poi(
                name = (view.findViewById<View>(R.id.poi_name) as TextView).text.toString(),
                address = (view.findViewById<View>(R.id.poi_address) as TextView).text.toString(),
                longitude = lngText.toDouble(),
                latitude = latText.toDouble(),
                tag = (view.findViewById<View>(R.id.poi_tag) as TextView).text.toString()
            )
            with(baiduMapViewModel) {
                val destinationId = findNavController(R.id.nav_host_fragment_content_main).currentDestination?.id
                if (destinationId == R.id.nav_route_edit) {
                    onPoiSelected?.invoke(selectedPoi)
                } else {
                    markName = selectedPoi.name
                    markedLoc = selectedPoi.latitude to selectedPoi.longitude
                    if (isExists) {
                        val gcjLoc = markedLoc!!.gcj02
                        val location = LatLng(gcjLoc.latitude, gcjLoc.longitude)
                        baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(location))
                    } else {
                        Toast.makeText(this@MainActivity, "Map is not ready", Toast.LENGTH_SHORT).show()
                    }
                    markMap()
                }
                binding.appBarMain.searchLinear.visibility = View.INVISIBLE
                searchItem.collapseActionView()
            }
        }

        if (mSuggestionSearch == null) {
            mSuggestionSearch = SuggestionSearch.newInstance()
            mSuggestionSearch?.setOnGetSuggestionResultListener { suggestionResult ->
                val data = suggestionResult?.toPoi(baiduMapViewModel.currentLocation)?.map { it.toMap() }.orEmpty()
                if (data.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No matching location found", Toast.LENGTH_SHORT).show()
                    binding.appBarMain.searchLinear.visibility = View.GONE
                    searchList.adapter = null
                } else {
                    val simAdapt = SimpleAdapter(
                        this@MainActivity,
                        data,
                        R.layout.layout_search_poi_item,
                        arrayOf(Poi.KEY_NAME, Poi.KEY_ADDRESS, Poi.KEY_LONGITUDE_RAW, Poi.KEY_LATITUDE_RAW, Poi.KEY_TAG),
                        intArrayOf(R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude, R.id.poi_tag)
                    )
                    searchList.adapter = simAdapt
                    binding.appBarMain.searchLinear.visibility = View.VISIBLE
                }
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrBlank()) return false
                val keyword = query.trim()
                if (keyword.length < 2) {
                    binding.appBarMain.searchLinear.visibility = View.GONE
                    searchList.adapter = null
                    Toast.makeText(this@MainActivity, "Please enter at least 2 characters", Toast.LENGTH_SHORT).show()
                    return true
                }
                try {
                    suggestionSearchJob?.cancel()
                    mSuggestionSearch?.requestSuggestion(createSuggestionSearchOption(keyword))
                    baiduMapViewModel.baiduMap.clear()
                    binding.appBarMain.searchLinear.visibility = View.INVISIBLE
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Search failed", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Search error: ${e.stackTraceToString()}")
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val keyword = newText?.trim().orEmpty()
                suggestionSearchJob?.cancel()
                if (keyword.length < 2) {
                    binding.appBarMain.searchLinear.visibility = View.GONE
                    searchList.adapter = null
                } else {
                    suggestionSearchJob = lifecycleScope.launch {
                        delay(300)
                        try {
                            mSuggestionSearch?.requestSuggestion(createSuggestionSearchOption(keyword))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Search failed", Toast.LENGTH_SHORT).show()
                            Log.e("MainActivity", "Search error: ${e.stackTraceToString()}")
                        }
                    }
                }
                return true
            }
        })
        return true
    }

    private fun createSuggestionSearchOption(keyword: String): SuggestionSearchOption {
        return SuggestionSearchOption()
            .keyword(keyword.trim())
            .city(mCityString ?: "")
            .citylimit(false)
            .apply {
                baiduMapViewModel.currentLocation?.let {
                    location(it.gcj02)
                }
            }
    }

    private fun markMap() = with(baiduMapViewModel) {
        if (markedLoc == null) return

        if (perspectiveState == MyLocationConfiguration.LocationMode.FOLLOWING) {
            perspectiveState = MyLocationConfiguration.LocationMode.NORMAL
        }

        val gcjLoc = markedLoc!!.gcj02
        val ooA = MarkerOptions()
            .position(gcjLoc)
            .apply {
                if (mMapIndicator != null)
                    icon(mMapIndicator)
            }
        baiduMap.clear()
        baiduMap.addOverlay(ooA)

        showDetailInfo(markedLoc!!, gcjLoc)
    }

    @SuppressLint("SetTextI18n")
    private fun showDetailInfo(wgsLoc: Pair<Double, Double>, gcjLoc: LatLng) {
        val infoView = layoutInflater.inflate(R.layout.layout_loc_detail, null)
        val locDetail = infoView.findViewById<TextView>(R.id.loc_detail)
        locDetail.text = "${wgsLoc.second.toString().take(10)}, ${wgsLoc.first.toString().take(10)}"
        val locAddr = infoView.findViewById<TextView>(R.id.loc_addr)
        locAddr.text = baiduMapViewModel.markName ?: "Unknown address"
        val mInfoWindow = InfoWindow(BitmapDescriptorFactory.fromView(infoView), gcjLoc, -95, null)

        baiduMapViewModel.baiduMap.showInfoWindow(mInfoWindow)
    }

    
    private fun createNavigationColorStateList(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                resolveThemeColor(R.attr.portalActiveColor),
                resolveThemeColor(R.attr.portalNavIconColor)
            )
        )
    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()

        suggestionSearchJob?.cancel()
        mSuggestionSearch?.destroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 111

        internal var mCityString: String? = null
            set(value) {
                if (field != value)  {
                    field = value
                    Log.d("HomeViewModel", "cityString: $value")
                }
            }
    }
}



