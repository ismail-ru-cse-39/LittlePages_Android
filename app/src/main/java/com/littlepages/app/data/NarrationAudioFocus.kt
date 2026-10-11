package com.littlepages.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class NarrationAudioFocus(context: Context, private val onFocusLost: () -> Unit) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                abandon()
                onFocusLost()
            }
        }
        .build()

    fun request(): Boolean = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        manager.abandonAudioFocusRequest(request)
    }
}
