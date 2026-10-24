package com.mauricio.rodada.data

import android.content.Context
import android.os.Build
import com.mauricio.rodada.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Si la app se cierra por un error, se guarda qué pasó y se manda en la siguiente apertura
 * (tabla rd_crashes en Supabase). Así se arreglan fallas que nadie reporta.
 */
object Crash {
    fun install(ctx: Context) {
        val file = File(ctx.filesDir, "falla.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(e)
                file.writeText("Hilo: ${t.name}\n$trace")
            }
            prev?.uncaughtException(t, e)
        }
        if (file.exists()) CoroutineScope(Dispatchers.IO).launch {
            val trace = runCatching { file.readText() }.getOrNull() ?: return@launch
            val r = Group.call("rd_crash_add", JSONObject().put("p_version", BuildConfig.VERSION_NAME)
                .put("p_device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}").put("p_trace", trace))
            if (r != null) file.delete()
        }
    }
}
