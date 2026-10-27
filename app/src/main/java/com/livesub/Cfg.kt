package com.livesub

import android.content.Context

/** الإعدادات المحفوظة + قائمة اللغات */
class Cfg(ctx: Context) {
    private val p = ctx.getSharedPreferences("livesub", Context.MODE_PRIVATE)
    var engine: String get() = p.getString("engine", "local")!!; set(v) = p.edit().putString("engine", v).apply()
    var localModel: String get() = p.getString("lm", "base")!!; set(v) = p.edit().putString("lm", v).apply()
    var source: String get() = p.getString("source", "en")!!; set(v) = p.edit().putString("source", v).apply()
    var target: String get() = p.getString("target", "ar")!!; set(v) = p.edit().putString("target", v).apply()
    var apiKey: String get() = p.getString("key", "")!!; set(v) = p.edit().putString("key", v).apply()
    // يمكن تغييرهما لأي خدمة متوافقة مع OpenAI (مثل Groq)
    var endpoint: String get() = p.getString("endpoint", "https://api.openai.com/v1/audio/transcriptions")!!
        set(v) = p.edit().putString("endpoint", v).apply()
    var model: String get() = p.getString("model", "whisper-1")!!; set(v) = p.edit().putString("model", v).apply()

    companion object {
        val LANGS = listOf(
            "ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español",
            "de" to "Deutsch", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی",
            "hi" to "हिन्दी", "ur" to "اردو", "zh" to "中文", "ja" to "日本語",
            "ko" to "한국어", "id" to "Indonesia", "pt" to "Português", "it" to "Italiano"
        )
    }
}
