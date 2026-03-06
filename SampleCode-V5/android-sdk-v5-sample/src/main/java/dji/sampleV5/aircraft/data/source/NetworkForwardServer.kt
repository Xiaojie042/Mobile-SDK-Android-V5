package dji.sampleV5.aircraft.data.source

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * 网络转发服务器
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class NetworkForwardServer(private val port: Int = 8080) {
    private val TAG = "NetworkForwardServer"
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val clients = mutableListOf<Socket>()
    private val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 启动网络服务器
     */
    fun start() {
        if (isRunning) return
        
        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.i(TAG, "Network server started on port $port")
                
                // 启动数据转发协程
                launch {
                    forwardDataToClients()
                }
                
                // 接受客户端连接
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        Log.i(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                        
                        clients.add(clientSocket)
                        
                        // 监控客户端连接状态
                        launch {
                            monitorClient(clientSocket)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start network server on port $port", e)
            }
        }
    }
    
    /**
     * 停止网络服务器
     */
    fun stop() {
        isRunning = false
        serverScope.cancel("Server stopped")
        
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket", e)
        }
        
        clients.forEach { client ->
            try {
                client.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
        clients.clear()
        
        Log.i(TAG, "Network server stopped")
    }
    
    /**
     * 发送数据到网络客户端
     */
    suspend fun sendData(data: ByteArray) {
        if (clients.isNotEmpty()) {
            dataChannel.send(data)
        }
    }
    
    /**
     * 获取连接客户端数量
     */
    fun getClientCount(): Int = clients.size
    
    /**
     * 获取服务器状态
     */
    fun isServerRunning(): Boolean = isRunning
    
    /**
     * 获取服务器端口
     */
    fun getPort(): Int = port
    
    /**
     * 设置服务器端口（重启生效）
     */
    fun setPort(newPort: Int) {
        if (isRunning) {
            Log.w(TAG, "Cannot change port while server is running")
            return
        }
        // 端口将在下次启动时生效
    }
    
    /**
     * 内部方法：转发数据到所有客户端
     */
    private suspend fun forwardDataToClients() {
        for (data in dataChannel) {
            val clientsToRemove = mutableListOf<Socket>()
            
            clients.forEach { client ->
                try {
                    client.getOutputStream().write(data)
                    client.getOutputStream().write("\n".toByteArray()) // 添加换行符分隔
                    client.getOutputStream().flush()
                    Log.d(TAG, "Data forwarded to client: ${data.size} bytes")
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending data to client", e)
                    clientsToRemove.add(client)
                }
            }
            
            // 移除断开连接的客户端
            clientsToRemove.forEach { client ->
                clients.remove(client)
                try {
                    client.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing disconnected client", e)
                }
            }
        }
    }
    
    /**
     * 内部方法：监控客户端连接状态
     */
    private suspend fun monitorClient(client: Socket) {
        try {
            val buffer = ByteArray(1024)
            while (isRunning) {
                val bytesRead = client.getInputStream().read(buffer)
                if (bytesRead == -1) {
                    break // 客户端断开连接
                }
                // 可以处理客户端发送的数据（如果需要双向通信）
            }
        } catch (e: IOException) {
            Log.d(TAG, "Client disconnected: ${client.inetAddress.hostAddress}")
        } finally {
            clients.remove(client)
            try {
                client.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }
}