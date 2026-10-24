package com.mauricio.rodada.ride

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mauricio.rodada.MainActivity
import com.mauricio.rodada.R
import com.mauricio.rodada.RodadaApp
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Ride
import com.mauricio.rodada.data.Rides
import com.mauricio.rodada.data.Route
import com.mauricio.rodada.data.Routing
import com.mauricio.rodada.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Servicio de la rodada: GPS, sensores, navegación, detección de caídas y grupo en vivo.
 * Sigue funcionando con la pantalla apagada y el celular en el bolsillo.
 */
class RideService : Service(), SensorEventListener, LocationListener {

    companion object {
        const val START = "start"; const val STOP = "stop"; const val PAUSE = "pause"
        const val SHARE = "share"; const val SOS = "sos"; const val TEST = "test"; const val REFUEL = "refuel"; const val FAMILY = "family"
        private const val NOTIF = 7

        @Volatile private var instance: RideService? = null
        fun routeChanged() { instance?.onRouteChanged() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sm: SensorManager
    private lateinit var lm: LocationManager
    private lateinit var voice: Voice
    private var wake: PowerManager.WakeLock? = null

    private var tele = Telemetry()
    private var nav: Navigator? = null
    private var recording = false
    private var paused = false
    private var startT = 0L
    private var share = false
    private var lastLoc: Location? = null
    private var heading = 0.0
    private var lastUi = 0L
    private var lastNotif = 0L
    private var rerouting = false
    private var groupJob: Job? = null
    private var familyJob: Job? = null
    private var crashJob: Job? = null
    private var notice = ""
    /** Alcance con la gasolina al empezar (+ lo ya recorrido si se cargó en el camino). */
    private var fuelBase: Double? = null
    private var fuelSaid = 0
    private var lastNav: NavInfo? = null
    private var lastLocT = 0L
    private var trackSize = -1
    private var track: List<P> = emptyList()
    private var noticeT = 0L

    // Caídas
    private var lastFastT = 0L
    /** Golpes de los últimos segundos: tiempo y fuerza en G. */
    private val spikes = ArrayDeque<Pair<Long, Double>>()
    private val speeds = ArrayDeque<Pair<Long, Double>>()
    private var stoppedAt = 0L
    private var stopChecked = false
    // Cansancio
    private var stoppedSince = 0L
    private var ridingSince = 0L
    private var fatigueSaid = 0L

    // Grupo
    private var riders = emptyList<com.mauricio.rodada.data.Live>()
    private val saidRider = HashMap<String, Long>()
    private var lastInbox = 0L
    private var sosMsg: com.mauricio.rodada.data.Msg? = null
    private var groupAlert = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        voice = Voice(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (hasLocation()) runCatching { goForeground() }
        else if (intent?.action == START) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            START -> { share = intent.getBooleanExtra("share", false); begin() }
            STOP -> finish()
            PAUSE -> { paused = !paused; voice.say(if (paused) "Rodada en pausa" else "Continuamos", false); push(true) }
            SHARE -> { share = intent.getBooleanExtra("share", false); if (share) startGroup() else stopGroup(); push(true) }
            SOS -> { if (!recording) startSensorsLite(); triggerCrash(manual = true) }
            TEST -> { if (!recording) startSensorsLite(); triggerCrash(manual = false, test = true) }
            FAMILY -> { startFamily(); push(true) }
            REFUEL -> {
                Store.currentBike?.let { b -> Store.upsertBike(b.copy(refuelKm = b.odometer + (tele.distance / 1000).toInt())) }
                fuelBase = Store.currentBike?.rangeKm?.toDouble()?.takeIf { it > 0 }?.let { it + tele.distance / 1000 }
                fuelSaid = 0; say("Tanque lleno. Te alcanza para unos ${Store.currentBike?.rangeKm ?: 0} kilómetros."); push(true)
            }
            else -> if (!recording) stopSelf()
        }
        return START_STICKY
    }

    private fun goForeground() {
        val n = notification("Rodada", if (recording) "Grabando tu rodada" else "Listo")
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(NOTIF, n)
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RideService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RodadaApp.CH_RIDE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Terminar", stop)
            .build()
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun begin() {
        if (recording) { push(true); return }
        if (!hasLocation()) { stopSelf(); return }
        tele = Telemetry()
        recording = true; paused = false
        startT = System.currentTimeMillis()
        ridingSince = startT; fatigueSaid = 0
        fuelSaid = 0
        fuelBase = Store.currentBike?.let { b -> if (b.rangeKm > 0 && b.refuelKm >= 0) (b.rangeKm - (b.odometer - b.refuelKm)).toDouble() else null }
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rodada:ride").apply { acquire() }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250L, 0f, this, Looper.getMainLooper())
        Session.route.value?.let { nav = Navigator(it) { t, u -> voice.say(t, u) } }
        voice.say(if (nav != null) "Vamos. Sigue la ruta." else "Rodada iniciada. Buen viaje.", false)
        if (Group.groups.value.isEmpty()) scope.launch { Group.refresh() }
        if (share) startGroup()
        startFamily()
        loadHazards()
        push(true)
    }

    @SuppressLint("MissingPermission")
    private fun startSensorsLite() {
        if (!hasLocation()) return
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()) }
    }

