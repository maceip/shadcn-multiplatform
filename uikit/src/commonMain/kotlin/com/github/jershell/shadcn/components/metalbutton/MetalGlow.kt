/* Derived from metal-fx, MIT Copyright (c) 2026 Jakub Antalik. See imports/metal-fx/LICENSE. */
package com.github.jershell.shadcn.components.metalbutton

import androidx.compose.ui.geometry.Offset
import kotlin.math.*
import kotlin.random.Random

internal data class MetalGlowFrame(val center: Offset, val extraCenter: Offset, val angleDegrees: Float,
    val opacity: Float, val tint: MetalRgb, val extraTint: MetalRgb, val ratio: Float)

private class GlowTween(val from: Double, val to: Double, val start: Double, val duration: Double) {
    fun done(now: Double) = now - start >= duration
    fun value(now: Double): Double = from + (to - from) * metalSmoothstep(0.0, duration, now - start)
}

/** Matches the source's 16 samples, luminance thresholds, dwell, relocation and tint timings. */
internal class MetalGlowState(private val random: Random = Random(0x4d4658)) {
    private var index = -1
    private var appearedAt = 0.0
    private var opacity = 0.0
    private var relocation: GlowTween? = null
    private var nextIndex = 0
    private var wander = 0.0
    private var wanderTarget = 0.0
    private var wanderFrames = 0
    private var tintFrom = MetalRgb(1.0, 1.0, 1.0)
    private var tintTarget = tintFrom
    private var tintStarted = -1.0
    private var tintHoldUntil = 0.0
    private var sampleTime = -1500.0
    private var sampled = emptyList<MetalRgb>()

    fun update(geometry: MetalGeometry, frame: MetalFrame, seconds: Double, dark: Boolean): MetalGlowFrame {
        val now = seconds * 1000
        // The source avoids GPU readback except every 1500 ms. Keep the same visual cadence.
        if (now - sampleTime >= 1500 || sampled.isEmpty()) {
            sampled = frame.perimeterColors
            sampleTime = now
        }
        val brightest = sampled.indices.maxBy { sampled[it].luminance }
        val current = if (index < 0) brightest else index
        val luminance = sampled[current].luminance
        val target = 0.34 + (0.85 - 0.34) * metalSmoothstep(0.08, 0.32, luminance)
        val tween = relocation
        if (index < 0) {
            index = brightest; appearedAt = now
            relocation = GlowTween(0.0, target, now, 1500.0)
        } else if (tween == null || tween.done(now)) {
            if (tween != null && tween.to == 0.0) {
                index = nextIndex; appearedAt = now; wander = 0.0; wanderTarget = 0.0; wanderFrames = 0
                val nextTarget = 0.34 + 0.51 * metalSmoothstep(0.08, 0.32, sampled[index].luminance)
                relocation = GlowTween(0.0, nextTarget, now, 1500.0)
            } else if (now - appearedAt >= 3000 && sampled[brightest].luminance - luminance > 0.05) {
                nextIndex = brightest
                relocation = GlowTween(opacity, 0.0, now, 1500.0)
            } else {
                relocation = null
                opacity += (target - opacity) * 0.00875
            }
        }
        relocation?.let { opacity = it.value(now) }
        opacity = opacity.coerceIn(0.0, 1.0)
        val ratio = geometry.perimeter / (200 + 40 * PI).toFloat()
        if (wanderFrames++ >= 120) {
            wanderTarget = (random.nextDouble() * 2 - 1) * 15 * ratio
            wanderFrames = 0
        }
        wander += (wanderTarget - wander) * 0.0075
        val arc = index / 16f * geometry.perimeter + wander.toFloat()
        val center = geometry.point(arc, 1.5f * geometry.scale)
        val extra = geometry.point(arc, 1.5f * geometry.scale, ratio * geometry.scale)
        val a = geometry.point(arc - 0.1f, 1.5f * geometry.scale)
        val b = geometry.point(arc + 0.1f, 1.5f * geometry.scale)
        val rawTint = sampled[index]
        if (tintStarted < 0) {
            tintFrom = rawTint; tintTarget = rawTint; tintStarted = now
            tintHoldUntil = if (dark) now + 2000 else 0.0
        } else if (now - tintStarted >= 400 && (!dark || now >= tintHoldUntil)) {
            tintFrom = tintTarget; tintTarget = rawTint; tintStarted = now; tintHoldUntil = now + 2000
        }
        val amount = ((now - tintStarted) / 400).coerceIn(0.0, 1.0)
        val mixed = tintFrom * (1 - amount) + tintTarget * amount
        val peak = max(max(mixed.r, mixed.g), mixed.b).takeIf { it > 0.0 } ?: 1.0
        val tint = if (dark) mixed * (1 / peak) else mixed
        val extraTint = if (dark) MetalRgb(1.0, 1.0, 1.0) else saturate(tint, 2.625, 1.008, 0.31)
        return MetalGlowFrame(center, extra, (atan2(b.y - a.y, b.x - a.x) * 180 / PI).toFloat(),
            opacity.toFloat(), tint, extraTint, ratio)
    }
}

internal fun saturate(rgb: MetalRgb, saturationMultiplier: Double, brightness: Double, minimumValue: Double = 0.0): MetalRgb {
    val maximum = max(max(rgb.r, rgb.g), rgb.b)
    val minimum = min(min(rgb.r, rgb.g), rgb.b)
    val delta = maximum - minimum
    val saturation = (if (maximum == 0.0) 0.0 else delta / maximum) * saturationMultiplier
    val value = max(minimumValue, maximum * brightness).coerceIn(0.0, 1.0)
    val hue = when {
        delta == 0.0 -> 0.0
        maximum == rgb.r -> ((rgb.g - rgb.b) / delta + 6) % 6
        maximum == rgb.g -> (rgb.b - rgb.r) / delta + 2
        else -> (rgb.r - rgb.g) / delta + 4
    }
    val chroma = value * saturation.coerceIn(0.0, 1.0)
    val x = chroma * (1 - abs(hue % 2 - 1))
    val m = value - chroma
    val base = when (hue.toInt()) {
        0 -> MetalRgb(chroma, x, 0.0); 1 -> MetalRgb(x, chroma, 0.0)
        2 -> MetalRgb(0.0, chroma, x); 3 -> MetalRgb(0.0, x, chroma)
        4 -> MetalRgb(x, 0.0, chroma); else -> MetalRgb(chroma, 0.0, x)
    }
    return base + MetalRgb(m, m, m)
}
