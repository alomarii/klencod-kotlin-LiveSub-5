package com.livesub

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** تحويل الكلام لنص عبر Whisper API (كشف اللغة تلقائياً) */
object WhisperClient {

    fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
        h.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }

    fun transcribe(pcm: ByteArray, cfg: Cfg): String {
        val b = "----ls${System.currentTimeMillis()}"
        val out = ByteArrayOutputStream()
        fun field(n: String, v: String) =
            out.write("--$b\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())
        field("model", cfg.model); field("response_format", "json"); field("temperature", "0")
        out.write("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
        out.write(wav(pcm)); out.write("\r\n--$b--\r\n".toByteArray())

        val c = URL(cfg.endpoint).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 10000; c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            c.outputStream.use { it.write(out.toByteArray()) }
            val body = (if (c.responseCode == 200) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return JSONObject(body).optString("text").trim()
        } finally { c.disconnect() }
    }
}
