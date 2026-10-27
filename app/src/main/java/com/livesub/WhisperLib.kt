package com.livesub

/** Whisper يعمل على الجهاز عبر whisper.cpp (JNI) */
object WhisperLib {
    init { System.loadLibrary("livesub_whisper") }

    @JvmStatic external fun init(modelPath: String): Long
    @JvmStatic external fun transcribe(ctx: Long, audio: FloatArray, threads: Int): ByteArray
    @JvmStatic external fun free(ctx: Long)

    /** pcm: 16kHz mono PCM16 (little-endian). يعيد (لغة، نص) أو null إن لم يوجد كلام مفيد */
    fun run(ctx: Long, pcm: ByteArray): Pair<String, String>? {
        val n = pcm.size / 2
        val a = FloatArray(maxOf(n, 17600)) // Whisper يحتاج أكثر من ثانية؛ نُكمل بصمت
        for (i in 0 until n) {
            val v = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
            a[i] = v.toShort() / 32768f
        }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val r = String(transcribe(ctx, a, threads), Charsets.UTF_8)
        val k = r.indexOf('|'); if (k < 0) return null
        val t = r.substring(k + 1).trim()
        val noise = t.length < 2 ||
            (t.startsWith("[") && t.endsWith("]")) || (t.startsWith("(") && t.endsWith(")")) || t.startsWith("♪")
        return if (noise) null else r.substring(0, k) to t
    }
}
