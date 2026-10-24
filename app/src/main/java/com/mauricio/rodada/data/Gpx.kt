package com.mauricio.rodada.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

object Gpx {

    /** Lee un GPX (track, ruta o puntos) y lo convierte en una ruta para seguir el trazo. */
    fun read(input: InputStream, fallbackName: String): Route? = runCatching {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val trk = ArrayList<P>(); val rte = ArrayList<P>(); val wpt = ArrayList<P>()
        var name = ""
        var inName = false; var depthName = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    when (tag) {
                        "trkpt", "rtept", "wpt" -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (lat != null && lon != null) when (tag) {
                                "trkpt" -> trk += P(lat, lon); "rtept" -> rte += P(lat, lon); else -> wpt += P(lat, lon)
                            }
                        }
                        "name" -> if (name.isBlank()) { inName = true; depthName = parser.depth }
                    }
                }
                XmlPullParser.TEXT -> if (inName && parser.depth == depthName) { name = parser.text.trim(); inName = false }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "name") inName = false
            }
            parser.next()
        }
        val pts = when { trk.size >= 2 -> trk; rte.size >= 2 -> rte; wpt.size >= 2 -> wpt; else -> return null }
        val len = Geo.length(pts)
        Route(pts, emptyList(), len, len / (50 / 3.6), null, name.ifBlank { fallbackName }, isTrack = true)
    }.getOrNull()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** GPX de una rodada grabada, con velocidad e inclinación de cada punto. */
    fun write(ride: Ride): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Rodada\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>").append(esc(ride.title)).append("</name><trkseg>\n")
        for (t in ride.points) {
            sb.append("<trkpt lat=\"").append(t.lat).append("\" lon=\"").append(t.lon).append("\">")
            if (!t.alt.isNaN()) sb.append("<ele>").append(String.format(java.util.Locale.US, "%.1f", t.alt)).append("</ele>")
            sb.append("<time>").append(Instant.ofEpochMilli(t.time)).append("</time>")
            sb.append("<extensions><speed>").append(String.format(java.util.Locale.US, "%.2f", t.speed / 3.6))
                .append("</speed><lean>").append(String.format(java.util.Locale.US, "%.1f", t.lean)).append("</lean></extensions>")
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    /** GPX de una ruta planeada (para pasarla a otra app o a un amigo). */
    fun write(route: Route, name: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Rodada\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>").append(esc(name)).append("</name><trkseg>\n")
        route.points.forEach { sb.append("<trkpt lat=\"").append(it.lat).append("\" lon=\"").append(it.lon).append("\"/>\n") }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }
}