    private fun onRouteChanged() {
        val r = Session.route.value
        if (r == null) nav = null else if (nav == null) nav = Navigator(r) { t, u -> voice.say(t, u) } else nav?.replace(r)
        lastNav = null
        loadHazards()
        push(true)
    }

    private fun loadHazards() {
        val g = Store.groupId.ifBlank { return }
        scope.launch { nav?.hazards = Group.hazards(g) }
    }

    private fun finish() {
        if (!recording) { stopEverything(); return }
        recording = false
        val end = System.currentTimeMillis()
        val stats = tele.stats((end - startT) / 1000.0)
        val pts = ArrayList(tele.points)
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", java.util.Locale("es", "MX"))
        val when_ = Instant.ofEpochMilli(startT).atZone(ZoneId.systemDefault()).format(fmt)
        val title = Session.route.value?.name?.takeIf { it.isNotBlank() } ?: "Rodada del $when_"
        if (stats.distanceM > 200 && pts.size > 5) {
            val ride = Ride(UUID.randomUUID().toString(), title, startT, end, Store.currentBike?.name.orEmpty(), stats, bikeId = Store.currentBike?.id.orEmpty(), points = pts)
            Rides.save(ride)
            Store.addKm(stats.distanceM / 1000)
            scope.launch { Group.addKm(stats.distanceM / 1000) }
            Session.finished.value = ride.id
            voice.say("Rodada terminada. ${Geo.fmtKm(stats.distanceM).replace("km", "kilómetros")}.", false)
        } else voice.say("Rodada terminada. Fue muy corta para guardarla.", false)
        stopEverything()
    }

