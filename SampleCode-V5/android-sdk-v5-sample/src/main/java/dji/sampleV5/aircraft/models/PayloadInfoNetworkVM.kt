package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dji.sampleV5.aircraft.data.DataFormatMode
import dji.sampleV5.aircraft.data.DataSendMode
import dji.sampleV5.aircraft.data.DJIToastResult
import dji.sampleV5.aircraft.data.NetworkConnectionState
import dji.sampleV5.aircraft.data.SendProgress
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.payload.PayloadCenter
import dji.v5.manager.aircraft.payload.PayloadIndexType
import dji.v5.manager.aircraft.payload.data.PayloadBasicInfo
import dji.v5.manager.aircraft.payload.listener.PayloadBasicInfoListener
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener
import dji.v5.utils.common.LogUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Payload设备信息与网络发送ViewModel
 * 
 * @author Payload开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class PayloadInfoNetworkVM : ViewModel() {
    
    // Payload基本信息
    val payloadBasicInfo = MutableLiveData<PayloadBasicInfo>()
    
    // 网络连接状态
    private val _networkConnectionState = MutableLiveData<NetworkConnectionState>(NetworkConnectionState.DISCONNECTED)
    val networkConnectionState = _networkConnectionState
    
    // 数据发送进度
    private val _sendProgress = MutableLiveData<SendProgress>(SendProgress.IDLE)
    val sendProgress = _sendProgress
    
    // 操作日志
    private val _logMessages = MutableLiveData<StringBuilder>(StringBuilder())
    val logMessages = _logMessages
    
    // 当前Payload索引类型
    private var payloadIndexType: PayloadIndexType = PayloadIndexType.UNKNOWN
    
    // Payload管理器
    private val payloadManagerMap = PayloadCenter.getInstance().payloadManager
    
    // Payload基本信息监听器
    private val payloadBasicInfoListener: PayloadBasicInfoListener = PayloadBasicInfoListener { info ->
        payloadBasicInfo.postValue(info)
        addLog("Payload基本信息更新: ${info.payloadProductName}")
        
        // 如果启用了自动发送模式，自动发送PSDK数据
        if (_dataSendMode.value == DataSendMode.AUTO_ONLY || _dataSendMode.value == DataSendMode.BOTH) {
            sendCurrentPayloadInfo()
        }
    }
    
    // 数据发送模式
    private val _dataSendMode = MutableLiveData<DataSendMode>(DataSendMode.MANUAL_ONLY)
    val dataSendMode = _dataSendMode
    
    // 数据格式模式
    private val _dataFormatMode = MutableLiveData<DataFormatMode>(DataFormatMode.RAW_DATA_ONLY)
    val dataFormatMode = _dataFormatMode
    
    // PSDK接收到的数据
    private val _psdkReceivedData = MutableLiveData<StringBuilder>(StringBuilder())
    val psdkReceivedData = _psdkReceivedData
    
    // PSDK数据监听器
    private val payloadDataListener: PayloadDataListener = PayloadDataListener { data ->
        val timestamp = getTimeNow()
        val dataString = if (data.isNotEmpty()) {
            String(data)
        } else {
            "空数据"
        }
        
        val logEntry = "[$timestamp] PSDK接收: $dataString\n"
        
        val currentData = _psdkReceivedData.value ?: StringBuilder()
        currentData.append(logEntry)
        
        // 限制数据长度，避免内存问题
        if (currentData.length > 10000) {
            currentData.delete(0, currentData.length - 5000)
        }
        
        _psdkReceivedData.postValue(currentData)
        addLog("PSDK数据接收: ${data.size} 字节")
        
        // 如果启用了自动发送模式，自动转发PSDK数据到网络
        if (_dataSendMode.value == DataSendMode.AUTO_ONLY || _dataSendMode.value == DataSendMode.BOTH) {
            sendPSDKDataToNetwork(dataString)
        }
    }
    
    // 网络客户端（基于USBSendDataVM的网络转发模式）
    private lateinit var networkClient: NetworkClient
    
    // 服务器配置
    private var serverHost: String = "192.168.3.31"
    private var serverPort: Int = 8888
    
    init {
        addLog("Payload信息网络发送模块初始化")
        networkClient = NetworkClient()
    }
    
    /**
     * 初始化Payload监听器
     */
    fun initPayloadListener(payloadIndexType: PayloadIndexType) {
        this.payloadIndexType = payloadIndexType
        val iPayloadManager = payloadManagerMap[payloadIndexType]
        iPayloadManager?.addPayloadBasicInfoListener(payloadBasicInfoListener)
        iPayloadManager?.addPayloadDataListener(payloadDataListener)
        addLog("Payload监听器初始化: ${payloadIndexType.name}")
    }
    
    /**
     * 设置数据格式模式
     */
    fun setDataFormatMode(mode: DataFormatMode) {
        _dataFormatMode.value = mode
        addLog("数据格式模式设置为: $mode")
    }
    
    /**
     * 发送PSDK接收到的数据到网络
     */
    private fun sendPSDKDataToNetwork(psdkData: String) {
        if (_networkConnectionState.value != NetworkConnectionState.CONNECTED) {
            addLog("❌ 网络未连接，无法转发PSDK数据")
            return
        }
        
        viewModelScope.launch {
            try {
                addLog("开始转发PSDK数据到网络")
                
                // 根据数据格式模式决定发送的数据格式
                val sendData = when (_dataFormatMode.value) {
                    DataFormatMode.RAW_DATA_ONLY -> psdkData
                    DataFormatMode.PSDK_FORMAT -> {
                        val payloadInfo = payloadBasicInfo.value
                        if (payloadInfo != null) {
                            constructPSDKDataFormat(payloadInfo, psdkData)
                        } else {
                            psdkData
                        }
                    }
                    DataFormatMode.MIXED_FORMAT -> {
                        val payloadInfo = payloadBasicInfo.value
                        if (payloadInfo != null) {
                            constructMixedDataFormat(payloadInfo, psdkData)
                        } else {
                            psdkData
                        }
                    }
                    else -> psdkData
                }
                
                // 实际发送数据
                val sendResult = networkClient.sendData(sendData.toByteArray())
                
                if (sendResult.isSuccess) {
                    addLog("✅ PSDK数据转发成功: ${sendData.length} 字节")
                } else {
                    addLog("❌ PSDK数据转发失败: ${sendResult.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                addLog("❌ PSDK数据转发异常: ${e.message}")
            }
        }
    }
    
    /**
     * 发送数据到PSDK
     */
    fun sendDataToPSDK(data: String) {
        if (data.isEmpty()) {
            addLog("⚠️ 发送到PSDK的数据为空")
            return
        }
        
        val byteArray = data.toByteArray()
        
        payloadManagerMap[payloadIndexType]?.sendDataToPayload(byteArray, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                addLog("✅ 数据发送到PSDK成功: ${byteArray.size} 字节")
            }
            
            override fun onFailure(error: IDJIError) {
                addLog("❌ 数据发送到PSDK失败: ${error.toString()}")
            }
        })
    }
    
    /**
     * 设置服务器地址
     */
    fun setServerAddress(host: String, port: Int) {
        serverHost = host
        serverPort = port
        networkClient.setServerAddress(host, port)
        addLog("服务器地址设置: $host:$port")
    }
    
    /**
     * 连接到服务器
     */
    fun connectToServer() {
        viewModelScope.launch {
            try {
                _networkConnectionState.value = NetworkConnectionState.CONNECTING
                addLog("正在连接到服务器: $serverHost:$serverPort")
                
                val result = networkClient.connect()
                if (result.isSuccess) {
                    _networkConnectionState.value = NetworkConnectionState.CONNECTED
                    addLog("✅ 服务器连接成功")
                } else {
                    _networkConnectionState.value = NetworkConnectionState.ERROR
                    addLog("❌ 服务器连接失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _networkConnectionState.value = NetworkConnectionState.ERROR
                addLog("❌ 连接异常: ${e.message}")
            }
        }
    }
    
    /**
     * 断开与服务器的连接
     */
    fun disconnectFromServer() {
        viewModelScope.launch {
            try {
                networkClient.disconnect()
                _networkConnectionState.value = NetworkConnectionState.DISCONNECTED
                addLog("🔌 服务器连接已断开")
            } catch (e: Exception) {
                addLog("❌ 断开连接异常: ${e.message}")
            }
        }
    }
    
    /**
     * 设置数据发送模式
     */
    fun setDataSendMode(mode: DataSendMode) {
        _dataSendMode.value = mode
        addLog("数据发送模式设置为: $mode")
    }
    
    /**
     * 发送手动输入的数据到服务器
     */
    fun sendManualData(data: String) {
        if (data.isEmpty()) {
            addLog("⚠️ 发送数据为空")
            return
        }
        
        if (_networkConnectionState.value != NetworkConnectionState.CONNECTED) {
            addLog("❌ 网络未连接，无法发送数据")
            return
        }
        
        viewModelScope.launch {
            try {
                _sendProgress.value = SendProgress.STARTED
                addLog("开始发送手动数据: ${data.length} 字节")
                
                // 根据发送模式和格式模式决定数据格式
                val sendData = when (_dataSendMode.value) {
                    DataSendMode.MANUAL_ONLY -> {
                        // 仅发送手动数据
                        when (_dataFormatMode.value) {
                            DataFormatMode.RAW_DATA_ONLY -> data
                            DataFormatMode.PSDK_FORMAT -> constructManualData(data)
                            DataFormatMode.MIXED_FORMAT -> constructManualData(data)
                            else -> data
                        }
                    }
                    DataSendMode.AUTO_ONLY -> {
                        // 仅发送PSDK数据（不应该调用此方法）
                        addLog("⚠️ 当前为自动模式，手动发送被忽略")
                        return@launch
                    }
                    DataSendMode.BOTH -> {
                        // 同时发送手动数据和PSDK数据
                        when (_dataFormatMode.value) {
                            DataFormatMode.RAW_DATA_ONLY -> data
                            DataFormatMode.PSDK_FORMAT -> {
                                val payloadInfo = payloadBasicInfo.value
                                if (payloadInfo != null) {
                                    constructPayloadInfoData(payloadInfo)
                                } else {
                                    ""
                                }
                            }
                            DataFormatMode.MIXED_FORMAT -> constructCombinedData(data)
                            else -> data
                        }
                    }
                    else -> data
                }
                
                // 模拟发送进度
                for (i in 0..100 step 10) {
                    _sendProgress.value = SendProgress.PROGRESS(i, 100)
                    kotlinx.coroutines.delay(50)
                }
                
                // 实际发送数据
                val sendResult = networkClient.sendData(sendData.toByteArray())
                
                if (sendResult.isSuccess) {
                    _sendProgress.value = SendProgress.COMPLETED(sendData.length)
                    addLog("✅ 手动数据发送成功: ${sendData.length} 字节")
                } else {
                    _sendProgress.value = SendProgress.ERROR(sendResult.exceptionOrNull()?.message)
                    addLog("❌ 手动数据发送失败: ${sendResult.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                _sendProgress.value = SendProgress.ERROR(e.message)
                addLog("❌ 手动数据发送异常: ${e.message}")
            }
        }
    }
    
    /**
     * 发送当前Payload信息到服务器（自动模式）
     */
    fun sendCurrentPayloadInfo() {
        val payloadInfo = payloadBasicInfo.value
        if (payloadInfo == null) {
            addLog("❌ 当前没有Payload信息")
            return
        }
        
        if (_networkConnectionState.value != NetworkConnectionState.CONNECTED) {
            addLog("❌ 网络未连接，无法发送PSDK数据")
            return
        }
        
        viewModelScope.launch {
            try {
                _sendProgress.value = SendProgress.STARTED
                addLog("开始发送PSDK数据")
                
                val payloadInfoJson = constructPayloadInfoData(payloadInfo)
                
                // 模拟发送进度
                for (i in 0..100 step 10) {
                    _sendProgress.value = SendProgress.PROGRESS(i, 100)
                    kotlinx.coroutines.delay(50)
                }
                
                // 实际发送数据
                val sendResult = networkClient.sendData(payloadInfoJson.toByteArray())
                
                if (sendResult.isSuccess) {
                    _sendProgress.value = SendProgress.COMPLETED(payloadInfoJson.length)
                    addLog("✅ PSDK数据发送成功: ${payloadInfoJson.length} 字节")
                } else {
                    _sendProgress.value = SendProgress.ERROR(sendResult.exceptionOrNull()?.message)
                    addLog("❌ PSDK数据发送失败: ${sendResult.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                _sendProgress.value = SendProgress.ERROR(e.message)
                addLog("❌ PSDK数据发送异常: ${e.message}")
            }
        }
    }
    
    /**
     * 构造手动数据
     */
    private fun constructManualData(userData: String): String {
        return """
        {
            "type": "manual_data",
            "timestamp": "${getTimeNow()}",
            "data": "$userData"
        }
        """.trimIndent()
    }
    
    /**
     * 构造PSDK信息数据
     */
    private fun constructPayloadInfoData(payloadInfo: PayloadBasicInfo): String {
        return """
        {
            "type": "payload_info",
            "timestamp": "${getTimeNow()}",
            "payloadName": "${payloadInfo.payloadProductName}",
            "payloadType": "${payloadInfo.payloadType}",
            "serialNumber": "${payloadInfo.serialNumber}",
            "firmwareVersion": "${payloadInfo.firmwareVersion}",
            "isConnected": ${payloadInfo.isConnected},
            "uploadBandwidth": ${payloadInfo.uploadBandwidth},
            "isFeatureOpened": ${payloadInfo.isFeatureOpened}
        }
        """.trimIndent()
    }
    
    /**
     * 构造组合数据
     */
    private fun constructCombinedData(userData: String): String {
        val payloadInfo = payloadBasicInfo.value
        return if (payloadInfo != null) {
            """
            {
                "type": "combined_data",
                "timestamp": "${getTimeNow()}",
                "payloadInfo": {
                    "payloadName": "${payloadInfo.payloadProductName}",
                    "payloadType": "${payloadInfo.payloadType}",
                    "serialNumber": "${payloadInfo.serialNumber}",
                    "firmwareVersion": "${payloadInfo.firmwareVersion}",
                    "isConnected": ${payloadInfo.isConnected}
                },
                "manualData": "$userData"
            }
            """.trimIndent()
        } else {
            constructManualData(userData)
        }
    }
    
    /**
     * 构造PSDK数据格式（PSDK信息 + 接收到的数据）
     */
    private fun constructPSDKDataFormat(payloadInfo: PayloadBasicInfo, psdkData: String): String {
        return """
        {
            "type": "psdk_data",
            "timestamp": "${getTimeNow()}",
            "payloadInfo": {
                "payloadName": "${payloadInfo.payloadProductName}",
                "payloadType": "${payloadInfo.payloadType}",
                "serialNumber": "${payloadInfo.serialNumber}",
                "firmwareVersion": "${payloadInfo.firmwareVersion}",
                "isConnected": ${payloadInfo.isConnected}
            },
            "receivedData": "$psdkData"
        }
        """.trimIndent()
    }
    
    /**
     * 构造混合数据格式（PSDK信息 + 接收到的数据 + 手动数据）
     */
    private fun constructMixedDataFormat(payloadInfo: PayloadBasicInfo, psdkData: String): String {
        return """
        {
            "type": "mixed_data",
            "timestamp": "${getTimeNow()}",
            "payloadInfo": {
                "payloadName": "${payloadInfo.payloadProductName}",
                "payloadType": "${payloadInfo.payloadType}",
                "serialNumber": "${payloadInfo.serialNumber}",
                "firmwareVersion": "${payloadInfo.firmwareVersion}",
                "isConnected": ${payloadInfo.isConnected}
            },
            "receivedData": "$psdkData"
        }
        """.trimIndent()
    }
    
    /**
     * 添加日志消息
     */
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message\n"
        
        val currentLog = _logMessages.value ?: StringBuilder()
        currentLog.append(logEntry)
        
        // 限制日志长度，避免内存问题
        if (currentLog.length > 10000) {
            currentLog.delete(0, currentLog.length - 5000)
        }
        
        _logMessages.postValue(currentLog)
        LogUtils.i("PayloadInfoNetworkVM", message)
    }
    
    /**
     * 获取当前时间
     */
    private fun getTimeNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    /**
     * 获取当前服务器地址
     */
    fun getCurrentServerHost(): String = serverHost
    
    /**
     * 获取当前服务器端口
     */
    fun getCurrentServerPort(): Int = serverPort
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        payloadManagerMap[payloadIndexType]?.removePayloadBasicInfoListener(payloadBasicInfoListener)
        payloadManagerMap[payloadIndexType]?.removePayloadDataListener(payloadDataListener)
        networkClient.disconnect()
        addLog("Payload信息网络发送模块已清理")
    }
}

/**
 * 网络客户端类（基于USBSendDataVM的网络转发模式）
 */
class NetworkClient {
    private var host: String = "192.168.3.31"
    private var port: Int = 8888
    private var socket: java.net.Socket? = null
    private var connected: Boolean = false
    
    fun setServerAddress(host: String, port: Int) {
        this.host = host
        this.port = port
    }
    
    suspend fun connect(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            socket = java.net.Socket(host, port)
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }
    
    suspend fun sendData(data: ByteArray): Result<Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (!connected || socket == null) {
                Result.failure(Exception("网络未连接"))
            } else {
                val outputStream = socket!!.getOutputStream()
                outputStream.write(data)
                outputStream.write('\n'.code) // 添加换行符作为数据包分隔符
                outputStream.flush()
                
                Result.success(data.size)
            }
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }
    
    fun disconnect() {
        try {
            socket?.close()
            connected = false
        } catch (e: Exception) {
            // 忽略断开连接时的异常
        }
    }
    
    fun isConnected(): Boolean = connected
}