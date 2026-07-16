package com.honglian.smartcycling.navi

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 导航语音播报封装:使用系统 TextToSpeech 播报高德导航诱导文本。
 * 优点:不依赖 SDK 内置语音引擎,开关完全可控。
 */
class NaviTts(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.CHINA)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    /** 播报一段语音(新提示打断旧提示)。 */
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "navi")
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