    private fun stopEverything() {
        recording = false
        runCatching { sm.unregisterListener(this) }
        runCatching { lm.removeUpdates(this) }
        stopGroup()
        stopFamily(true)
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        Session.set(Dash())
        scope.launch { delay(4000); voice.shutdown() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        runCatching { sm.unregisterListener(this) }
        runCatching { lm.removeUpdates(this) }
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Sensores ----------
    override fun onSensorChanged(e: SensorEvent) {
        if (!recording) return
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                tele.onAccel(e.values[0], e.values[1], e.values[2], e.timestamp)
                val g = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / Telemetry.G
                if (g > 2.5) {
                    val now = System.currentTimeMillis()
                    spikes.addLast(now to g)
                    while (spikes.isNotEmpty() && now - spikes.first().first > 8_000) spikes.removeFirst()
                }
            }
            Sensor.TYPE_GYROSCOPE -> tele.onGyro(e.values[0], e.values[1], e.values[2], e.timestamp)
        }
        val now = System.currentTimeMillis()
        if (now - lastUi > 120) push(false)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- GPS ----------
    override fun onLocationChanged(loc: Location) {
        val p = P(loc.latitude, loc.longitude)
        Store.lastLocation = p
        if (!recording) { lastLoc = loc; return }
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6 else null
        if (loc.hasBearing() && (kmh ?: 0.0) > 5) heading = loc.bearing.toDouble()
        if (!paused) tele.onLocation(p, if (loc.hasAltitude()) loc.altitude else Double.NaN, kmh, loc.accuracy, loc.time)
        val speed = tele.speedMs * 3.6
        val now = System.currentTimeMillis()
        if (speed > 15) lastFastT = now

        // Navegación
        nav?.let { n ->
            val (info, reroute) = n.update(p, speed, loc.accuracy)
            lastNav = info
            if (reroute && !rerouting && !n.route.isTrack) reroute(p, n)
        }
        lastLocT = now

        // Caída
        checkCrash(now, speed)
        // Cansancio
        fatigue(now, speed)
        // Gasolina
        fuelLeft()?.let { left ->
            val level = when { left <= 25 -> 2; left <= 60 -> 1; else -> 0 }
            if (level > fuelSaid) {
                fuelSaid = level
                say(if (level == 2) "Atención: te quedan unos ${left} kilómetros de gasolina. Carga en la siguiente gasolinera." else "Te quedan unos ${left} kilómetros de gasolina.", level == 2)
                showNotice("Gasolina para ~$left km")
            }
        }

        lastLoc = loc
        push(true)
        if (now - lastNotif > 15_000) {
            lastNotif = now
            val n = notification("Rodada · ${Geo.fmtKm(tele.distance)}", "Velocidad ${speed.toInt()} km/h · máx. inclinación ${tele.maxLeanL.toInt()}°/${tele.maxLeanR.toInt()}°")
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF, n)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { say("El GPS está apagado") }

    private fun say(t: String, urgent: Boolean = false) = voice.say(t, urgent)

    /** Te saliste: se calcula cómo volver a la ruta un poco más adelante, sin perder el resto del camino. */
    private fun reroute(p: P, n: Navigator) {
        rerouting = true
        val old = n.route
        scope.launch {
            val idx = n.index
            // Punto para reincorporarse: ~1.5 km adelante en la ruta original
            val target = old.cum.getOrNull(idx)?.plus(1500) ?: 0.0
            var j = idx
            while (j < old.points.size - 1 && old.cum[j] < target) j++
            val rejoin = old.points[j]
            val part = Routing.route(listOf(p, rejoin), old.mode ?: com.mauricio.rodada.data.Mode.RAPIDA)
            if (part != null) {
                val restPts = old.points.subList(j, old.points.size)
                val offset = part.points.size
                val restMans = old.maneuvers.filter { it.shapeIndex >= j }.map { it.copy(shapeIndex = it.shapeIndex - j + offset) }
                val partMans = part.maneuvers.filter { it.type != 4 && it.type != 5 && it.type != 6 }
                val merged = Route(part.points + restPts, partMans + restMans, part.lengthM + (old.lengthM - (old.cum.getOrNull(j) ?: 0.0)),
                    part.timeS + old.timeS * (1 - (old.cum.getOrNull(j) ?: 0.0) / (old.cum.lastOrNull() ?: 1.0)), old.mode, old.name)
                Session.setRoute(merged)
            }
            rerouting = false
        }
    }

    // ---------- Caídas ----------
    /**
     * Caída = ibas a más de 20 km/h, hubo un golpe fuerte justo antes de detenerte de golpe,
     * sigues parado 5 segundos y la moto/celular quedó en otra posición (tirada).
     * Los baches sueltos no cuentan porque después sigues avanzando.
     */
    private fun checkCrash(now: Long, speed: Double) {
        speeds.addLast(now to speed)
        while (speeds.isNotEmpty() && now - speeds.first().first > 10_000) speeds.removeFirst()
        if (!Store.settings.value.crashDetection || crashJob != null || paused) return
        if (speed > 5) { stoppedAt = 0L; stopChecked = false; return }
        if (stoppedAt == 0L) stoppedAt = now
        if (stopChecked || now - stoppedAt < 5_000) return
        stopChecked = true
        // ¿Venía rápido 2–6 s antes de parar?
        val before = speeds.filter { it.first in (stoppedAt - 6_000)..(stoppedAt - 1_000) }.maxOfOrNull { it.second } ?: 0.0
        if (before < 20) return
        // Golpe más fuerte entre 5 s antes de parar y 2 s después
        val hit = spikes.filter { it.first in (stoppedAt - 5_000)..(stoppedAt + 2_000) }.maxOfOrNull { it.second } ?: 0.0
        val tilt = tele.tiltFromNormal()
        if ((hit >= 4.5 && tilt > 30) || (hit >= 2.5 && tilt > 55)) triggerCrash(manual = false)
    }

    private fun triggerCrash(manual: Boolean, test: Boolean = false) {
        if (crashJob != null) return
        Session.cancelCrash = false; Session.helpNow = manual
        val total = Store.settings.value.crashSeconds
        if (!manual) Session.setCrash(CrashState(total))
        // Pantalla de alerta (encima de la pantalla bloqueada)
        val i = Intent(this, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("test", test)
        val full = PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val okIntent = PendingIntent.getActivity(this, 4, Intent(this, CrashActivity::class.java).setAction("ok").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manual) {
            val n = NotificationCompat.Builder(this, RodadaApp.CH_ALERT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (test) "Prueba: ¿estás bien?" else "¿Estás bien?")
                .setContentText("Detectamos una posible caída. Toca para responder.")
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(full, true).setContentIntent(full).setAutoCancel(true)
                .addAction(0, "Estoy bien", okIntent)
                .build()
            nm.notify(99, n)
            runCatching { startActivity(i) }
        }
        val ring = runCatching { RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) }.getOrNull()
        ring?.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        crashJob = scope.launch {
            if (!manual) {
                voice.say("¿Estás bien? Detecté una posible caída. Si estás bien, toca Estoy bien. En un minuto aviso a tus contactos.", true, force = true)
                delay(6000)
                runCatching { ring?.play() }
            }
            var left = if (manual) 0 else total
            while (isActive && left > 0 && !Session.cancelCrash && !Session.helpNow) {
                Session.setCrash(CrashState(left))
                if (left % 2 == 0) runCatching { vib.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)) }
                if (left == 30 || left == 10) voice.say("Aviso a tus contactos en $left segundos", true, force = true)
                delay(1000); left--
            }
            runCatching { ring?.stop() }
            nm.cancel(99)
            if (Session.cancelCrash) {
                Session.setCrash(null)
                voice.say("Qué bueno. Seguimos.", true, force = true)
            } else {
                val p = lastLoc?.let { P(it.latitude, it.longitude) } ?: Store.lastLocation
                if (test) {
                    Session.setCrash(CrashState(0, sent = true, message = "Era una prueba: no se mandó nada. Así se verá el aviso a tus contactos:\n\n" + Sos.text(p, true)))
                } else {
                    val n = Sos.sms(this@RideService, p, !manual)
                    Sos.group(p, !manual)
                    val msg = when {
                        n > 0 -> "Se avisó a $n ${if (n == 1) "contacto" else "contactos"} por SMS y a tu grupo con tu ubicación."
                        Store.contacts.value.isEmpty() -> "No tienes contactos de emergencia guardados. Se avisó a tu grupo."
                        else -> "No se pudo mandar el SMS (revisa el permiso). Se avisó a tu grupo."
                    }
                    Session.setCrash(CrashState(0, sent = true, message = msg))
                    voice.say(msg, true, force = true)
                }
            }
            Session.cancelCrash = false; Session.helpNow = false
            crashJob = null
            if (!recording) { delay(1500); if (!recording) stopEverything() }
        }
    }

