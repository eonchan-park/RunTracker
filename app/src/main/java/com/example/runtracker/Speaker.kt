package com.example.runtracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 음성 안내(TTS). 한국어 음성을 사용하고, 여성 음성이 있으면 우선 선택합니다.
 * - 음악을 듣고 있으면 잠깐 소리를 줄이고(ducking) 안내가 끝나면 원래대로 돌려놓습니다.
 * - 엔진 준비 전에 들어온 안내는 잠시 모아 두었다가 준비되면 읽습니다.
 */
object Speaker {
    private const val TAG = "Speaker"

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()
    private val outstanding = AtomicInteger(0)
    private var counter = 0
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** 시작 버튼을 누를 때 호출해서, 달리기 시작 전에 엔진을 미리 준비한다. */
    fun init(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        audioManager = app.getSystemService(AudioManager::class.java)
        tts = TextToSpeech(app) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS 초기화 실패: $status")
            engine.shutdown()
            tts = null
            return
        }

        val result = engine.setLanguage(Locale.KOREA)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "한국어 TTS 음성 데이터가 없습니다. 설정 > 텍스트 음성 변환에서 한국어를 설치하세요.")
            pending.clear()
            return
        }

        engine.setAudioAttributes(attrs)
        pickFemaleVoice(engine)
        engine.setPitch(1.1f)
        engine.setSpeechRate(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()

            override fun onError(utteranceId: String?, errorCode: Int) = finished()
        })

        ready = true
        while (pending.isNotEmpty()) speak(pending.removeFirst())
    }

    /**
     * 이름에 "female"이 들어간 한국어 음성이 있으면 그것을 사용한다.
     * (안드로이드는 음성의 성별을 공식적으로 알려주지 않으므로, 없으면 기기의 기본 한국어 음성을 쓴다.
     *  구글 TTS의 기본 한국어 음성은 여성이다.)
     */
    private fun pickFemaleVoice(engine: TextToSpeech) {
        try {
            val female = engine.voices.orEmpty().firstOrNull {
                it.locale.language == Locale.KOREAN.language &&
                    it.name.contains("female", ignoreCase = true)
            }
            if (female != null) engine.voice = female
        } catch (e: Exception) {
            Log.w(TAG, "음성 선택 실패", e)
        }
    }

    fun speak(text: String) {
        val engine = tts
        if (engine == null || !ready) {
            if (pending.size >= 4) pending.removeFirst()
            pending.addLast(text)
            return
        }
        requestFocus()
        outstanding.incrementAndGet()
        val code = engine.speak(text, TextToSpeech.QUEUE_ADD, null, "run-${counter++}")
        if (code != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (outstanding.decrementAndGet() <= 0) {
            outstanding.set(0)
            abandonFocus()
        }
    }

    @Synchronized
    private fun requestFocus() {
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = req
        audioManager?.requestAudioFocus(req)
    }

    @Synchronized
    private fun abandonFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
