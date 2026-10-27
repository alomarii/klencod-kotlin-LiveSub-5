LiveSub - ترجمة فورية للفيديو (أندرويد 10+، يفضّل جهاز arm64)

الإعداد (مرة واحدة):
1) نفّذ setup_whisper.sh  (أو: داخل app/src/main/cpp نفّذ
   git clone --depth 1 --branch v1.7.4 https://github.com/ggerganov/whisper.cpp.git)
2) افتح المجلد في Android Studio، ووافق على تثبيت NDK و CMake عند الطلب، ثم Sync و Run.
3) عند أول تشغيل بوضع "Whisper على الجهاز" يُحمَّل النموذج تلقائياً (31-181MB) ثم يعمل بلا إنترنت.

أوضاع التعرف على الصوت:
- Whisper على الجهاز: كشف تلقائي للغة، بلا إنترنت، يستهلك معالجاً وبطارية.
- Whisper سحابي: أسرع وأدق، يحتاج مفتاح API (endpoint/model في Cfg.kt).
- Vosk: خفيف، لغة واحدة، ضع النموذج في app/src/main/assets/model/

الترجمة بين اللغات عبر ML Kit على الجهاز (نموذج كل لغة يُحمَّل مرة واحدة).
