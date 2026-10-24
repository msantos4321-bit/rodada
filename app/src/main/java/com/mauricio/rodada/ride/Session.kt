package com.mauricio.rodada.ride

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mauricio.rodada.data.Live
import com.mauricio.rodada.data.Msg
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Dash(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val startT: Long = 0,
    val speed: Double = 0.0,
    val lean: Double = 0.0,
    val maxLeanL: Double = 0.0,
    val maxLeanR: Double = 0.0,
    val latG: Double = 0.0,
    val longG: Double = 0.0,
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    val curves: Int = 0,
    val location: P? = null,
    val heading: Double = 0.0,
    val gps: Boolean = false,
    val nav: NavInfo? = null,
    val launch: String = "",
    val notice: String = "",
    val sharing: Boolean = false,
    val riders: List<Live> = emptyList(),
    val groupAlert: String = "",
    val sos: Msg? = null,
    val rerouting: Boolean = false,
    val track: List<P> = emptyList(),
    /** Kilómetros que quedan de gasolina (-1 = no se sabe). */
    val fuelKm: Int = -1,
    val sharingFamily: Boolean = false,
)

data class CrashState(val secondsLeft: Int, val sent: Boolean = false, val message: String = "")

/** Estado compartido entre el servicio de la rodada y las pantallas. */
object Session {
    private val _dash = MutableStateFlow(Dash())
    val dash: StateFlow<Dash> get() = _dash
    internal fun set(d: Dash) { _dash.value = d }

    private val _crash = MutableStateFlow<CrashState?>(null)
    val crash: StateFlow<CrashState?> get() = _crash
    internal fun setCrash(c: CrashState?) { _crash.value = c }

    /** Ruta que se va a seguir en la siguiente rodada (o la que se sigue ahora). */
    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> get() = _route
    fun setRoute(r: Route?) { _route.value = r; RideService.routeChanged() }

    /** Paradas del destino (para recalcular). */
    var stops: List<P> = emptyList()

    /** Rodada recién terminada para abrir su resumen. */
    val finished = MutableStateFlow<String?>(null)

    /** Liga «Sígueme» activa (la familia ve el viaje). */
    @Volatile var familyToken: String? = null

    @Volatile var cancelCrash = false
    @Volatile var helpNow = false

    private fun located(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Devuelve false si falta el permiso de ubicación (y abre los ajustes de la app para darlo). */
    fun start(ctx: Context, share: Boolean): Boolean {
        if (!located(ctx)) {
            android.widget.Toast.makeText(ctx, "Rodada necesita el permiso de ubicación precisa para grabar la rodada.", android.widget.Toast.LENGTH_LONG).show()
            runCatching {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        val i = Intent(ctx, RideService::class.java).setAction(RideService.START).putExtra("share", share)
        ContextCompat.startForegroundService(ctx, i)
        return true
    }

    private fun launch(ctx: Context, action: String) {
        val i = Intent(ctx, RideService::class.java).setAction(action)
        if (located(ctx)) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
    }

    fun stop(ctx: Context) {
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.STOP))
    }

    fun pause(ctx: Context) {
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.PAUSE))
    }

    fun setShare(ctx: Context, on: Boolean) {
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.SHARE).putExtra("share", on))
    }

    /** Botón SOS manual: avisa de inmediato. */
    fun sos(ctx: Context) {
        launch(ctx, RideService.SOS)
    }

    /** Simula una caída para probar la alerta (no manda mensajes reales). */
    fun testCrash(ctx: Context) {
        launch(ctx, RideService.TEST)
    }

    /** Empieza a mandar la ubicación a la liga «Sígueme» (si ya hay rodada). */
    fun familyOn(ctx: Context) {
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.FAMILY))
    }

    /** «Llené el tanque» durante la rodada. */
    fun refuel(ctx: Context) {
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.REFUEL))
    }

    fun imOk() { cancelCrash = true }
    fun helpNow() { helpNow = true }
}
