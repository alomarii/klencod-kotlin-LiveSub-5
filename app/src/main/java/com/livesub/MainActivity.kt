package com.livesub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var cfg: Cfg
    private lateinit var engineSp: Spinner
    private lateinit var srcSp: Spinner
    private lateinit var tgtSp: Spinner
    private lateinit var keyEt: EditText
    private lateinit var srcLabel: TextView
    private lateinit var keyLabel: TextView
    private lateinit var modelSp: Spinner
    private lateinit var modelLabel: TextView
    private val ENG = listOf("local", "whisper", "vosk")
    private val MODELS = listOf("tiny", "base", "small")

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK && r.data != null) {
                ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                    .putExtra("code", r.resultCode).putExtra("data", r.data))
                moveTaskToBack(true)
            }
        }
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { proceed() }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setPadding(0, 28, 0, 4) }
    private fun langSpinner(sel: String) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Cfg.LANGS.map { it.second })
        setSelection(Cfg.LANGS.indexOfFirst { it.first == sel }.coerceAtLeast(0))
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        cfg = Cfg(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 48) }
        root.addView(TextView(this).apply {
            text = "الترجمة الفورية للفيديو"; textSize = 22f; gravity = Gravity.CENTER
        })
        root.addView(label("محرك التعرف على الصوت"))
        engineSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Whisper على الجهاز (بدون إنترنت، كشف تلقائي للغة)", "Whisper سحابي (إنترنت ومفتاح API)", "Vosk (بدون إنترنت، لغة واحدة)"))
            setSelection(ENG.indexOf(cfg.engine).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        root.addView(engineSp)
        modelLabel = label("حجم نموذج Whisper (يُحمَّل مرة واحدة)"); root.addView(modelLabel)
        modelSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("tiny — 31MB، الأسرع", "base — 57MB، متوازن", "small — 181MB، الأدق وأبطأ"))
            setSelection(MODELS.indexOf(cfg.localModel).coerceAtLeast(1))
        }
        root.addView(modelSp)
        keyLabel = label("مفتاح Whisper API"); root.addView(keyLabel)
        keyEt = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(cfg.apiKey); hint = "sk-..."
        }
        root.addView(keyEt)
        srcLabel = label("لغة الصوت (لوضع Vosk)"); root.addView(srcLabel)
        srcSp = langSpinner(cfg.source); root.addView(srcSp)
        root.addView(label("الترجمة إلى"))
        tgtSp = langSpinner(cfg.target); root.addView(tgtSp)
        root.addView(Button(this).apply { text = "ابدأ الترجمة"; setOnClickListener { save(); proceed() } })
        root.addView(Button(this).apply {
            text = "إيقاف"; setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        })
        root.addView(TextView(this).apply {
            text = "بعد البدء شغّل أي فيديو في أي تطبيق وستظهر الترجمة أسفل الشاشة."
            textSize = 13f; setPadding(0, 32, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun refresh() {
        val e = ENG[engineSp.selectedItemPosition]
        fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE
        modelLabel.visibility = vis(e == "local"); modelSp.visibility = modelLabel.visibility
        keyLabel.visibility = vis(e == "whisper"); keyEt.visibility = keyLabel.visibility
        srcLabel.visibility = vis(e == "vosk"); srcSp.visibility = srcLabel.visibility
    }

    private fun save() {
        cfg.engine = ENG[engineSp.selectedItemPosition]
        cfg.localModel = MODELS[modelSp.selectedItemPosition]
        cfg.apiKey = keyEt.text.toString().trim()
        cfg.source = Cfg.LANGS[srcSp.selectedItemPosition].first
        cfg.target = Cfg.LANGS[tgtSp.selectedItemPosition].first
    }

    private fun proceed() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != 0 }) { permLauncher.launch(perms); return }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "اسمح بالظهور فوق التطبيقات ثم ارجع واضغط ابدأ", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (cfg.engine == "whisper" && cfg.apiKey.isBlank()) {
            Toast.makeText(this, "أدخل مفتاح Whisper API أو اختر Vosk", Toast.LENGTH_LONG).show(); return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}