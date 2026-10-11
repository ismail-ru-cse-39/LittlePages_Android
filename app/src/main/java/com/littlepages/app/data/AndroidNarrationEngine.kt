package com.littlepages.app.data

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class NarrationVoice(val id: String, val label: String)

data class NarrationState(
    val ready: Boolean = false,
    val error: String? = null,
    val voices: List<NarrationVoice> = emptyList(),
    val activeRange: IntRange? = null,
    val wordTimingAvailable: Boolean = false,
    val finished: Boolean = false
)

/** Uses the device's installed English TTS voice; it does not download or retain audio files. */
class AndroidNarrationEngine(context: Context, private val onState: (NarrationState) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicLong()
    @Volatile private var activeUtteranceId: String = ""
    @Volatile private var activeTextOffset: Int = 0
    private var engine: TextToSpeech? = null
    private var currentState = NarrationState()

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    publish(currentState.copy(error = "Text-to-speech is unavailable on this device."))
                } else {
                    initializeEngine()
                }
            }
        } catch (_: Exception) {
            publish(currentState.copy(error = "Text-to-speech could not be started."))
        }
    }

    fun speak(text: String, startOffset: Int, rate: Float, voiceId: String?): Boolean {
        val tts = engine ?: return false
        if (!currentState.ready || text.isEmpty()) return false
        if (voiceId != null) tts.voices?.firstOrNull { it.name == voiceId }?.let(tts::setVoice)
        tts.setSpeechRate(rate)
        val safeOffset = startOffset.coerceIn(0, text.length)
        activeTextOffset = safeOffset
        val utteranceId = "littlepages-${requestCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId
        publish(currentState.copy(activeRange = null, wordTimingAvailable = false, finished = false, error = null))
        val accepted = tts.speak(text.substring(safeOffset), TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId) == TextToSpeech.SUCCESS
        if (!accepted) publish(currentState.copy(error = "The selected voice could not read this page."))
        return accepted
    }

    fun stop() {
        activeUtteranceId = ""
        engine?.stop()
        publish(currentState.copy(activeRange = null, finished = false))
    }

    fun close() {
        activeUtteranceId = ""
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun initializeEngine() {
        val tts = engine ?: return
        val languageStatus = tts.setLanguage(Locale.US)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            publish(currentState.copy(error = "Install an English text-to-speech voice in Android settings."))
            return
        }
        val voices = tts.voices.orEmpty()
            .filter { it.locale.language == "en" }
            .sortedBy { it.name }
            .take(8)
            .map { voice -> NarrationVoice(voice.name, voice.name.substringAfterLast('-').replace('_', ' ')) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) publish(currentState.copy(activeRange = null, finished = true))
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) publish(currentState.copy(activeRange = null, error = "Narration stopped because the voice engine reported an error."))
            }

            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == activeUtteranceId && end > start) {
                    val range = (activeTextOffset + start) until (activeTextOffset + end)
                    publish(currentState.copy(activeRange = range, wordTimingAvailable = true, error = null))
                }
            }
        })
        publish(currentState.copy(ready = true, error = null, voices = voices))
    }

    private fun publish(newState: NarrationState) {
        mainHandler.post {
            currentState = newState
            onState(newState)
        }
    }
}
