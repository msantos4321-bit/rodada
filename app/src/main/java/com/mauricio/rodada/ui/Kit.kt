package com.mauricio.rodada.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier
        .shadow(1.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
        .clip(RoundedCornerShape(18.dp))
        .background(C.card)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(padding)
    Column(m, verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color = C.accent, textColor: Color = Color.White,
              icon: ImageVector? = null, height: Dp = 60.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor, disabledContainerColor = color.copy(alpha = 0.4f), disabledContentColor = textColor)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = C.text, bg: Color = C.card, height: Dp = 52.dp, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(height), shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = color),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (bg == C.card) 1.dp else 0.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
        .background(if (selected) C.accent else C.bg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.White else C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Tag(text: String, bg: Color = C.bg, color: Color = C.text) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, bg: Color = C.card) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = C.text2)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.text, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = C.accentText, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun Row2(title: String, subtitle: String = "", leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
         divider: Boolean = true, onClick: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.heightIn(min = 52.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = C.text2)
            }
            if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF8E8E93))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = C.accentText, bg: Color = C.accentSoft, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Avatar(name: String, color: Color, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun Header(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(end = 2.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = C.accentText)
        }
        Column(Modifier.weight(1f)) {
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = C.text2)
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, number: Boolean = false,
          phone: Boolean = false, singleLine: Boolean = true, placeholder: String = "") {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, color = C.text2) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = when { number -> KeyboardType.Decimal; phone -> KeyboardType.Phone; else -> KeyboardType.Text }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = Color(0xFFD1D1D6),
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedLabelColor = C.accentText))
}

@Composable
fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onYes(); onDismiss() }) { Text(yes, color = if (danger) C.red else C.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = C.text2) } },
        containerColor = Color.White)
}

@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, color: Color = C.text, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E5EA))) { Text(text, color = color, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Progress(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}