    // ---------- Cansancio ----------
    private fun fatigue(now: Long, speed: Double) {
        if (speed < 3) { if (stoppedSince == 0L) stoppedSince = now; if (now - stoppedSince > 10 * 60_000) ridingSince = now }
        else stoppedSince = 0L
        val limit = Store.settings.value.fatigueMinutes * 60_000L
        if (limit > 0 && now - ridingSince > limit && now - fatigueSaid > 30 * 60_000) {
            fatigueSaid = now
            val h = (now - ridingSince) / 3_600_000.0
            val txt = "Llevas ${if (h >= 1.5) "${h.toInt()} horas" else "más de una hora"} rodando sin descanso. Toma un descanso, agua y estira."
            say(txt); showNotice("Toma un descanso")
        }
    }

    private fun fuelLeft(): Int? = fuelBase?.let { (it - tele.distance / 1000).toInt().coerceAtLeast(0) }

    private fun showNotice(t: String) { notice = t; noticeT = System.currentTimeMillis() }

    // ---------- Grupo en vivo ----------
    private fun startGroup() {
        if (groupJob != null) return
        val g = Store.groupId.ifBlank { return }
        groupJob = scope.launch {
            lastInbox = Store.lastMsgId
            while (isActive) {
                val l = lastLoc
                if (l != null) {
                    val status = when { Session.crash.value != null -> "sos"; paused -> "pausa"; (tele.speedMs * 3.6) < 3 -> "detenido"; else -> "rodando" }
                    Group.liveUpdate(g, P(l.latitude, l.longitude), tele.speedMs * 3.6, heading, battery(), status)
                }
                Group.live(g)?.let { list ->
                    riders = list.filter { it.member != Store.memberId }
                    checkRiders()
                }
                Group.inbox(lastInbox)?.let { msgs ->
                    msgs.forEach { m ->
                        lastInbox = maxOf(lastInbox, m.id)
                        when (m.kind) {
                            "sos" -> { sosMsg = m; say("Alerta del grupo. ${m.body.replace("🚨", "")}", true) }
                            "quick" -> say("${m.name}: ${m.body}")
                            "hazard" -> { say(m.body); loadHazards() }
                        }
                    }
                    Store.lastMsgId = lastInbox
                }
                push(true)
                delay(5000)
            }
        }
    }

