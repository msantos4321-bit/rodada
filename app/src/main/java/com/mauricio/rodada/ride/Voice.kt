package com.mauricio.rodada.ride

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mauricio.rodada.data.Store
import java.util.Locale

/** Voz en español para la navegación y los avisos (se oye por el intercomunicador si está conectado). */
class Voice(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private val pending = ArrayList<String>()
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val mx = Locale("es", "MX")
            if (tts.setLanguage(mx) < 0) tts.setLanguage(Locale("es"))
            tts.setAudioAttributes(attrs)
            tts.setSpeechRate(1.05f)
            ready = true
            pending.forEach { say(it) }
            pending.clear()
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
        })
    }

    /** [urgent] interrumpe lo que se esté diciendo (caídas, peligros). */
    fun say(text: String, urgent: Boolean = false, force: Boolean = false) {
        if (!force && !Store.settings.value.voice) return
        if (!ready) { pending += text; return }
        am.requestAudioFocus(focus)
        tts.speak(text, if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "r" + System.nanoTime())
    }

    fun stop() { runCatching { tts.stop() } }
    fun shutdown() { runCatching { tts.shutdown() } }
}
