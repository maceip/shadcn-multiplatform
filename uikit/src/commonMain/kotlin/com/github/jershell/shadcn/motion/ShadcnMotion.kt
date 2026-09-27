package com.github.jershell.shadcn.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.composeunstyled.AnchorSide

/** Shared timing for this SDK's original Compose animations; reference: docs/MOTION_AUDIT.md. */
object ShadcnMotionTokens {
    const val Quick = 150
    const val Fast = 250
    const val Resize = 300
    const val Slow = 400
    val SmoothOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

val LocalShadcnMotionEnabled = staticCompositionLocalOf { true }
internal val LocalMenuMotionOrigin = staticCompositionLocalOf { TransformOrigin(0.5f, 0f) }

@Composable
internal fun menuMotionOrigin(side: AnchorSide): TransformOrigin = when (side) {
    AnchorSide.Top -> TransformOrigin(0.5f, 1f)
    AnchorSide.Bottom -> TransformOrigin(0.5f, 0f)
    AnchorSide.Start -> TransformOrigin(if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else 0f, 0.5f)
    AnchorSide.End -> TransformOrigin(if (LocalLayoutDirection.current == LayoutDirection.Ltr) 0f else 1f, 0.5f)
}

/** Null follows the system setting; false disables finite and decorative SDK motion. */
@Composable
fun ShadcnMotion(enabled: Boolean? = null, content: @Composable () -> Unit) {
    val systemReduced = prefersReducedMotion()
    CompositionLocalProvider(
        LocalShadcnMotionEnabled provides (LocalShadcnMotionEnabled.current && (enabled ?: !systemReduced)),
        content = content,
    )
}

@Composable
fun motionDurationMillis(duration: Int): Int = if (LocalShadcnMotionEnabled.current) duration else 0

@Composable
fun <T> shadcnTween(duration: Int = ShadcnMotionTokens.Fast): TweenSpec<T> =
    tween(motionDurationMillis(duration), easing = ShadcnMotionTokens.SmoothOut)

/** Animate discrete container layout changes (never attach to streaming output). */
@Composable
fun Modifier.shadcnAnimateContentSize(): Modifier = animateContentSize(shadcnTween(ShadcnMotionTokens.Resize))

/** Trigger-origin reveal for surfaces whose existing owner removes them immediately on close. */
@Composable
fun Modifier.shadcnMenuAppearance(origin: TransformOrigin = TransformOrigin(0.5f, 0f)): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, shadcnTween(), label = "menu presence")
    val enabled = LocalShadcnMotionEnabled.current
    return graphicsLayer {
        alpha = if (enabled) progress else 1f
        scaleX = if (enabled) 0.97f + progress * 0.03f else 1f
        scaleY = scaleX
        transformOrigin = origin
    }
}

@Composable
fun shadcnMenuEnter(origin: TransformOrigin = LocalMenuMotionOrigin.current): EnterTransition =
    if (LocalShadcnMotionEnabled.current) {
        fadeIn(shadcnTween()) + scaleIn(shadcnTween(), initialScale = 0.97f, transformOrigin = origin)
    } else EnterTransition.None

@Composable
fun shadcnMenuExit(origin: TransformOrigin = LocalMenuMotionOrigin.current): ExitTransition =
    if (LocalShadcnMotionEnabled.current) {
        fadeOut(shadcnTween(ShadcnMotionTokens.Quick)) +
            scaleOut(shadcnTween(ShadcnMotionTokens.Quick), targetScale = 0.99f, transformOrigin = origin)
    } else ExitTransition.None

/** A quiet presence transition. Semantic controls/content remain provided by the caller. */
@Composable
fun ShadcnVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (LocalShadcnMotionEnabled.current) fadeIn(shadcnTween()) else EnterTransition.None,
        exit = if (LocalShadcnMotionEnabled.current) fadeOut(shadcnTween(ShadcnMotionTokens.Quick)) else ExitTransition.None,
        content = content,
    )
}

@Composable
internal expect fun prefersReducedMotion(): Boolean
