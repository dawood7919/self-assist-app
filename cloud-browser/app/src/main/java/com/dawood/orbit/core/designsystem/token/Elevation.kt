package com.dawood.orbit.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shadows are soft and sparse: they separate layers, they do not decorate.
 * Dark mode leans on borders and surface lightness instead, so the same token
 * reads correctly in both themes.
 */
@Immutable
data class OrbitElevation(
    val none: Dp = 0.dp,
    /** Resting cards and inputs. */
    val sm: Dp = 2.dp,
    /** Popovers, dropdowns, hovered cards. */
    val md: Dp = 10.dp,
    /** Dialogs and bottom sheets. */
    val lg: Dp = 22.dp,
    /** Command palette — the top-most layer in the product. */
    val xl: Dp = 36.dp,
)

enum class OrbitShadow { None, Sm, Md, Lg, Xl }
