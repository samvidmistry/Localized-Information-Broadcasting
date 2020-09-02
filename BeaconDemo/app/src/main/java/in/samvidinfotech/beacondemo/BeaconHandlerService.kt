package `in`.samvidinfotech.beacondemo

import `in`.samvidinfotech.beacondemo.events.*
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.orm.query.Condition
import com.orm.query.Select
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.IOException
import java.util.*

class BeaconHandlerService : Service() {
    private val TAG = BeaconHandlerService::class.java.canonicalName
    private val PERIODIC_SEARCH_NOTIFICATION_CHANNEL = "periodic-search"
    private val offlineDataMask = 0x4000000000000000
    private val silentZoneMask = 0x2000000000000000
    private val scanTime = 10000
    private val scanInterval = 5000
    private var notificationIdCounter = 0
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var locationManager: LocationManager
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    private val devices = mutableListOf<BluetoothDevice>()
    private val calls = mutableListOf<FetchRequest>()
    private val pendingNotificationPersistance = mutableListOf<NotificationData>()
    private val client = OkHttpClient()
    private val handler = Handler()
    private var isScanRunning = false
    private var scanId: Long = 0
    private var silencedByUs = false
    private var silencedScanId: Long = 0
    private val stopScanRunnable = {
        bluetoothLeScanner.stopScan(scanCallback)
        Log.d(TAG, "Stopped scanning")
        devices.clear()
        EventBus.getDefault().post(ScanFinishedEvent())
        isScanRunning = false
    }
    private val startScanRunnable = { startScan() }
    private val scanCallback = object: ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null || result.device == null || devices.contains(result.device)) return

            Log.d(TAG, "Found device " + (result.device.name))
            devices.add(result.device)

            val scanRecord = result.scanRecord?.bytes ?: return

            var startByte = 2
            var patternFound = false
            while (startByte <= 5) {
                if (scanRecord[startByte + 2].toInt() and 0xff == 0x02 && // identifies an iBeacon
                    scanRecord[startByte + 3].toInt() and 0xff == 0x15
                ) {
                    // identifies correct data length
                    patternFound = true
                    break
                }
                startByte++
            }

            if (patternFound) {
                val uuidBytes = ByteArray(16) { 0 }
                System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16)
                val uuid: UUID = ConversionUtils.bytesToUuid(uuidBytes)
                val major = ConversionUtils.byteArrayToInteger(Arrays.copyOfRange(scanRecord, startByte + 20,
                                startByte + 22))
                val minor = ConversionUtils.byteArrayToInteger(Arrays.copyOfRange(scanRecord, startByte + 22,
                                startByte + 24))

                Log.d(TAG, "UUID for device " + result.device.name + " is " + uuid)
                val bitString = ConversionUtils.bitStringFromBeaconData(
                    uuid.mostSignificantBits, uuid.leastSignificantBits, major, minor)

                processBitString(bitString, result.device?.name ?: "Around you")
            }
        }
    }

    /**
     * @param bitString bit string of full 160 bits
     */
    private fun processBitString(bitString: String, deviceName: String) {
        processFirstFiveBits(bitString)
        if(bitString[0] == '1') { //if MSB of UUID is 1, it means data is on the server
            fetchDataAndNotify(bitString.substring(5))
        } else {
            notifyUsingLocalData(bitString, deviceName)
        }
    }

    /**
     * Processes the first 5 bits of the BLE UUID. The meaning associated with every bit is as specified below:
     * 0th bit = 0 means the data is offline and is present in the UUID itself, 1 means the data is at server and must
     *           be fetched
     * 1st bit = Silent Zone feature. 0 means we don't want to switch the phone to silent mode, 1 means we want to.
     */
    private fun processFirstFiveBits(bitString: String) {
        if (bitString[1] == '1') {
            if (!silencedByUs and isAlreadySilent()) {
                return
            }
            silencedByUs = true
            silencedScanId = scanId
            switchPhoneToVibrateMode()
        }
    }

    /**
     * Fetches the notification data from the server. Does nothing if the parameter doesn't satisfy the condition.
     * @param bitString A bit string of 155 bits specifying the BLE ID
     */
    private fun fetchDataAndNotify(bitString: String) {
        if (bitString.length != 155) {
            Log.e(TAG, "bitString has length ${bitString.length}")
            return
        }

        val formBody = FormBody.Builder().add("ble_id", bitString).build()
        val request = Request.Builder().url(NOTIFICATION_DATA_REQUEST_URL)
            .post(formBody).build()

        val call = client.newCall(request)
        calls.add(FetchRequest(bitString, call))
        val callback = object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body().use {
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }

                    val message = it?.string() ?: return
                    calls.removeAll(calls.filter { i -> i.call == call })
                    try {
                        val json = JSONObject(message)

                        if (json.has("error")) {
                            Log.e(TAG, json.optString("error"))
                            return
                        }

                        val notification = NotificationData(json.optString("title"),
                            json.optString("content"),
                            json.optString("link"))
                        sendNotification(notification)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

        }
        call.enqueue(callback)
    }

    private fun sendNotification(notification: NotificationData) {
        val builder = NotificationCompat.Builder(this@BeaconHandlerService, PERIODIC_SEARCH_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(notification.title)
            .setContentText(notification.content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(this@BeaconHandlerService).notify(notificationIdCounter++,
            builder.build())
        if (ContextCompat.checkSelfPermission(
                this@BeaconHandlerService,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            EventBus.getDefault().post(ToastEvent("Location permission not provided"))
            return
        }

        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (lastKnownLocation != null && lastKnownLocation.time > Calendar.getInstance().timeInMillis
            - 2 * 60 * 1000
        ) {
            saveLocation(notification, lastKnownLocation)
            EventBus.getDefault().post(LocationHistoryChangedEvent())
        } else {
            val locationListenerImplementation = object : LocationListener {
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

                }

                override fun onProviderEnabled(provider: String?) {

                }

                override fun onProviderDisabled(provider: String?) {

                }

                override fun onLocationChanged(location: Location?) {
                    if (location == null) return

                    updateLocation(pendingNotificationPersistance.first(), location)
                    EventBus.getDefault().post(LocationHistoryChangedEvent())
                    EventBus.getDefault().post(ToastEvent("Changed coordinates of last location"))
                    locationManager.removeUpdates(this)
                }

            }
            pendingNotificationPersistance.add(notification)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f,
                locationListenerImplementation)
            saveLocation(notification)
            EventBus.getDefault().post(LocationHistoryChangedEvent())
        }
    }

    private fun updateLocation(notificationData: NotificationData, location: Location) {
        val locationData = Select.from(LocationData::class.java)
            .where(
                Condition.prop("title").eq(notificationData.title),
                Condition.prop("content").eq(notificationData.content),
                Condition.prop("link").eq(notificationData.link)
            )
            .list()
            .maxBy { it.id } ?: return

        locationData.latitude = location.latitude
        locationData.longitude = location.longitude
        locationData.save()
    }

    private fun saveLocation(
        notification: NotificationData,
        lastKnownLocation: Location
    ) {
        val locationData = LocationData(
            notification.title ?: "No title", notification.content, notification.link,
            lastKnownLocation.latitude, lastKnownLocation.longitude
        )
        locationData.save()
    }

    private fun saveLocation(notification: NotificationData) {
        val locationData = LocationData(
            notification.title ?: "No title", notification.content, notification.link,
            null, null)
        locationData.save()
    }

    private fun notifyUsingLocalData(bitString: String, deviceName: String) {
        val numberOfCharacters = ConversionUtils.numberOfCharactersFromBeaconData(bitString)
        val beaconData = ConversionUtils.bitsToString(bitString.substring(10), numberOfCharacters) ?: return

        sendNotification(NotificationData("Message from device $deviceName", beaconData, null))
    }

    /**
     * Switches the phone to vibrate mode. Called when the device has silent zone bit as 1. For more info,
     * look at the documentation of [processFirstFiveBits].
     */
    private fun switchPhoneToVibrateMode() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    private fun switchPhoneToRingerMode() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun isAlreadySilent(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = bluetoothManager.adapter
        bluetoothLeScanner = adapter.bluetoothLeScanner
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startScan()
        return START_STICKY
    }

    private fun startScan() {
        if (isScanRunning) {
            stopScan(handler, 0, false, -1)
        }
        scanId++
        bluetoothLeScanner.startScan(mutableListOf<ScanFilter>(), scanSettings, scanCallback)
        Log.d(TAG, "Started scanning")
        EventBus.getDefault().post(ScanStartedEvent())
        isScanRunning = true
        stopScan(handler, scanTime.toLong(), true, scanInterval.toLong())
    }

    private fun stopScan(handler: Handler, scanTime: Long, reschedule: Boolean, rescanInterval: Long) {
        if (!isScanRunning) return

        if (silencedByUs and (silencedScanId != scanId)) {
            switchPhoneToRingerMode()
            silencedByUs = false
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(stopScanRunnable, scanTime)
        if (reschedule) {
            handler.removeCallbacks(startScanRunnable)
            handler.postDelayed(startScanRunnable, scanTime + rescanInterval)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onStartScanEvent(startScanEvent: StartScanEvent) {
        startScan()
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onStopScanEvent(stopScanEvent: StopScanEvent) {
        stopScan(handler, 0, stopScanEvent.reschedule, scanInterval.toLong())
    }

    @Subscribe
    fun onFakeOfflineMessageEvent(fakeOfflineMessageEvent: FakeOfflineMessageEvent) {
        processBitString(fakeOfflineMessageEvent.messageBits, fakeOfflineMessageEvent.deviceName)
    }

    @Subscribe
    fun onFakeOnlineMessageEvent(fakeOnlineMessageEvent: FakeOnlineMessageEvent) {
        processBitString(fakeOnlineMessageEvent.bitStringWithBleId, "")
    }

    override fun onCreate() {
        super.onCreate()
        EventBus.getDefault().register(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PERIODIC_SEARCH_NOTIFICATION_CHANNEL,
                "Search channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel = notificationManager
                .getNotificationChannel(PERIODIC_SEARCH_NOTIFICATION_CHANNEL)
            if (notificationChannel != null) return
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        pendingNotificationPersistance.forEach { saveLocation(it) }
        super.onDestroy()
    }

    companion object {
        var NOTIFICATION_DATA_REQUEST_URL = "http://192.168.43.184/localized_information_broadcasting" +
                "/access_with_ble_id.php"
            set(value) {
                field = String.format("http://%s/localized_information_broadcasting" +
                        "/access_with_ble_id.php", value)
            }

        fun getIntent(context: Context): Intent {
            return Intent(context, BeaconHandlerService::class.java)
        }
    }

    data class FetchRequest(val bleId: String, val call: Call)

    data class NotificationData(val title: String?, val content: String, val link: String?)
}
