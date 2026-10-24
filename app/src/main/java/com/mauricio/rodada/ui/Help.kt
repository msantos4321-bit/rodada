package com.mauricio.rodada.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FAQ = listOf(
    "¿Dónde pongo el celular?" to "En el manubrio con un soporte firme es lo mejor para navegar. En el tanque, la chamarra o el bolsillo también funciona: la inclinación y la detección de caídas no dependen de cómo esté puesto.",
    "¿Cómo mide la inclinación?" to "Con la física de la curva: velocidad del GPS por el giro que mide el giroscopio. Es buena referencia (±2–3°), no una medición de laboratorio. En los primeros segundos y a menos de 10 km/h marca 0.",
    "¿Cómo sabe que me caí?" to "Busca tres cosas juntas: un golpe fuerte, que la moto se detenga de golpe y que se quede en otra posición. Entonces aparece «¿Estás bien?» con un minuto para responder. Si no contestas, manda SMS a tus contactos y alerta a tu grupo con tu ubicación. Pruébala en Seguridad → «Probar la alerta».",
    "¿Funciona con la pantalla apagada?" to "Sí. La rodada, la voz y la detección de caídas siguen. En Seguridad revisa que «Sin límite de batería» esté activado; algunos celulares (Xiaomi, Huawei, Samsung) cierran apps en el bolsillo.",
    "¿Gasta mucha batería?" to "Como cualquier navegador: el GPS es lo que más gasta. Con la pantalla apagada dura mucho más. Para viajes largos conecta el celular a la moto.",
    "¿Y si no hay señal?" to "La rodada se graba igual y la detección de caídas manda SMS (no necesita datos). Antes de salir, en Rutas toca «Descargar mapa de la ruta» para ver el mapa sin señal.",
    "¿Cómo invito a mis amigos?" to "Grupo → «Invitar amigos». Les llega la liga para descargar y el código del grupo.",
    "¿Qué es la ruta de la semana?" to "Cada semana Rodada propone la misma ruta a todo el grupo. Contestan Voy, Tal vez o No puedo, se ponen de acuerdo en el chat y la pueden cambiar.",
    "¿Qué es «Sígueme»?" to "Antes de salir, en Rodar, prende «Avisar a mi familia». Les mandas una liga y ven por dónde vas y a qué hora llegas, sin instalar nada. Al terminar ven «Llegó bien».",
    "¿Tengo varias motos?" to "Agrégalas en Garaje (+). Cada una lleva su mantenimiento, papeles, bitácora y rodadas. Antes de salir eliges con cuál vas.",
    "¿Qué pasa si cambio de celular?" to "Ajustes → Respaldo → «Guardar respaldo». Guarda el archivo en Drive o mándatelo por WhatsApp y en el celular nuevo toca «Restaurar respaldo».",
    "¿Quién ve mis datos?" to "Tus rodadas, garaje y bitácora se quedan en tu celular. Al grupo solo llega lo que compartes: tu nombre, tu moto, mensajes, fotos y tu ubicación mientras compartes una rodada.",
)

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Ayuda", "Preguntas frecuentes", onBack = { Nav.back() })
        FAQ.forEach { (q, a) ->
            var open by remember { mutableStateOf(false) }
            Card(Modifier.animateContentSize(), onClick = { open = !open }) {
                Text(q, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (open) Text(a, fontSize = 15.sp)
            }
        }
        Text("¿Algo no funciona? Escríbenos desde Ajustes → «Reportar un problema».", fontSize = 13.sp, color = C.text2,
            modifier = Modifier.fillMaxWidth().clickable { })
        Spacer(Modifier.height(16.dp))
    }
}
