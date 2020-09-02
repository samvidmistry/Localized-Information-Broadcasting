package `in`.samvidinfotech.beacondemo.events

class AlphabetReadyEvent

class ScanStartedEvent

class ScanFinishedEvent

class StartScanEvent

data class StopScanEvent(val reschedule: Boolean)

data class FakeOnlineMessageEvent(val bitStringWithBleId: String)

data class FakeOfflineMessageEvent(val messageBits: String, val deviceName: String)

data class ToastEvent(val message: String)

class LocationHistoryChangedEvent