package com.dawood.orbit.tools.image

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in source-image pixels. */
@Immutable
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** A width and height in pixels. */
@Immutable
data class ImageSize(val width: Int, val height: Int) {
    val megapixels: Double get() = width.toDouble() * height / 1_000_000
    val label: String get() = "$width × $height"
}

/**
 * The arithmetic behind the image tools.
 *
 * Kept away from Bitmap so it can be tested: an off-by-one in a crop rectangle
 * is invisible until someone's photo comes back with a one-pixel black edge,
 * and rounding a resize the wrong way silently changes the aspect ratio.
 */
object ImageMath {

    /**
     * The largest size that fits inside [maxWidth] × [maxHeight] without
     * changing the aspect ratio. Never enlarges: asking for a bigger box than
     * the image returns the image.
     */
    fun fitWithin(source: ImageSize, maxWidth: Int, maxHeight: Int): ImageSize {
        if (source.width <= 0 || source.height <= 0) return source
        if (maxWidth <= 0 || maxHeight <= 0) return source
        if (source.width <= maxWidth && source.height <= maxHeight) return source

        val scale = min(
            maxWidth.toDouble() / source.width,
            maxHeight.toDouble() / source.height,
        )
        return ImageSize(
            width = max(1, (source.width * scale).roundToInt()),
            height = max(1, (source.height * scale).roundToInt()),
        )
    }

    /** Scales so the longer edge becomes [longEdge]. Never enlarges. */
    fun scaleToLongEdge(source: ImageSize, longEdge: Int): ImageSize {
        if (longEdge <= 0) return source
        val currentLongEdge = max(source.width, source.height)
        if (currentLongEdge <= longEdge) return source
        val scale = longEdge.toDouble() / currentLongEdge
        return ImageSize(
            width = max(1, (source.width * scale).roundToInt()),
            height = max(1, (source.height * scale).roundToInt()),
        )
    }

    /** Scales by a percentage. 50 halves each edge. */
    fun scaleByPercent(source: ImageSize, percent: Int): ImageSize {
        val factor = percent.coerceIn(1, 400) / 100.0
        return ImageSize(
            width = max(1, (source.width * factor).roundToInt()),
            height = max(1, (source.height * factor).roundToInt()),
        )
    }

    /**
     * The largest centred rectangle of [ratioWidth]:[ratioHeight] that fits
     * inside the image. A ratio matching the image returns the whole image, so
     * cropping to the shape it already is never loses a row of pixels.
     */
    fun centreCrop(source: ImageSize, ratioWidth: Int, ratioHeight: Int): CropRect {
        if (source.width <= 0 || source.height <= 0 || ratioWidth <= 0 || ratioHeight <= 0) {
            return CropRect(0, 0, max(0, source.width), max(0, source.height))
        }
        val targetRatio = ratioWidth.toDouble() / ratioHeight
        val sourceRatio = source.width.toDouble() / source.height

        val (width, height) = if (sourceRatio > targetRatio) {
            // Too wide: full height, trimmed sides.
            val w = (source.height * targetRatio).roundToInt().coerceAtMost(source.width)
            w to source.height
        } else {
            // Too tall: full width, trimmed top and bottom.
            val h = (source.width / targetRatio).roundToInt().coerceAtMost(source.height)
            source.width to h
        }
        return CropRect(
            x = (source.width - width) / 2,
            y = (source.height - height) / 2,
            width = max(1, width),
            height = max(1, height),
        )
    }

    /** Swaps width and height, which is what a quarter turn does to a size. */
    fun rotated(source: ImageSize, degrees: Int): ImageSize =
        if (((degrees % 360) + 360) % 360 % 180 == 90) {
            ImageSize(source.height, source.width)
        } else {
            source
        }

    /**
     * A rough estimate of the encoded size, used to warn before a save rather
     * than to promise a number. JPEG at quality q averages a few bits per
     * pixel; PNG is lossless so it is estimated far more conservatively.
     */
    fun estimateBytes(size: ImageSize, format: ImageFormat, quality: Int): Long {
        val pixels = size.width.toLong() * size.height
        return when (format) {
            ImageFormat.Jpeg -> (pixels * (0.08 + quality / 100.0 * 0.45)).toLong()
            ImageFormat.Webp -> (pixels * (0.05 + quality / 100.0 * 0.30)).toLong()
            ImageFormat.Png -> (pixels * 2.2).toLong()
        }
    }
}

/** The formats the image tools can write. */
enum class ImageFormat(val label: String, val extension: String, val mimeType: String) {
    Jpeg("JPEG", "jpg", "image/jpeg"),
    Png("PNG", "png", "image/png"),
    Webp("WebP", "webp", "image/webp");

    /** PNG ignores quality, so the UI should not offer the slider for it. */
    val usesQuality: Boolean get() = this != Png

    /** Only PNG and WebP keep an alpha channel. */
    val keepsTransparency: Boolean get() = this != Jpeg
}
