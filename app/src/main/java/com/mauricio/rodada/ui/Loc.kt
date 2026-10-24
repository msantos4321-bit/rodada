package com.mauricio.rodada.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Store
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object Loc {
    fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun last(ctx: Context): P? {
        if (!granted(ctx)) return Store.lastLocation
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return best?.let { P(it.latitude, it.longitude) }?.also { Store.lastLocation = it } ?: Store.lastLocation
    }

    /** Ubicación de ahorita (espera hasta 12 s al GPS); si no, la última conocida. */
    @SuppressLint("MissingPermission")
    suspend fun now(ctx: Context): P? {
        if (!granted(ctx)) return Store.lastLocation
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return last(ctx)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val l: Location? = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine { cont ->
                    lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }
            }
            if (l != null) return P(l.latitude, l.longitude).also { Store.lastLocation = it }
        }
        return last(ctx)
    }
}
