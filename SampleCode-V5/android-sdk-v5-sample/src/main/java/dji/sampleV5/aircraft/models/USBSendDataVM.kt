package dji.sampleV5.aircraft.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dji.sampleV5.aircraft.data.USBBufferedPacket
import dji.sampleV5.aircraft.data.USBConnectionState
import dji.sampleV5.aircraft.data.source.USBDataRepository
import dji.v5.manager.aircraft.payload.PayloadCenter
import dji.v5.manager.aircraft.payload.PayloadIndexType
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener
import kotlinx.coroutines.launch
import java.util.Locale

class USBSendDataVM : ViewModel() {

    companion object {
        private const val TAG = "USBSendDataVM"
        private const val DEFAULT_PORT = 18080
    }

    private lateinit var usbRepository: USBDataRepository
    private val payloadManagerMap = PayloadCenter.getInstance().payloadManager
    private val registeredPayloadIndices = linkedSetOf<PayloadIndexType>()
    private val historyList = mutableListOf<String>()

    private val _usbConnectionState = MutableLiveData(USBConnectionState.DISCONNECTED)
    val usbConnectionState: LiveData<USBConnectionState> = _usbConnectionState

    private val _bufferSize = MutableLiveData(0)
    val bufferSize: LiveData<Int> = _bufferSize

    private val _sendHistory = MutableLiveData<List<String>>(emptyList())
    val sendHistory: LiveData<List<String>> = _sendHistory

    private val _pollingHint = MutableLiveData("")
    val pollingHint: LiveData<String> = _pollingHint

    private val _psdkListening = MutableLiveData(false)
    val psdkListening: LiveData<Boolean> = _psdkListening

    private val _latestPacketPreview = MutableLiveData("暂无缓存数据")
    val latestPacketPreview: LiveData<String> = _latestPacketPreview

    private val payloadDataListener = PayloadDataListener { data ->
        if (!::usbRepository.isInitialized) {
            return@PayloadDataListener
        }
        val result = usbRepository.addPsdkData(data)
        if (result.isSuccess) {
            val packet = result.getOrNull() ?: return@PayloadDataListener
            appendHistory(packet, "PSDK 数据进入缓冲区")
            updateBufferState()
        } else {
            Log.e(TAG, "Failed to buffer PSDK data", result.exceptionOrNull())
        }
    }

    fun initRepository(repository: USBDataRepository) {
        usbRepository = repository
        val serverResult = usbRepository.startPollingServer(DEFAULT_PORT)
        if (serverResult.isFailure) {
            Log.e(TAG, "Failed to start polling server", serverResult.exceptionOrNull())
            _usbConnectionState.value = USBConnectionState.ERROR
        }
        refreshStatus()
    }

    fun checkUSBConnection() {
        refreshStatus()
    }

    fun getUSBConnectionStatus(): String {
        return if (::usbRepository.isInitialized) {
            usbRepository.getConnectionStatusText()
        } else {
            "USB 缓冲仓库未初始化"
        }
    }

    fun addTestData(data: String) {
        if (data.isBlank() || !::usbRepository.isInitialized) {
            return
        }
        viewModelScope.launch {
            val result = usbRepository.addTestData(data)
            if (result.isSuccess) {
                val packet = result.getOrNull() ?: return@launch
                appendHistory(packet, "测试数据进入缓冲区")
                updateBufferState()
            } else {
                Log.e(TAG, "Failed to buffer test data", result.exceptionOrNull())
            }
        }
    }

    fun togglePsdkListening() {
        if (_psdkListening.value == true) {
            stopPsdkListening()
        } else {
            startPsdkListening()
        }
    }

    fun clearHistory() {
        historyList.clear()
        _sendHistory.value = emptyList()
        _latestPacketPreview.value = "暂无缓存数据"
        if (::usbRepository.isInitialized) {
            usbRepository.clearBuffer()
            updateBufferState()
        }
    }

    private fun startPsdkListening() {
        if (_psdkListening.value == true) {
            return
        }

        registeredPayloadIndices.clear()
        payloadManagerMap.forEach { (index, manager) ->
            if (manager != null) {
                manager.addPayloadDataListener(payloadDataListener)
                registeredPayloadIndices.add(index)
            }
        }

        val indicesText = if (registeredPayloadIndices.isEmpty()) {
            "未发现可监听的 Payload Manager"
        } else {
            registeredPayloadIndices.joinToString { it.name }
        }
        addSystemHistory("已开启 PSDK 监听: $indicesText")
        _psdkListening.value = registeredPayloadIndices.isNotEmpty()
    }

    private fun stopPsdkListening() {
        registeredPayloadIndices.forEach { index ->
            payloadManagerMap[index]?.removePayloadDataListener(payloadDataListener)
        }
        registeredPayloadIndices.clear()
        _psdkListening.value = false
        addSystemHistory("已停止 PSDK 监听")
    }

    private fun refreshStatus() {
        if (!::usbRepository.isInitialized) {
            return
        }
        _usbConnectionState.value = when {
            usbRepository.isPollingServerRunning() -> USBConnectionState.CONNECTED
            else -> USBConnectionState.DISCONNECTED
        }
        _pollingHint.value = usbRepository.getPollingHintText()
        updateBufferState()
    }

    private fun updateBufferState() {
        if (!::usbRepository.isInitialized) {
            return
        }
        _bufferSize.postValue(usbRepository.getBufferSize())
        val latestPacket = usbRepository.getRecentPackets(limit = 1).firstOrNull()
        _latestPacketPreview.postValue(latestPacket?.toPreviewText() ?: "暂无缓存数据")
    }

    private fun appendHistory(packet: USBBufferedPacket, prefix: String) {
        val historyEntry = "[${packet.createdAtText()}] $prefix: ${packet.source}, ${packet.byteLength} bytes, ${packet.payloadText.take(80)}"
        historyList.add(0, historyEntry)
        if (historyList.size > 20) {
            historyList.removeAt(historyList.lastIndex)
        }
        _sendHistory.postValue(historyList.toList())
        _latestPacketPreview.postValue(packet.toPreviewText())
    }

    private fun addSystemHistory(message: String) {
        val entry = "[SYSTEM] $message"
        historyList.add(0, entry)
        if (historyList.size > 20) {
            historyList.removeAt(historyList.lastIndex)
        }
        _sendHistory.value = historyList.toList()
    }

    private fun USBBufferedPacket.toPreviewText(): String {
        return String.format(
            Locale.US,
            "ID=%d | 来源=%s | 长度=%d bytes | 时间=%s\n文本=%s",
            id,
            source,
            byteLength,
            createdAtText(),
            payloadText.take(120)
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopPsdkListening()
    }
}
