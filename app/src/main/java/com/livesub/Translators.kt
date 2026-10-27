package com.livesub

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.*

/** مترجمات ML Kit مخزّنة مؤقتاً لكل زوج لغات (تحميل النموذج مرة واحدة) */
class Translators {
    private val map = HashMap<String, Translator>()

    @Synchronized private fun get(src: String, tgt: String) = map.getOrPut("$src>$tgt") {
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
    }

    fun translate(text: String, src: String, tgt: String, ok: (String) -> Unit, err: (String) -> Unit) {
        if (src == tgt) { ok(text); return }
        val t = get(src, tgt)
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { t.translate(text).addOnSuccessListener { r -> ok(r) }.addOnFailureListener { err("فشلت الترجمة") } }
            .addOnFailureListener { err("حمّل نموذج الترجمة: يلزم إنترنت أول مرة") }
    }

    fun close() { map.values.forEach { it.close() }; map.clear() }
}
