package com.mauricio.rodada.ride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mauricio.rodada.ui.CrashOverlay
import com.mauricio.rodada.ui.RodadaTheme

/** Alerta de caída a pantalla completa, aunque el celular esté bloqueado. */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == "ok") { Session.imOk(); finish(); return }
        setContent {
            RodadaTheme {
                val c by Session.crash.collectAsState()
                LaunchedEffect(c) { if (c == null) finish() }
                c?.let { CrashOverlay(it) }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == "ok") { Session.imOk(); finish() }
    }
}
