package dji.sampleV5.aircraft.data.source

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import dji.sampleV5.aircraft.data.USBProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

class USBDataRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "USBDataRepository"
    }
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // 网络客户端 - 备用方案
    private var networkClient: NetworkClient = NetworkClient("192.168.3.31", 8888)
    private var currentServerHost = "192.168.3.31"
    private var currentServerPort = 8888
    
    private val dataBuffer = ConcurrentLinkedQueue<ByteArray>()
    
    private var isConnected = false
    
    // 获取缓冲区大小
    fun getBufferSize(): Int = dataBuffer.size
    
    // 添加数据到缓冲区
    fun addDataToBuffer(data: ByteArray): Boolean {
        return try {
            dataBuffer.offer(data)
            Log.d(TAG, "数据已添加到缓冲区，当前缓冲区大小: ${dataBuffer.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "添加数据到缓冲区失败: ${e.message}")
            false
        }
    }
    
    // 获取数据并从缓冲区移除
    fun getDataFromBuffer(): ByteArray? {
        return dataBuffer.poll()
    }
    
    // 查看数据但不移除
    fun peekDataFromBuffer(): ByteArray? {
        return dataBuffer.peek()
    }
    
    // 清空缓冲区
    fun clearBuffer() {
        dataBuffer.clear()
        Log.d(TAG, "缓冲区已清空")
    }
    
    // 检查USB连接状态
    fun checkUSBConnection(): Boolean {
        val accessoryList = usbManager.accessoryList
        isConnected = accessoryList != null && accessoryList.isNotEmpty()
        Log.d(TAG, "USB连接状态: $isConnected, Accessory数量: ${accessoryList?.size ?: 0}")
        return isConnected
    }
    
    // 获取USB连接状态文本
    fun getConnectionStatusText(): String {
        return if (checkUSBConnection()) {
            "USB已连接 - 等待PC轮询"
        } else {
            "USB未连接 - 请通过USB连接PC"
        }
    }
    
    // 发送数据到缓冲区（供ViewModel调用）
    suspend fun sendData(data: String, protocol: USBProtocol = USBProtocol.BULK): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            val added = addDataToBuffer(dataBytes)
            
            if (added) {
                Log.d(TAG, "数据已添加到缓冲区: ${dataBytes.size} 字节")
                Result.success(dataBytes.size)
            } else {
                Result.failure(Exception("数据添加到缓冲区失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送数据失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取USB连接状态
    fun isUSBConnected(): Boolean = isConnected
    
    // 断开连接
    fun disconnect() {
        isConnected = false
        clearBuffer()
        Log.d(TAG, "已断开USB连接")
    }
    
    // 连接到网络服务器（备用方案）
    fun connectToServer() {
        try {
            networkClient.connect()
            Log.d(TAG, "已连接到网络服务器")
        } catch (e: Exception) {
            Log.e(TAG, "连接网络服务器失败: ${e.message}")
        }
    }
    
    // 断开网络服务器连接（备用方案）
    fun disconnectFromServer() {
        try {
            networkClient.disconnect()
            Log.d(TAG, "已断开网络服务器连接")
        } catch (e: Exception) {
            Log.e(TAG, "断开网络服务器连接失败: ${e.message}")
        }
    }
    
    // 获取网络连接状态（备用方案）
    fun getNetworkConnectionStatus(): String {
        return if (networkClient.isConnected()) {
            "已连接 - 服务器: ${networkClient.getServerInfo()}"
        } else {
            "未连接 - 服务器: ${networkClient.getServerInfo()}"
        }
    }
    
    // 设置服务器地址（备用方案）
    fun setServerAddress(host: String, port: Int) {
        currentServerHost = host
        currentServerPort = port
        networkClient.setServer(host, port)
        Log.d(TAG, "已设置服务器地址: $host:$port")
    }
    
    // 获取当前服务器主机
    fun getCurrentServerHost(): String = currentServerHost
    
    // 获取当前服务器端口
    fun getCurrentServerPort(): Int = currentServerPort
}