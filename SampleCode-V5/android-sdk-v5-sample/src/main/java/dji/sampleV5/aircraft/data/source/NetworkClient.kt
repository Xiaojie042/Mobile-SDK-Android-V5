package dji.sampleV5.aircraft.data.source

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

/**
 * 网络客户端 - 连接到PC服务器
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class NetworkClient(private val serverHost: String = "192.168.3.31", private val serverPort: Int = 8888) {
    private val TAG = "NetworkClient"
    
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 连接到PC服务器
     */
    fun connect() {
        if (isConnected) return
        
        clientScope.launch {
            try {
                Log.i(TAG, "正在连接到PC服务器: $serverHost:$serverPort")
                socket = Socket(serverHost, serverPort)
                outputStream = socket!!.getOutputStream()
                isConnected = true
                
                Log.i(TAG, "成功连接到PC服务器")
                
                // 启动数据发送协程
                launch {
                    sendDataToServer()
                }
                
                // 监控连接状态
                launch {
                    monitorConnection()
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "连接PC服务器失败: ${e.message}")
                isConnected = false
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        isConnected = false
        
        // 只取消当前连接相关的协程，不取消整个scope
        clientScope.coroutineContext.cancelChildren()
        
        try {
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭输出流错误", e)
        }
        
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭Socket错误", e)
        }
        
        outputStream = null
        socket = null
        
        Log.i(TAG, "已断开与PC服务器的连接")
    }
    
    /**
     * 发送数据到PC服务器
     */
    suspend fun sendData(data: ByteArray) {
        if (isConnected) {
            dataChannel.send(data)
        }
    }
    
    /**
     * 获取连接状态
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * 获取服务器信息
     */
    fun getServerInfo(): String = "$serverHost:$serverPort"
    
    /**
     * 设置服务器地址
     */
    fun setServer(host: String, port: Int) {
        if (isConnected) {
            disconnect()
        }
        // 新的服务器地址将在下次连接时生效
    }
    
    /**
     * 内部方法：发送数据到服务器
     */
    private suspend fun sendDataToServer() {
        for (data in dataChannel) {
            try {
                outputStream?.let { stream ->
                    stream.write(data)
                    stream.write("\n".toByteArray()) // 添加换行符分隔
                    stream.flush()
                    Log.d(TAG, "数据发送到PC服务器: ${data.size} 字节")
                }
            } catch (e: IOException) {
                Log.e(TAG, "发送数据到PC服务器失败", e)
                disconnect()
                break
            }
        }
    }
    
    /**
     * 内部方法：监控连接状态
     */
    private suspend fun monitorConnection() {
        try {
            val buffer = ByteArray(1024)
            while (isConnected) {
                val bytesRead = socket!!.getInputStream().read(buffer)
                if (bytesRead == -1) {
                    break // 服务器断开连接
                }
                // 可以处理服务器发送的数据（如果需要双向通信）
            }
        } catch (e: IOException) {
            Log.d(TAG, "PC服务器断开连接")
        } finally {
            isConnected = false
            Log.i(TAG, "连接监控结束")
        }
    }
}