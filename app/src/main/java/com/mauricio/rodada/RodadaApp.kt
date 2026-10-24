package com.mauricio.rodada

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mauricio.rodada.data.License
import com.mauricio.rodada.data.Rides
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.work.Avisos
import org.maplibre.android.MapLibre

class RodadaApp : Application() {
    companion object {
        const val CH_RIDE = "rodada"
        const val CH_ALERT = "alertas"
        const val CH_GROUP = "grupo"
        const val CH_GARAGE = "garaje"
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Rides.init(this)
        License.init(this)
        MapLibre.getInstance(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RIDE, "Rodada en curso", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Caídas y emergencias", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerta de caída y avisos de ayuda del grupo"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_GROUP, "Grupo y ruta de la semana", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_GARAGE, "Mantenimiento y papeles", NotificationManager.IMPORTANCE_DEFAULT))
        Avisos.schedule(this)
    }
}
