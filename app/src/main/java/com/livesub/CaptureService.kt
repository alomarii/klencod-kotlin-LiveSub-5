package com.livesub

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

class CaptureService : Service() {
    private val RATE = 16000
    @Volatile private var running = false
    @Volatile private var localCtx = 0L
    private val ui = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private lateinit var cfg: Cfg
    private lateinit var wm: WindowManager
    private var box: LinearLayout? = null
    private lateinit var original: TextView
    private lateinit var translated: TextView
    private val translators = Translators()
    private val langId = LanguageIdentification.getClient()
    private val net = Executors.newSingleThreadExecutor() as ThreadPoolExecutor
    private val clearTask = Runnable { original.text = ""; translated.text = "" }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, f: Int, id: Int): Int {
        startForeground(1, notification())
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        cfg = Cfg(this)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(intent.getIntExtra("code", 0), data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, ui)
        showOverlay("جارِ التحضير…")
        running = true
        thread(name = "capture") { try { loop() } catch (e: Throwable) { say("خطأ: ${e.message}"); stopLater() } }
        return START_NOT_STICKY
    }

    private fun loop() {
        val vosk = cfg.engine == "vosk"
        var recognizer: Recognizer? = null
        if (vosk) {
            val dir = File(filesDir, "vosk-model")
            if (!dir.exists()) {
                if (assets.list("model").isNullOrEmpty()) { say("ضع نموذج Vosk في assets/model"); stopLater(); return }
                copyAssets("model", dir)
            }
            recognizer = Recognizer(Model(dir.absolutePath), RATE.toFloat())
        }
        if (cfg.engine == "local") {
            say("تجهيز نموذج Whisper…")
            val f = ensureModel() ?: run { stopLater(); return }
            localCtx = WhisperLib.init(f.absolutePath)
            if (localCtx == 0L) { say("فشل تحميل النموذج"); f.delete(); stopLater(); return }
        }
        val cfgCap = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val buf = ShortArray(1600) // 100ms
        record = AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(buf.size * 8)
            .setAudioPlaybackCaptureConfig(cfgCap).build().also { it.startRecording() }
        say("في انتظار الصوت…")

        // مقطّع الكلام (VAD بسيط يعتمد على طاقة الصوت)
        var seg = ByteArrayOutputStream(); var segN = 0; var silence = 0
        var inSpeech = false; var noise = 200.0; var prev: ShortArray? = null

        while (running) {
            val n = record!!.read(buf, 0, buf.size)
            if (n <= 0) continue
            if (vosk) {
                if (recognizer!!.acceptWaveForm(buf, n)) {
                    val t = JSONObject(recognizer.result).optString("text")
                    if (t.isNotBlank()) handle(t, cfg.source)
                } else {
                    val p = JSONObject(recognizer.partialResult).optString("partial")
                    if (p.isNotBlank()) ui.post { original.text = p }
                }
                continue
            }
            var sum = 0.0; for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
            val rms = sqrt(sum / n)
            val speech = rms > max(350.0, noise * 2.5)
            if (!speech) noise = noise * 0.95 + rms * 0.05
            if (speech && !inSpeech) { inSpeech = true; prev?.let { write(seg, it, it.size); segN += it.size } }
            if (inSpeech) {
                write(seg, buf, n); segN += n
                silence = if (speech) 0 else silence + n
                if (silence >= RATE * 6 / 10 || segN >= RATE * 10) { // صمت 0.6ث أو 10ث كحد أقصى
                    if (segN - silence >= RATE / 2) submit(seg.toByteArray())
                    seg = ByteArrayOutputStream(); segN = 0; silence = 0; inSpeech = false
                }
            }
            prev = buf.copyOf(n)
        }
        recognizer?.close()
    }

    private fun ensureModel(): File? {
        val name = "ggml-${cfg.localModel}-q5_1.bin"
        val f = File(filesDir, name)
        if (f.exists() && f.length() > 1_000_000) return f
        val part = File(filesDir, "$name.part")
        return try {
            val c = URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$name").openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            val total = c.contentLengthLong; var done = 0L; var last = -1
            c.inputStream.use { i -> part.outputStream().use { o ->
                val b = ByteArray(65536)
                while (running) {
                    val n = i.read(b); if (n < 0) break
                    o.write(b, 0, n); done += n
                    val pc = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pc != last && pc % 2 == 0) { last = pc; say("تحميل نموذج Whisper… $pc%") }
                }
            } }
            if (running && part.renameTo(f)) f else null
        } catch (e: Exception) { say("فشل تحميل النموذج: ${e.message}"); null }
    }

    private fun write(o: ByteArrayOutputStream, b: ShortArray, n: Int) {
        for (i in 0 until n) { o.write(b[i].toInt() and 0xFF); o.write((b[i].toInt() shr 8) and 0xFF) }
    }

    private fun submit(pcm: ByteArray) {
        val local = cfg.engine == "local"
        if (net.queue.size >= (if (local) 2 else 3)) return // تجنّب التراكم عند البطء
        net.execute {
            try {
                if (local) { WhisperLib.run(localCtx, pcm)?.let { (l, t) -> handle(t, l) }; return@execute }
                val t = WhisperClient.transcribe(pcm, cfg)
                if (t.length >= 2) langId.identifyLanguage(t).addOnSuccessListener { tag -> handle(t, tag) }
            } catch (e: Exception) { say("خطأ Whisper: ${e.message}") }
        }
    }

    /** src: رمز لغة الكلام (من Vosk أو من كشف اللغة) */
    private fun handle(text: String, src: String) {
        val s = TranslateLanguage.fromLanguageTag(src)
        if (s == null) { show(text, ""); return }
        translators.translate(text, s, cfg.target, { show(text, it) }, { show(text, it) })
    }

    private fun show(orig: String, tr: String) = ui.post {
        original.text = orig; translated.text = tr
        ui.removeCallbacks(clearTask); ui.postDelayed(clearTask, 7000)
    }
    private fun say(m: String) = ui.post { if (box != null) translated.text = m }
    private fun stopLater() = ui.post { stopSelf() }

    private fun showOverlay(msg: String) {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        original = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; gravity = Gravity.CENTER }
        translated = TextView(this).apply { setTextColor(Color.WHITE); textSize = 19f; gravity = Gravity.CENTER; text = msg }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xB3000000.toInt()); setPadding(24, 16, 24, 16)
            addView(original); addView(translated)
        }
        wm.addView(box, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM; y = 160 })
    }

    private fun copyAssets(src: String, dst: File) {
        val list = assets.list(src) ?: return
        if (list.isEmpty()) { dst.parentFile?.mkdirs(); assets.open(src).use { i -> dst.outputStream().use { i.copyTo(it) } } }
        else { dst.mkdirs(); list.forEach { copyAssets("$src/$it", File(dst, it)) } }
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("live", "LiveSub", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "live").setContentTitle("الترجمة الفورية تعمل")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
    }

    override fun onDestroy() {
        running = false
        ui.removeCallbacks(clearTask)
        net.shutdownNow()
        thread { // لا نحرّر السياق قبل انتهاء أي ترجمة جارية
            net.awaitTermination(15, TimeUnit.SECONDS)
            if (localCtx != 0L) { WhisperLib.free(localCtx); localCtx = 0L }
        }
        record?.run { try { stop() } catch (_: Exception) {}; release() }
        projection?.stop()
        box?.let { wm.removeView(it) }; box = null
        translators.close(); langId.close()
        super.onDestroy()
    }
}
