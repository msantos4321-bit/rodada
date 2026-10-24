package com.mauricio.rodada.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mauricio.rodada.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Respaldo completo en un solo archivo (motos, bitácora con fotos, rodadas, contactos y ajustes)
 * para guardarlo en Drive o WhatsApp y pasarlo a un celular nuevo.
 */
object Backup {
    private val FOLDERS = listOf("rodadas", "bitacora", "motos")
    private const val PREFS = "rodada.xml"

    suspend fun export(ctx: Context, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = File(ctx.applicationInfo.dataDir, "shared_prefs/$PREFS")
            ctx.contentResolver.openOutputStream(dest)?.use { os ->
                ZipOutputStream(os).use { z ->
                    if (prefs.exists()) { z.putNextEntry(ZipEntry("prefs/$PREFS")); prefs.inputStream().use { it.copyTo(z) }; z.closeEntry() }
                    for (f in FOLDERS) {
                        File(ctx.filesDir, f).listFiles()?.forEach { file ->
                            z.putNextEntry(ZipEntry("$f/${file.name}")); file.inputStream().use { it.copyTo(z) }; z.closeEntry()
                        }
                    }
                }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** Restaura y reinicia la app. Devuelve false si el archivo no es un respaldo de Rodada. */
    suspend fun restore(ctx: Context, src: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            var found = false
            val tmp = File(ctx.cacheDir, "restaurar").apply { deleteRecursively(); mkdirs() }
            ctx.contentResolver.openInputStream(src)?.use { ins ->
                ZipInputStream(ins).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (!name.contains("..") && (name.startsWith("prefs/") || FOLDERS.any { name.startsWith("$it/") })) {
                            if (name == "prefs/$PREFS") found = true
                            val out = File(tmp, name); out.parentFile?.mkdirs()
                            out.outputStream().use { z.copyTo(it) }
                        }
                        e = z.nextEntry
                    }
                }
            }
            if (!found) return@runCatching false
            for (f in FOLDERS) {
                val from = File(tmp, f)
                if (from.exists()) { val to = File(ctx.filesDir, f); to.deleteRecursively(); from.copyRecursively(to, true) }
            }
            // Las rutas de las fotos de la bitácora pueden cambiar de celular: se ajustan al nuevo
            val prefsText = File(tmp, "prefs/$PREFS").readText()
                .replace(Regex("/data/(user/\\d+|data)/com\\.mauricio\\.rodada/files/"), ctx.filesDir.absolutePath.replace("\\", "\\\\") + "/")
            File(ctx.applicationInfo.dataDir, "shared_prefs/$PREFS").writeText(prefsText)
            tmp.deleteRecursively()
            true
        }.getOrDefault(false)
        if (ok) withContext(Dispatchers.Main) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            Runtime.getRuntime().exit(0)
        }
        ok
    }

    /** Borra todo lo de este celular (para «Borrar mis datos»). */
    fun wipe(ctx: Context) {
        FOLDERS.forEach { File(ctx.filesDir, it).deleteRecursively() }
        File(ctx.filesDir, "falla.txt").delete()
        ctx.getSharedPreferences("rodada", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }
}
