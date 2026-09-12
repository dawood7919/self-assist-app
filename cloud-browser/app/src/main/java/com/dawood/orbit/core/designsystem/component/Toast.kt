package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class OrbitToast(
    val message: String,
    val description: String? = null,
    val tone: OrbitTone = OrbitTone.Neutral,
    val icon: ImageVector? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMillis: Long = 3200L,
)

/**
 * Transient confirmations. One at a time, queued, always in the same place —
 * so a toast from a PDF tool and a toast from the notebook are the same event
 * to the user.
 */
class OrbitToastState {
    var current: OrbitToast? by mutableStateOf(null)
        private set

    private val mutex = Mutex()

    suspend fun show(toast: OrbitToast) {
        mutex.withLock {
            current = toast
            delay(toast.durationMillis)
            current = null
        }
    }

    fun dismiss() {
        current = null
    }
}

val LocalOrbitToastState = staticCompositionLocalOf { OrbitToastState() }

@Composable
fun OrbitToastHost(
    state: OrbitToastState,
    modifier: Modifier = Modifier,
) {
    val toast = state.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(OrbitTheme.motion.enter()) +
                slideInVertically(OrbitTheme.motion.enter()) { it / 2 },
            exit = fadeOut(OrbitTheme.motion.exit()) +
                slideOutVertically(OrbitTheme.motion.exit()) { it / 2 },
            label = "toast",
        ) {
            if (toast != null) {
                ToastSurface(toast, onDismiss = state::dismiss)
            }
        }
    }
}

@Composable
private fun ToastSurface(toast: OrbitToast, onDismiss: () -> Unit) {
    val shape = OrbitTheme.radius.shapeLg
    val icon = toast.icon ?: when (toast.tone) {
        OrbitTone.Success -> OrbitIcons.Success
        OrbitTone.Warning -> OrbitIcons.Warning
        OrbitTone.Error -> OrbitIcons.Error
        else -> OrbitIcons.Info
    }

    Row(
        modifier = Modifier
            .padding(OrbitTheme.spacing.lg)
            .widthIn(max = 420.dp)
            .orbitShadow(OrbitShadow.Lg, shape)
            .clip(shape)
            .background(OrbitTheme.colors.surfaceElevated)
            .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, shape)
            .padding(OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitIconTile(
            icon = icon,
            tint = toast.tone.contentColor(),
            background = toast.tone.containerColor(),
            size = 32.dp,
            iconSize = OrbitTheme.sizes.iconMd,
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitText(toast.message, style = OrbitTheme.typography.h4, maxLines = 2)
            val description = toast.description
            if (description != null) {
                OrbitText(
                    text = description,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 2,
                )
            }
        }
        val actionLabel = toast.actionLabel
        val onAction = toast.onAction
        if (actionLabel != null && onAction != null) {
            OrbitButton(
                text = actionLabel,
                onClick = {
                    onAction()
                    onDismiss()
                },
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
            )
        }
        OrbitIconButton(
            icon = OrbitIcons.Close,
            contentDescription = "Dismiss notification",
            onClick = onDismiss,
            size = OrbitButtonSize.Small,
        )
    }
}
