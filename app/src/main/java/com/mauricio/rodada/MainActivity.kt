package com.mauricio.rodada

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.mauricio.rodada.data.Gpx
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.ride.Session
import com.mauricio.rodada.ui.App
import com.mauricio.rodada.ui.Nav
import com.mauricio.rodada.ui.RodadaTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Pestaña que pide una notificación o un enlace (grupo, chat, garaje…). */
    val pendingTab = mutableStateOf<String?>(null)
    val pendingJoin = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { RodadaTheme { App(this) } }
        // Pantalla encendida mientras se rueda (si así está en Ajustes)
        lifecycleScope.launch {
            Session.dash.collectLatest { d ->
                if (d.recording && Store.settings.value.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        i.getStringExtra("tab")?.let { pendingTab.value = it }
        val data: Uri? = i.data ?: if (i.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } else null
        data ?: return
        if (data.scheme == "rodada") {
            data.getQueryParameter("c")?.let { pendingJoin.value = it; pendingTab.value = "grupo" }
            return
        }
        // Archivo GPX compartido o abierto
        runCatching {
            contentResolver.openInputStream(data)?.use { input ->
                Gpx.read(input, data.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Ruta importada")
            }
        }.getOrNull()?.let { r ->
            Nav.importedRoute.value = r
            pendingTab.value = "rutas"
        }
    }
}
