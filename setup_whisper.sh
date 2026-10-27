#!/bin/sh
# ينزّل مصدر whisper.cpp داخل المشروع (مرة واحدة)
cd "$(dirname "$0")/app/src/main/cpp" || exit 1
git clone --depth 1 --branch v1.7.4 https://github.com/ggerganov/whisper.cpp.git
