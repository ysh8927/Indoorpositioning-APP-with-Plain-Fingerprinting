package com.example.indoorpositioning

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var floorPlanView: FloorPlanView
    private lateinit var spinnerMap: Spinner
    private lateinit var btnLocate: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvNoMap: TextView
    private lateinit var tvLoading: TextView
    private lateinit var loadingOverlay: FrameLayout

    // WiFi
    private lateinit var wifiManager: WifiManager
    private var wifiScanReceiver: BroadcastReceiver? = null

    // 데이터
    private var mapList: List<MapInfo> = emptyList()
    private var selectedMap: MapInfo? = null

    // 권한 요청
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            loadMaps()
        } else {
            showStatus("위치 권한이 필요합니다.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupWifi()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        floorPlanView  = findViewById(R.id.floorPlanView)
        spinnerMap     = findViewById(R.id.spinnerMap)
        btnLocate      = findViewById(R.id.btnLocate)
        tvStatus       = findViewById(R.id.tvStatus)
        tvLocation     = findViewById(R.id.tvLocation)
        tvNoMap        = findViewById(R.id.tvNoMap)
        tvLoading      = findViewById(R.id.tvLoading)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        btnLocate.setOnClickListener { startLocate() }

        spinnerMap.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos > 0 && pos <= mapList.size) {
                    selectMap(mapList[pos - 1])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupWifi() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun checkPermissionsAndLoad() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) {
            loadMaps()
        } else {
            permissionLauncher.launch(denied.toTypedArray())
        }
    }

    // =========================================================================
    // 맵 로드
    // =========================================================================

    private fun loadMaps() {
        showLoading("라디오맵 불러오는 중...")
        lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(60_000) {
                    RetrofitClient.api.getMaps()
                }

                if (result == null) {
                    hideLoading()
                    showStatus("서버 응답 시간 초과\n(15분 미사용 시 서버가 잠듭니다. 잠시 후 다시 시도해주세요)")
                    return@launch
                }

                if (result.isSuccessful) {
                    mapList = result.body()?.maps ?: emptyList()
                    hideLoading()
                    setupSpinner()
                } else {
                    hideLoading()
                    showStatus("맵 로드 실패: ${result.code()}")
                }
            } catch (e: Exception) {
                hideLoading()
                showStatus("서버 연결 실패\n인터넷 연결을 확인해주세요")
            }
        }
    }

    private fun setupSpinner() {
        val items = mutableListOf("맵 선택...")
        items.addAll(mapList.map { it.name })
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMap.adapter = adapter

        if (mapList.isEmpty()) {
            showStatus("저장된 라디오맵이 없습니다.")
        } else {
            showStatus("맵을 선택해주세요 (${mapList.size}개)")
        }
    }

    private fun selectMap(map: MapInfo) {
        selectedMap = map
        floorPlanView.setMapInfo(map)
        floorPlanView.clearLocation()
        tvLocation.visibility = View.GONE
        tvNoMap.visibility = View.GONE
        btnLocate.isEnabled = true
        showStatus("${map.name} 선택됨")

        // 도면 이미지 로드
        if (!map.floor_image_url.isNullOrEmpty()) {
            Glide.with(this)
                .load(map.floor_image_url)
                .into(floorPlanView)
        } else {
            floorPlanView.setImageResource(android.R.color.darker_gray)
            showStatus("${map.name} (도면 이미지 없음)")
        }
    }

    // =========================================================================
    // 위치 추정
    // =========================================================================

    private fun startLocate() {
        val map = selectedMap ?: return
        btnLocate.isEnabled = false
        showStatus("WiFi 스캔 중...")
        scanWifi { rssiVector ->
            if (rssiVector.isEmpty()) {
                showStatus("WiFi 신호를 감지하지 못했습니다.")
                btnLocate.isEnabled = true
                return@scanWifi
            }
            requestLocation(map, rssiVector)
        }
    }

    private fun scanWifi(callback: (Map<String, Int>) -> Unit) {
        val rssiMap = mutableMapOf<String, Int>()

        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unregisterReceiver(this)
                wifiScanReceiver = null

                val results = wifiManager.scanResults
                for (result in results) {
                    if (result.BSSID != null) {
                        rssiMap[result.BSSID.uppercase()] = result.level
                    }
                }
                callback(rssiMap)
            }
        }

        registerReceiver(
            wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        @Suppress("DEPRECATION")
        val started = wifiManager.startScan()
        if (!started) {
            unregisterReceiver(wifiScanReceiver)
            wifiScanReceiver = null
            // 캐시된 결과 사용
            val results = wifiManager.scanResults
            for (result in results) {
                if (result.BSSID != null) {
                    rssiMap[result.BSSID.uppercase()] = result.level
                }
            }
            callback(rssiMap)
        }
    }

    private fun requestLocation(map: MapInfo, rssiVector: Map<String, Int>) {
        showLoading("위치 계산 중...")
        lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(30_000) {
                    RetrofitClient.api.locate(
                        LocateRequest(
                            map_id = map.id,
                            rssi_vector = rssiVector
                        )
                    )
                }

                hideLoading()
                btnLocate.isEnabled = true

                if (result == null) {
                    showStatus("위치 추정 시간 초과")
                    return@launch
                }

                if (result.isSuccessful) {
                    val loc = result.body()!!
                    floorPlanView.setLocation(loc.x, loc.y)
                    tvLocation.text = "현재 위치: (%.2f, %.2f)m  |  매칭 AP: %d개".format(
                        loc.x, loc.y, loc.matched_aps
                    )
                    tvLocation.visibility = View.VISIBLE
                    showStatus("위치 추정 완료")
                } else {
                    showStatus("위치 추정 실패: ${result.code()}")
                }

            } catch (e: Exception) {
                hideLoading()
                btnLocate.isEnabled = true
                showStatus("오류: ${e.message}")
            }
        }
    }

    // =========================================================================
    // UI 헬퍼
    // =========================================================================

    private fun showStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun showLoading(msg: String) {
        tvLoading.text = msg
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
    }
}
