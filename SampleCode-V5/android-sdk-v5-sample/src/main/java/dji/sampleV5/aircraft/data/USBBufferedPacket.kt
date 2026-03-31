package dji.sampleV5.aircraft.data

import android.util.Base64
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class USBBufferedPacket(
    val id: Long,
    val source: String,
    val payloadText: String,
    val payloadBase64: String,
    val byteLength: Int,
    val createdAtMillis: Long
) {
    fun createdAtText(): String {
        return DATE_FORMAT.format(Date(createdAtMillis))
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("source", source)
            put("payloadText", payloadText)
            put("payloadBase64", payloadBase64)
            put("byteLength", byteLength)
            put("createdAtMillis", createdAtMillis)
            put("createdAtText", createdAtText())
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        fun fromBytes(id: Long, source: String, bytes: ByteArray): USBBufferedPacket {
            return USBBufferedPacket(
                id = id,
                source = source,
                payloadText = bytes.toString(Charsets.UTF_8),
                payloadBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                byteLength = bytes.size,
                createdAtMillis = System.currentTimeMillis()
            )
        }
    }
}
