package com.dawood.orbit.core.designsystem.token

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion exists to explain state, hierarchy and navigation — never decoration.
 *
 * Every duration collapses to zero when the platform reports that the user
 * prefers reduced motion, so no component needs its own accessibility branch.
 */
@Immutable
data class OrbitMotion(val reduceMotion: Boolean = false) {

    /** Hover tints, focus rings, checkbox ticks. */
    val instant: Int get() = if (reduceMotion) 0 else 90

    /** The default for micro-interactions: press, selection, tint changes. */
    val fast: Int get() = if (reduceMotion) 0 else 150

    /** Expanding rows, inline reveals, toolbar swaps. */
    val normal: Int get() = if (reduceMotion) 0 else 210

    /** Sheets, dialogs, screen transitions. */
    val slow: Int get() = if (reduceMotion) 0 else 280

    /** Full-surface transitions such as entering a tool workspace. */
    val slower: Int get() = if (reduceMotion) 0 else 340

    /** Decelerating curve for elements entering the screen. */
    val enterEasing: Easing get() = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Accelerating curve for elements leaving the screen. */
    val exitEasing: Easing get() = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Symmetric curve for state changes that stay on screen. */
    val standardEasing: Easing get() = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> tweenFast(): AnimationSpec<T> = tween(fast, easing = standardEasing)

    fun <T> tweenNormal(): AnimationSpec<T> = tween(normal, easing = standardEasing)

    fun <T> tweenSlow(): AnimationSpec<T> = tween(slow, easing = standardEasing)

    fun <T> enter(durationMillis: Int = normal): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = enterEasing)

    fun <T> exit(durationMillis: Int = fast): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = exitEasing)

    /** For anything that should feel physical: press scale, drag settle. */
    fun <T> springy(): AnimationSpec<T> =
        if (reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        }
}
