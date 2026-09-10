package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The only colour the Cloud Browser tool may define for itself.
 *
 * Everything else must use [OrbitTheme] tokens directly. It exists because
 * it has no semantic token: the stream viewport needs a scrim that reads as
 * "video surface" in both themes.
 */
object CloudColors {

    /**
     * Tinted veil behind the stream placeholder and fullscreen preview.
     * Darker in dark theme so the "not connected" copy stays legible.
     */
    val streamScrim: Color
        @Composable
        @ReadOnlyComposable
        get() = if (OrbitTheme.colors.isDark) {
            Color.Black.copy(alpha = 0.55f)
        } else {
            Color.Black.copy(alpha = 0.38f)
        }
}
