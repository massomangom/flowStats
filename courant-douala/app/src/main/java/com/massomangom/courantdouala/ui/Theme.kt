package com.massomangom.courantdouala.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.massomangom.courantdouala.logic.State
import com.massomangom.courantdouala.logic.Utility

val Night = Color(0xFF0F1B2D)
val NightCard = Color(0xFF17263D)
val NightLine = Color(0xFF243652)
val Bolt = Color(0xFFFFC83D)
val Drop = Color(0xFF3DA5FF)
val Ok = Color(0xFF34C77B)
val Cut = Color(0xFFFF5A5F)
val Muted = Color(0xFF8A9BB4)

fun utilityColor(u: Utility) = if (u == Utility.POWER) Bolt else Drop

fun stateColor(s: State) = when (s) {
    State.ON -> Ok
    State.OFF -> Cut
    State.UNKNOWN -> Muted
}

@Composable
fun CourantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Bolt,
            onPrimary = Night,
            secondary = Drop,
            background = Night,
            onBackground = Color.White,
            surface = NightCard,
            onSurface = Color.White,
            surfaceVariant = NightLine,
            onSurfaceVariant = Muted,
            surfaceContainer = NightCard,
            secondaryContainer = Color(0xFF3A3420),
            onSecondaryContainer = Bolt,
            error = Cut,
        ),
        content = content,
    )
}
