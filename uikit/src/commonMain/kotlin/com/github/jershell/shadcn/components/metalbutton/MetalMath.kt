/* Derived from metal-fx, MIT Copyright (c) 2026 Jakub Antalik.
 * Pinned source and full license: imports/metal-fx. */
package com.github.jershell.shadcn.components.metalbutton

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class MetalPreset { Chromatic, Silver, Gold }
enum class MetalVariant { Button, Circle }

internal data class MetalRgb(val r: Double, val g: Double, val b: Double) {
    operator fun plus(other: MetalRgb) = MetalRgb(r + other.r, g + other.g, b + other.b)
    operator fun times(weight: Double) = MetalRgb(r * weight, g * weight, b * weight)
    fun color(alpha: Float = 1f) = Color(r.toFloat().coerceIn(0f, 1f), g.toFloat().coerceIn(0f, 1f),
        b.toFloat().coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
    val luminance: Double get() = r * 0.2126 + g * 0.7152 + b * 0.0722
    companion object {
        fun hex(value: Int) = MetalRgb((value shr 16 and 255) / 255.0, (value shr 8 and 255) / 255.0, (value and 255) / 255.0)
    }
}

internal data class MetalMode(
    val colors: List<MetalRgb>, val speed: Double, val scale: Double,
    val vignette: Double, val vigOpacity: Double, val opacity: Float = 1f,
)

/** These colors are the requested artwork's source palette, not general application theme tokens. */
internal fun metalMode(preset: MetalPreset, dark: Boolean): MetalMode {
    val colors = when (preset) {
        MetalPreset.Chromatic -> if (dark) listOf(0x000000, 0xaae8ff, 0xc5fe9e, 0xf7888d, 0x0d0d0d)
            else listOf(0xffffff, 0xffffff, 0xffffff, 0xffb3b3, 0xadadad)
        MetalPreset.Silver -> if (dark) listOf(0x000000, 0xdedede, 0x747270, 0xe5e5e5, 0x0d0d0d)
            else listOf(0xf6f6f6, 0xffffff, 0xffffff, 0xf7f7f7, 0xc9c9c9)
        MetalPreset.Gold -> if (dark) listOf(0x000000, 0xffffff, 0xffffff, 0xf7d488, 0x0d0d0d)
            else listOf(0xfff8e1, 0xfffbe0, 0xffffff, 0xfff6d6, 0xd2c7a7)
    }.map(MetalRgb::hex)
    return MetalMode(colors, if (preset == MetalPreset.Gold && dark) 1.0 else 1.2,
        if (preset == MetalPreset.Chromatic && dark) 1.6 else 2.5,
        if (dark) 0.26 else when (preset) { MetalPreset.Chromatic -> 0.24; MetalPreset.Silver -> 0.2; MetalPreset.Gold -> 0.22 },
        if (dark) 0.6 else when (preset) { MetalPreset.Chromatic -> 0.16; MetalPreset.Silver -> 0.26; MetalPreset.Gold -> 0.24 },
        if (dark) when (preset) { MetalPreset.Chromatic -> 1f; MetalPreset.Silver -> 0.88f; MetalPreset.Gold -> 0.92f } else 1f)
}

private fun mod289(x: Double) = x - floor(x / 289.0) * 289.0
private fun permute(x: Double) = mod289((x * 34.0 + 1.0) * x)
private fun fract(x: Double) = x - floor(x)

private fun noiseCorner(x: Double, y: Double, permutation: Double): Double {
    var m = max(0.5 - x * x - y * y, 0.0)
    m *= m; m *= m
    val gradient = 2 * fract(permutation * 0.024390243902439) - 1
    val h = abs(gradient) - 0.5
    val a = gradient - floor(gradient + 0.5)
    m *= 1.79284291400159 - 0.85373472095314 * (a * a + h * h)
    return m * (a * x + h * y)
}

/** Scalar translation of the source GLSL Ashima simplex-noise function. */
internal fun metalNoise(vx: Double, vy: Double): Double {
    val c0 = 0.211324865405187
    val c1 = 0.366025403784439
    val c2 = -0.577350269189626
    var ix = floor(vx + (vx + vy) * c1)
    var iy = floor(vy + (vx + vy) * c1)
    val x0 = vx - ix + (ix + iy) * c0
    val y0 = vy - iy + (ix + iy) * c0
    val i1x = if (x0 > y0) 1.0 else 0.0
    val i1y = 1.0 - i1x
    ix = mod289(ix); iy = mod289(iy)
    return 130 * (noiseCorner(x0, y0, permute(permute(iy) + ix)) +
        noiseCorner(x0 + c0 - i1x, y0 + c0 - i1y, permute(permute(iy + i1y) + ix + i1x)) +
        noiseCorner(x0 + c2, y0 + c2, permute(permute(iy + 1) + ix + 1)))
}

private fun fbm(x: Double, y: Double): Double {
    // int(3 + complexity * 4), with the source's complexity = 0.68 => five octaves.
    var px = x; var py = y; var amplitude = 0.5; var value = 0.0
    repeat(5) { value += amplitude * metalNoise(px, py); px *= 2; py *= 2; amplitude *= 0.5 }
    return value
}

private fun palette(value: Double, mode: MetalMode): MetalRgb {
    val clamped = value.coerceIn(0.0, 1.0)
    val t = clamped * clamped * (3 - 2 * clamped)
    var total = 0.0001
    var red = 0.0; var green = 0.0; var blue = 0.0
    repeat(5) { index ->
        val distance = t - index * 0.25
        val weight = exp(-64 * distance * distance)
        total += weight
        red += mode.colors[index].r * weight
        green += mode.colors[index].g * weight
        blue += mode.colors[index].b * weight
    }
    return MetalRgb(red / total, green / total, blue / total)
}

private fun plasma(u: Double, v: Double, time: Double, mode: MetalMode): MetalRgb {
    val direction = 80 * PI / 180
    val px = (u - 0.5) * mode.scale + cos(direction) * time * 0.15
    val py = (v - 0.5) * mode.scale + sin(direction) * time * 0.15
    val frequency = 3.0 + 0.68 * 8
    var value = sin(px * frequency + time) + sin(py * frequency + time * 1.3) +
        sin((px + py) * frequency * 0.7 + time * 0.7) + sin(hypot(px, py) * frequency * 0.8 - time * 1.5)
    val wx = fbm(px + time * 0.1, py) * 0.6
    val wy = fbm(px + 5, py + time * 0.12 + 5) * 0.6
    value += (wx + wy) * 0.3
    return palette(value * 0.2 * 2 + 0.5, mode)
}

internal fun metalSmoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/** Same five-tap blur, gamma 1.3 and 96-pixel reference vignette as the source shader. */
internal fun sampleMetal(u: Double, v: Double, seconds: Double, mode: MetalMode, referencePixels: Double = 96.0): MetalRgb {
    val t = seconds * mode.speed
    val color = plasma(u, v, t, mode) * 0.4 + plasma(u + 0.02, v, t, mode) * 0.15 +
        plasma(u - 0.02, v, t, mode) * 0.15 + plasma(u, v + 0.02, t, mode) * 0.15 +
        plasma(u, v - 0.02, t, mode) * 0.15
    val edge = min(min(u, 1 - u), min(v, 1 - v))
    val range = 40 / referencePixels * (1 + mode.vignette * 3)
    val vignette = metalSmoothstep(0.0, 1.0, edge * edge / (range * range))
    val multiplier = 1 - mode.vignette * mode.vigOpacity + vignette * mode.vignette * mode.vigOpacity
    return MetalRgb(color.r.pow(1.3), color.g.pow(1.3), color.b.pow(1.3)) * multiplier
}

internal data class MetalGeometry(val width: Float, val height: Float, val radius: Float,
    val variant: MetalVariant, val ring: Float, val shaderScale: Float, val scale: Float = 1f) {
    val perimeter: Float get() = if (variant == MetalVariant.Circle) (2 * PI * radius).toFloat()
        else 2 * max(0f, width - 2 * radius) + 2 * max(0f, height - 2 * radius) + (2 * PI * radius).toFloat()

    fun point(arc: Float, inset: Float = ring / 2, outward: Float = 0f): Offset {
        val r = radius.coerceIn(0f, min(width, height) / 2)
        if (perimeter <= 0f) return Offset(width / 2, height / 2)
        var d = ((arc % perimeter) + perimeter) % perimeter
        val rad = max(0f, r - inset + outward)
        if (variant == MetalVariant.Circle) {
            val angle = -PI / 2 + d / perimeter * 2 * PI
            return Offset(width / 2 + rad * cos(angle).toFloat(), height / 2 + rad * sin(angle).toFloat())
        }
        val top = max(0f, width - 2 * r); val side = max(0f, height - 2 * r)
        val quarter = (PI * r / 2).toFloat()
        if (d < top) return Offset(r + d, inset - outward)
        d -= top
        fun corner(cx: Float, cy: Float, start: Double): Offset {
            val angle = start + if (quarter > 0f) d / quarter * PI / 2 else 0.0
            return Offset(cx + rad * cos(angle).toFloat(), cy + rad * sin(angle).toFloat())
        }
        if (d < quarter) return corner(width - r, r, -PI / 2)
        d -= quarter
        if (d < side) return Offset(width - inset + outward, r + d)
        d -= side
        if (d < quarter) return corner(width - r, height - r, 0.0)
        d -= quarter
        if (d < top) return Offset(width - r - d, height - inset + outward)
        d -= top
        if (d < quarter) return corner(r, height - r, PI / 2)
        d -= quarter
        if (d < side) return Offset(inset - outward, height - r - d)
        d -= side
        return corner(r, r, PI)
    }

    fun sample(point: Offset, seconds: Double, mode: MetalMode, referencePixels: Double): MetalRgb {
        val cropX = min(1.0, width / 140.0 / shaderScale)
        val cropY = min(1.0, height / 40.0 / shaderScale)
        val u = (1 - cropX) / 2 + point.x / width * cropX
        // GLSL fragment coordinates are bottom-up; the native Canvas is top-down.
        val v = 1 - ((1 - cropY) / 2 + point.y / height * cropY)
        return sampleMetal(u, v, seconds, mode, referencePixels)
    }
}

internal data class MetalRingSample(val point: Offset, val color: MetalRgb)
internal data class MetalFrame(val ring: List<MetalRingSample>, val perimeterColors: List<MetalRgb>)

internal fun createMetalFrame(geometry: MetalGeometry, seconds: Double, mode: MetalMode, density: Float): MetalFrame {
    val count = ceil(geometry.perimeter * 0.75f).toInt().coerceIn(64, 512)
    val pixels = 96.0 * density.coerceIn(1f, 2f)
    return MetalFrame(List(count + 1) { index ->
        val point = geometry.point(index.toFloat() / count * geometry.perimeter)
        MetalRingSample(point, geometry.sample(point, seconds, mode, pixels))
    }, List(16) { index ->
        geometry.sample(geometry.point(index / 16f * geometry.perimeter, inset = 1.5f * geometry.scale), seconds, mode, pixels)
    })
}
