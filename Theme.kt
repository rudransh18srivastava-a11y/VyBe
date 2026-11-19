package com.vybe.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VyBg = Color(0xFF0B0B12)
val VySurface = Color(0xFF151522)
val VySurfaceHi = Color(0xFF1F1F33)
val VyPurple = Color(0xFF8B5CF6)
val VyPink = Color(0xFFFF4FA3)
val VyText = Color(0xFFECECF4)
val VyMuted = Color(0xFF9A9AB0)
val VyGradient = Brush.linearGradient(listOf(VyPurple, VyPink))

@Composable
fun VyBeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VyPurple,
            secondary = VyPink,
            background = VyBg,
            surface = VySurface,
            surfaceVariant = VySurfaceHi,
            onPrimary = Color.White,
            onBackground = VyText,
            onSurface = VyText,
            onSurfaceVariant = VyMuted
        ),
        content = content
    )
}

@Composable
fun VyBeLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Vy",
            style = TextStyle(
                color = VyText,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            "Be",
            style = TextStyle(
                brush = VyGradient,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(VyGradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
