#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL Java_com_livesub_WhisperLib_init(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) ctx;
}

// يعيد "رمز_اللغة|النص" بصيغة UTF-8 كبايتات (لتفادي أخطاء JNI مع نص غير صالح)
JNIEXPORT jbyteArray JNICALL Java_com_livesub_WhisperLib_transcribe(
        JNIEnv *env, jclass, jlong h, jfloatArray audio, jint threads) {
    auto *ctx = (whisper_context *) h;
    std::string out;
    if (ctx) {
        jsize n = env->GetArrayLength(audio);
        jfloat *d = env->GetFloatArrayElements(audio, nullptr);
        whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        wp.print_realtime = false; wp.print_progress = false;
        wp.print_timestamps = false; wp.print_special = false;
        wp.translate = false;
        wp.language = "auto";          // كشف تلقائي للغة
        wp.detect_language = false;
        wp.n_threads = threads;
        wp.single_segment = true;
        wp.no_context = true;
        wp.suppress_blank = true;
        wp.no_speech_thold = 0.6f;
        wp.temperature_inc = 0.0f;     // أسرع: بلا إعادة محاولات
        if (whisper_full(ctx, wp, d, n) == 0) {
            std::string text;
            int ns = whisper_full_n_segments(ctx);
            for (int i = 0; i < ns; i++) text += whisper_full_get_segment_text(ctx, i);
            out = std::string(whisper_lang_str(whisper_full_lang_id(ctx))) + "|" + text;
        }
        env->ReleaseFloatArrayElements(audio, d, JNI_ABORT);
    }
    jbyteArray r = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(r, 0, (jsize) out.size(), (const jbyte *) out.data());
    return r;
}

JNIEXPORT void JNICALL Java_com_livesub_WhisperLib_free(JNIEnv *, jclass, jlong h) {
    if (h) whisper_free((whisper_context *) h);
}

}
