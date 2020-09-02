package `in`.samvidinfotech.beacondemo

import `in`.samvidinfotech.beacondemo.events.*
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.transition.TransitionManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.canonicalName
    private lateinit var root: ViewGroup
    private lateinit var startStopButton: Button
    private lateinit var fakeOfflineNotificationButton: Button
    private lateinit var fakeOnlineNotificationButton: Button
    private lateinit var setLocalIpButton: Button
    private lateinit var fakeMessageInput: EditText
    private lateinit var localIpInput: EditText
    private lateinit var silentZoneCheckBox: CheckBox
    private lateinit var emptyView: TextView
    private lateinit var historyListView: ListView
    private lateinit var historyAdapter: HistoryAdapter
    private val locationHistoryList = mutableListOf<LocationData>()
    private var isHistoryShowing = false
    private val toggleViewList = mutableListOf<View>()
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startService(AlphabetLoaderService.createIntent(this, "alphabet"))

        root = findViewById(R.id.root)
        startStopButton = findViewById(R.id.startstop)
        fakeOfflineNotificationButton = findViewById(R.id.fake_offline_notification)
        fakeOnlineNotificationButton = findViewById(R.id.fake_online_notification)
        fakeMessageInput = findViewById(R.id.fake_data_edittext)
        setLocalIpButton = findViewById(R.id.set_local_ip)
        localIpInput = findViewById(R.id.local_ip_edittext)
        silentZoneCheckBox = findViewById(R.id.silent_zone_checkbox)
        historyListView = findViewById(R.id.history_list)
        emptyView = findViewById(R.id.empty_view)
        locationHistoryList.addAll(LocationData.listAll(LocationData::class.java).reversed())
        historyAdapter = HistoryAdapter(this@MainActivity, locationHistoryList)
        historyListView.adapter = historyAdapter
        toggleViewList.addAll(listOf(fakeOfflineNotificationButton, fakeOnlineNotificationButton,
            setLocalIpButton, fakeMessageInput, localIpInput, silentZoneCheckBox))
        startStopButton.setOnClickListener {
            isScanning = if (!isScanning) {
                EventBus.getDefault().post(StartScanEvent())
                true
            } else {
                EventBus.getDefault().post(StopScanEvent(false))
                false
            }
        }

        fakeOfflineNotificationButton.setOnClickListener {
            val message = fakeMessageInput.text.toString()
            if (TextUtils.isEmpty(message)) {
                fakeMessageInput.error = "Please specify content string for offline content."
                return@setOnClickListener
            }

            if (message.length > 30) {
                fakeMessageInput.error = "Offline content size cannot be greater than 30"
                return@setOnClickListener
            }

            val offlineMessageBitString = ConversionUtils.messageToOfflineBits(message.toLowerCase())
            if (offlineMessageBitString == null) {
                Log.e(TAG, "Problem creating offline content, possibly alphabet is empty")
                return@setOnClickListener
            }

            val bitString = ConversionUtils.createFeatureListBits(true, silentZoneCheckBox.isChecked) +
                    offlineMessageBitString

            EventBus.getDefault().post(FakeOfflineMessageEvent(bitString, "Fake Device"))
        }

        fakeOnlineNotificationButton.setOnClickListener {
            val bleId = fakeMessageInput.text.toString()
            if (!bleId.matches(Regex("[0-9]+"))) {
                fakeMessageInput.error = "Please specify a BLE ID as Integer number."
                return@setOnClickListener
            }

            try {
                val bleIdBitString = ConversionUtils.createFeatureListBits(false,
                    silentZoneCheckBox.isChecked) + ConversionUtils.bleIdToBitString(bleId)
                EventBus.getDefault().post(FakeOnlineMessageEvent(bleIdBitString))
            } catch (e: Throwable) {
                fakeMessageInput.error = "Number out of range or parsing problem."
            }
        }

        setLocalIpButton.setOnClickListener {
            val localIp = localIpInput.text.toString()
            if (!localIp.matches(Regex("(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]" +
                        "{1,2})|(2[0-4][0-9])|(25[0-5]))"))) {
                localIpInput.error = "Please enter a valid IP address"
                return@setOnClickListener
            }

            BeaconHandlerService.NOTIFICATION_DATA_REQUEST_URL = localIp
        }

        isHistoryShowing = true
        toggleHistoryVisible(toggleViewList)
    }

    private fun toggleHistoryVisible(toggleViewList: List<View>) {
        TransitionManager.beginDelayedTransition(root)
        toggleViewList.forEach { it.visibility = View.GONE }
        handleEmptyView()
    }

    private fun handleEmptyView() {
        if (!isHistoryShowing) return

        if (historyAdapter.count > 0) {
            emptyView.visibility = View.GONE
            historyListView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.VISIBLE
            historyListView.visibility = View.GONE
        }
    }

    private fun toggleHistoryGone(toggleViewList: List<View>) {
        TransitionManager.beginDelayedTransition(root)
        toggleViewList.forEach { it.visibility = View.VISIBLE }
        historyListView.visibility = View.GONE
        emptyView.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu == null) return super.onPrepareOptionsMenu(menu)

        if (isHistoryShowing) {
            menu.findItem(R.id.show_settings).setVisible(true)
            menu.findItem(R.id.show_history).setVisible(false)
        } else {
            menu.findItem(R.id.show_history).setVisible(true)
            menu.findItem(R.id.show_settings).setVisible(false)
        }

        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        super.onOptionsItemSelected(item)
        if (item == null) return false

        when {
            item.itemId == R.id.show_history -> {
                isHistoryShowing = true
                toggleHistoryVisible(toggleViewList)
                invalidateOptionsMenu()
                return true
            }
            item.itemId == R.id.show_settings -> {
                isHistoryShowing = false
                toggleHistoryGone(toggleViewList)
                invalidateOptionsMenu()
                return true
            }
            item.itemId == R.id.clear_history -> {
                LocationData.deleteAll(LocationData::class.java)
                historyAdapter.clear()
                historyAdapter.notifyDataSetChanged()
                handleEmptyView()
                return true
            }
            else -> return false
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onAlphabetReady(event: AlphabetReadyEvent) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        var shouldLaunchService = true
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_DENIED ||
            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Please provide location permission from settings", Toast.LENGTH_LONG)
                .show()
            shouldLaunchService = false
        }
        if (!bluetoothManager.adapter.isEnabled) {
            Toast.makeText(this, "Please enable bluetooth and restart app", Toast.LENGTH_LONG).show()
            shouldLaunchService = false
        }

        if (isRunningOnEmulator()) {
            shouldLaunchService = true
        }

        if (!shouldLaunchService) {
            ActivityCompat.finishAfterTransition(this)
            return
        }
        startService(BeaconHandlerService.getIntent(this))
        Log.d(TAG, "Started BeaconHandlerService")
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onScanStartedEvent(scanStartedEvent: ScanStartedEvent) {
        startStopButton.text = getString(R.string.stop_scan)
        isScanning = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onScanFinishedEvent(scanFinishedEvent: ScanFinishedEvent) {
        startStopButton.text = getString(R.string.start_scan)
        isScanning = false
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onLocationHistoryChangedEvent(locationHistoryChangedEvent: LocationHistoryChangedEvent) {
        TransitionManager.beginDelayedTransition(root)
        historyAdapter.clear()
        historyAdapter.addAll(LocationData.listAll(LocationData::class.java).reversed())
        historyAdapter.notifyDataSetChanged()
        handleEmptyView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onToastEvent(toastEvent: ToastEvent) {
        Toast.makeText(this@MainActivity, toastEvent.message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Got this code from:
     * https://stackoverflow.com/a/21505193/5042104
     */
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
}
