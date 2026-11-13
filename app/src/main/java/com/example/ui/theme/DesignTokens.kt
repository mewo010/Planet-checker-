package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

object PlanetSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object PlanetRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val pill = 999.dp
}

object PlanetElevation {
    val none = 0.dp
    val subtle = 2.dp
    val card = 4.dp
    val overlay = 8.dp
}

object PlanetMotion {
    const val fastMs = 180
    const val mediumMs = 260
    const val slowMs = 360
    const val counterMs = 260
    const val progressMs = 450
    const val pulseHighlightMs = 650

    fun <T> standardTween(durationMs: Int = mediumMs, reducedMotion: Boolean = false) =
        tween<T>(
            durationMillis = if (reducedMotion) 0 else durationMs,
            easing = FastOutSlowInEasing
        )

    fun <T> subtleSpring(reducedMotion: Boolean = false) =
        if (reducedMotion) tween<T>(durationMillis = 0)
        else spring<T>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
}

@Composable
fun rememberReducedMotion(): Boolean {
    val inInspection = LocalInspectionMode.current
    if (inInspection) return true
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}
