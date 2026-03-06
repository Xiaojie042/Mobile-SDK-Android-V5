package dji.sampleV5.aircraft.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dji.sampleV5.aircraft.data.USBProtocol
import dji.sampleV5.aircraft.data.USBConnectionState
import dji.sampleV5.aircraft.data.source.USBDataRepository
import kotlinx.coroutines.launch

/**
 * USB数据发送ViewModel - 简化版
 * 安卓设备作为USB从设备，不需要扫描和连接设备
 * 数据存入缓冲区，由PC端主动轮询读取
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class USBSendDataVM : ViewModel() {
    
    private lateinit var usbRepository: USBDataRepository
    
    // USB连接状态
    private val _usbConnectionState = MutableLiveData<USBConnectionState>(USBConnectionState.DISCONNECTED)
    val usbConnectionState: LiveData<USBConnectionState> = _usbConnectionState
    
    // 网络连接状态（备用方案）
    private val _networkConnectionStatus = MutableLiveData<String>("未连接")
    val networkConnectionStatus: LiveData<String> = _networkConnectionStatus
    
    // 缓冲区大小
    private val _bufferSize = MutableLiveData<Int>(0)
    val bufferSize: LiveData<Int> = _bufferSize
    
    // 发送历史记录
    private val _sendHistory = MutableLiveData<List<String>>(emptyList())
    val sendHistory: LiveData<List<String>> = _sendHistory
    
    // 发送历史记录列表
    private val historyList = mutableListOf<String>()
    
    // 初始化USB数据仓库
    fun initRepository(repository: USBDataRepository) {
        usbRepository = repository
        checkUSBConnection()
    }
    
    // 检查USB连接状态
    fun checkUSBConnection() {
        if (::usbRepository.isInitialized) {
            val isConnected = usbRepository.checkUSBConnection()
            _usbConnectionState.value = if (isConnected) {
                USBConnectionState.CONNECTED
            } else {
                USBConnectionState.DISCONNECTED
            }
            updateBufferSize()
        }
    }
    
    // 获取USB连接状态文本
    fun getUSBConnectionStatus(): String {
        return if (::usbRepository.isInitialized) {
            usbRepository.getConnectionStatusText()
        } else {
            "USB数据仓库未初始化"
        }
    }
    
    // 发送数据 - 将数据添加到缓冲区，等待PC轮询
    fun sendData(data: String, protocol: USBProtocol = USBProtocol.BULK) {
        viewModelScope.launch {
            try {
                if (!::usbRepository.isInitialized) {
                    Log.e("USBSendDataVM", "USB数据仓库未初始化")
                    return@launch
                }
                
                // 发送数据到USB数据仓库（添加到缓冲区）
                val result = usbRepository.sendData(data, protocol)
                
                if (result.isSuccess) {
                    val bytesSent = result.getOrNull() ?: 0
                    
                    // 添加到发送历史记录
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    val historyEntry = "[$timestamp] $data (${bytesSent}字节)"
                    
                    historyList.add(0, historyEntry) // 添加到开头
                    if (historyList.size > 10) { // 限制历史记录数量
                        historyList.removeAt(historyList.size - 1)
                    }
                    
                    _sendHistory.value = historyList.toList()
                    
                    Log.d("USBSendDataVM", "数据已添加到缓冲区: $bytesSent 字节")
                    
                    // 更新缓冲区大小
                    updateBufferSize()
                } else {
                    val exception = result.exceptionOrNull()
                    Log.e("USBSendDataVM", "数据添加到缓冲区失败: ${exception?.message}", exception)
                }
            } catch (e: Exception) {
                Log.e("USBSendDataVM", "数据发送异常: ${e.message}", e)
            }
        }
    }
    
    // 连接到网络服务器（备用方案）
    fun connectToServer() {
        if (::usbRepository.isInitialized) {
            try {
                usbRepository.connectToServer()
                _networkConnectionStatus.value = usbRepository.getNetworkConnectionStatus()
            } catch (e: Exception) {
                Log.e("USBSendDataVM", "连接服务器失败: ${e.message}")
            }
        }
    }
    
    // 断开网络服务器连接（备用方案）
    fun disconnectFromServer() {
        if (::usbRepository.isInitialized) {
            try {
                usbRepository.disconnectFromServer()
                _networkConnectionStatus.value = usbRepository.getNetworkConnectionStatus()
            } catch (e: Exception) {
                Log.e("USBSendDataVM", "断开服务器连接失败: ${e.message}")
            }
        }
    }
    
    // 设置服务器地址（备用方案）
    fun setServerAddress(host: String, port: Int) {
        if (::usbRepository.isInitialized) {
            usbRepository.setServerAddress(host, port)
        }
    }
    
    // 获取当前服务器地址（备用方案）
    fun getCurrentServerHost(): String {
        return if (::usbRepository.isInitialized) {
            usbRepository.getCurrentServerHost()
        } else {
            "未知"
        }
    }
    
    // 获取当前服务器端口（备用方案）
    fun getCurrentServerPort(): Int {
        return if (::usbRepository.isInitialized) {
            usbRepository.getCurrentServerPort()
        } else {
            0
        }
    }
    
    // 断开USB连接
    fun disconnect() {
        if (::usbRepository.isInitialized) {
            usbRepository.disconnect()
            _usbConnectionState.value = USBConnectionState.DISCONNECTED
            updateBufferSize()
        }
    }
    
    // 更新缓冲区大小
    private fun updateBufferSize() {
        if (::usbRepository.isInitialized) {
            _bufferSize.value = usbRepository.getBufferSize()
        }
    }
    
    // 清空发送历史记录
    fun clearHistory() {
        historyList.clear()
        _sendHistory.value = emptyList()
    }
    
    // 检查USB是否连接
    fun isUSBConnected(): Boolean {
        return if (::usbRepository.isInitialized) {
            usbRepository.isUSBConnected()
        } else {
            false
        }
    }
}
