package dji.sampleV5.aircraft.data.source

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import dji.sampleV5.aircraft.data.USBBufferedPacket
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class USBDataRepository(context: Context) {

    companion object {
        private const val TAG = "USBDataRepository"
        private const val DEFAULT_PORT = 18080
        private const val MAX_BUFFER_SIZE = 200

        private val packetIdGenerator = AtomicLong(1L)
        private val packetBuffer = LinkedBlockingQueue<USBBufferedPacket>()
        private val serverLock = Any()

        @Volatile
        private var serverSocket: ServerSocket? = null

        @Volatile
        private var acceptThread: Thread? = null

        @Volatile
        private var pollingPort: Int = DEFAULT_PORT
    }

    private val appContext = context.applicationContext
    private val usbManager: UsbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    fun startPollingServer(port: Int = DEFAULT_PORT): Result<Int> {
        synchronized(serverLock) {
            if (serverSocket?.isClosed == false) {
                pollingPort = serverSocket?.localPort ?: pollingPort
                return Result.success(pollingPort)
            }

            return try {
                val socket = ServerSocket(port)
                serverSocket = socket
                pollingPort = socket.localPort
                acceptThread = Thread {
                    acceptLoop(socket)
                }.apply {
                    name = "usb-polling-server"
                    isDaemon = true
                    start()
                }
                Log.i(TAG, "Polling server started on port $pollingPort")
                Result.success(pollingPort)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start polling server", error)
                Result.failure(error)
            }
        }
    }

    fun stopPollingServer() {
        val socketToClose: ServerSocket?
        val threadToStop: Thread?
        synchronized(serverLock) {
            socketToClose = serverSocket
            threadToStop = acceptThread
            serverSocket = null
            acceptThread = null
        }

        try {
            socketToClose?.close()
        } catch (error: Exception) {
            Log.w(TAG, "Failed to close polling server", error)
        }

        if (threadToStop != null && threadToStop !== Thread.currentThread()) {
            try {
                threadToStop.interrupt()
                threadToStop.join(500)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while waiting for polling server thread to stop", error)
            }
        }
    }

    fun isPollingServerRunning(): Boolean {
        return serverSocket?.isClosed == false
    }

    fun getPollingPort(): Int = pollingPort

    fun getBufferSize(): Int = packetBuffer.size

    fun addTestData(data: String): Result<USBBufferedPacket> {
        return addPacket("TEST", data.toByteArray(Charsets.UTF_8))
    }

    fun addPsdkData(data: ByteArray): Result<USBBufferedPacket> {
        return addPacket("PSDK", data)
    }

    fun clearBuffer() {
        packetBuffer.clear()
        Log.i(TAG, "Polling buffer cleared")
    }

    fun checkUSBConnection(): Boolean {
        val accessoryList = usbManager.accessoryList
        return accessoryList != null && accessoryList.isNotEmpty()
    }

    fun getConnectionStatusText(): String {
        val accessoryConnected = checkUSBConnection()
        val serverStatus = if (isPollingServerRunning()) "轮询服务已启动" else "轮询服务未启动"
        val usbStatus = if (accessoryConnected) {
            "检测到 Android Accessory 连接"
        } else {
            "未检测到 Accessory 直连，当前方案使用 USB 网络轮询"
        }
        return "$serverStatus，$usbStatus"
    }

    fun getPollingHintText(): String {
        val addresses = getLocalIpv4Addresses()
        val addressText = if (addresses.isEmpty()) {
            "请在遥控器开启 USB 网络共享后查看 IP"
        } else {
            addresses.joinToString(" / ")
        }
        return buildString {
            append("PC 轮询地址: GET http://<遥控器IP>:")
            append(getPollingPort())
            append("/poll?timeoutMs=1000\n")
            append("状态查询: GET http://<遥控器IP>:")
            append(getPollingPort())
            append("/status\n")
            append("当前可见 IPv4: ")
            append(addressText)
        }
    }

    fun getRecentPackets(limit: Int = 10): List<USBBufferedPacket> {
        if (limit <= 0) {
            return emptyList()
        }
        return packetBuffer.toList().takeLast(limit).reversed()
    }

    private fun addPacket(source: String, bytes: ByteArray): Result<USBBufferedPacket> {
        return try {
            while (packetBuffer.size >= MAX_BUFFER_SIZE) {
                packetBuffer.poll()
            }
            val packet = USBBufferedPacket.fromBytes(packetIdGenerator.getAndIncrement(), source, bytes)
            packetBuffer.offer(packet)
            Log.i(TAG, "Buffered packet source=$source size=${bytes.size} queue=${packetBuffer.size}")
            Result.success(packet)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to buffer packet", error)
            Result.failure(error)
        }
    }

    private fun pollPacket(timeoutMs: Long): USBBufferedPacket? {
        return if (timeoutMs > 0) {
            packetBuffer.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            packetBuffer.poll()
        }
    }

    private fun peekPacket(): USBBufferedPacket? = packetBuffer.peek()

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                val client = socket.accept()
                Thread({
                    handleClient(client)
                }, "usb-polling-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (error: Exception) {
                if (!socket.isClosed) {
                    Log.e(TAG, "Accept failed", error)
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Skip headers.
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeJson(socket, 400, errorResponse("Invalid request line"))
                    return
                }

                val method = parts[0].uppercase(Locale.US)
                val requestTarget = parts[1]
                if (method != "GET") {
                    writeJson(socket, 405, errorResponse("Only GET is supported"))
                    return
                }

                val uri = URI(requestTarget)
                val timeoutMs = uri.getQueryParam("timeoutMs")?.toLongOrNull()?.coerceIn(0L, 5000L) ?: 0L
                val response = when (uri.path ?: "/") {
                    "/poll" -> pollResponse(timeoutMs)
                    "/peek" -> packetResponse(true, peekPacket())
                    "/status" -> statusResponse()
                    "/history" -> historyResponse(uri.getQueryParam("limit")?.toIntOrNull() ?: 10)
                    else -> errorResponse("Unknown path: ${uri.path}")
                }
                val statusCode = if (response.optBoolean("success", false)) 200 else 404
                writeJson(socket, statusCode, response)
            } catch (error: Exception) {
                Log.e(TAG, "Client handling failed", error)
                writeJson(socket, 500, errorResponse(error.message ?: "Internal error"))
            }
        }
    }

    private fun pollResponse(timeoutMs: Long): JSONObject {
        val packet = pollPacket(timeoutMs)
        return packetResponse(true, packet)
    }

    private fun packetResponse(success: Boolean, packet: USBBufferedPacket?): JSONObject {
        return JSONObject().apply {
            put("success", success)
            put("hasData", packet != null)
            put("queueSize", packetBuffer.size)
            put("packet", packet?.toJson() ?: JSONObject.NULL)
        }
    }

    private fun statusResponse(): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("queueSize", packetBuffer.size)
            put("pollingPort", getPollingPort())
            put("pollingServerRunning", isPollingServerRunning())
            put("usbAccessoryConnected", checkUSBConnection())
            put("ipv4Addresses", JSONArray(getLocalIpv4Addresses()))
            put("message", getConnectionStatusText())
        }
    }

    private fun historyResponse(limit: Int): JSONObject {
        val packets = getRecentPackets(limit.coerceIn(1, 50))
        return JSONObject().apply {
            put("success", true)
            put("queueSize", packetBuffer.size)
            put("packets", JSONArray().apply {
                packets.forEach { put(it.toJson()) }
            })
        }
    }

    private fun errorResponse(message: String): JSONObject {
        return JSONObject().apply {
            put("success", false)
            put("message", message)
        }
    }

    private fun writeJson(socket: Socket, statusCode: Int, body: JSONObject) {
        try {
            val payload = body.toString()
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 $statusCode ${statusText(statusCode)}\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Cache-Control: no-store\r\n")
            writer.write("Connection: close\r\n")
            writer.write("Content-Length: ${payload.toByteArray(Charsets.UTF_8).size}\r\n")
            writer.write("\r\n")
            writer.write(payload)
            writer.flush()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to write response", error)
        }
    }

    private fun statusText(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Internal Server Error"
        }
    }

    private fun getLocalIpv4Addresses(): List<String> {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses)
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress }
                        .map { "${networkInterface.name}:${it.hostAddress}" }
                }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to enumerate IPv4 addresses", error)
            emptyList()
        }
    }

    private fun URI.getQueryParam(key: String): String? {
        val query = rawQuery ?: return null
        return query.split("&")
            .mapNotNull { item ->
                val split = item.split("=", limit = 2)
                if (split.size == 2) split[0] to split[1] else null
            }
            .firstOrNull { it.first == key }
            ?.second
    }
}