    private fun startFamily() {
        if (familyJob != null) return
        val token = Session.familyToken ?: return
        familyJob = scope.launch {
            while (isActive) {
                val l = lastLoc
                val n = lastNav
                val status = when { Session.crash.value?.sent == true -> "sos"; paused -> "pausa"; else -> "rodando" }
                com.mauricio.rodada.data.Family.update(token, l?.let { P(it.latitude, it.longitude) }, tele.speedMs * 3.6,
                    n?.let { (it.remainingS / 60).toInt() }, n?.let { it.remainingM / 1000 }, status)
                delay(30_000)
            }
        }
    }

    private fun stopFamily(arrived: Boolean) {
        familyJob?.cancel(); familyJob = null
        val token = Session.familyToken ?: return
        Session.familyToken = null
        val l = lastLoc
        CoroutineScope(Dispatchers.IO).launch {
            com.mauricio.rodada.data.Family.update(token, l?.let { P(it.latitude, it.longitude) }, 0.0, 0, 0.0, if (arrived) "llego" else "terminado")
        }
    }

    private fun stopGroup() {
        groupJob?.cancel(); groupJob = null
        val g = Store.groupId
        if (g.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { Group.liveStop(g) }
        riders = emptyList()
    }

    private fun checkRiders() {
        val me = lastLoc ?: return
        val mp = P(me.latitude, me.longitude)
        val now = System.currentTimeMillis()
        val alerts = ArrayList<String>()
        for (r in riders) {
            val d = Geo.dist(mp, r.p)
            val key: String
            val text: String
            when {
                r.status == "sos" -> { key = "${r.member}-sos"; text = "${r.name} pidió ayuda a ${Geo.fmtKm(d)}" }
                r.ageS > 120 -> { key = "${r.member}-señal"; text = "${r.name} sin señal hace ${r.ageS / 60} min" }
                r.status == "detenido" && d > 1500 && tele.speedMs * 3.6 > 20 -> { key = "${r.member}-det"; text = "${r.name} se detuvo a ${Geo.fmtKm(d)}" }
                d > 3000 -> { key = "${r.member}-lejos"; text = "${r.name} se separó: va a ${Geo.fmtKm(d)}" }
                else -> continue
            }
            alerts += text
            val last = saidRider[key] ?: 0L
            if (now - last > 10 * 60_000) { saidRider[key] = now; say(text, r.status == "sos") }
        }
        groupAlert = alerts.firstOrNull().orEmpty()
    }

    private fun battery(): Int = runCatching {
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

    // ---------- Pantalla ----------
    private fun push(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUi < 100) return
        lastUi = now
        val l = lastLoc
        val navInfo = if (nav != null) lastNav else null
        if (tele.points.size != trackSize && (tele.points.size % 5 == 0 || force)) {
            trackSize = tele.points.size
            track = tele.points.map { P(it.lat, it.lon) }
        }
        if (now - noticeT > 15_000) notice = ""
        Session.set(Dash(
            recording = recording, paused = paused, startT = startT,
            speed = tele.speedMs * 3.6, lean = tele.lean, maxLeanL = tele.maxLeanL, maxLeanR = tele.maxLeanR,
            latG = tele.latG, longG = tele.longG, distanceM = tele.distance, movingS = tele.movingS, curves = tele.curves,
            location = l?.let { P(it.latitude, it.longitude) }, heading = heading,
            gps = l != null && now - lastLocT < 10_000,
            nav = navInfo, launch = tele.lastLaunch, notice = notice, sharing = groupJob != null,
            riders = riders, groupAlert = groupAlert, sos = sosMsg, rerouting = rerouting,
            track = track,
            fuelKm = fuelLeft() ?: -1,
            sharingFamily = familyJob != null,
        ))
    }
}
