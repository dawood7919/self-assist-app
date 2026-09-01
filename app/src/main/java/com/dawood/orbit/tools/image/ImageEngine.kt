package com.dawood.orbit.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Crop, resize, rotate and re-encode, on the device.
 *
 * Everything decodes with a sample size first, so a 50-megapixel photo does not
 * have to fit in memory at full resolution just to be shrunk.
 */
object ImageEngine {

    sealed interface Result {
        data class Success(val file: File, val size: ImageSize, val bytes: Long) : Result
        data class Failure(val message: String) : Result
    }

    /** What an image is before anything is done to it. */
    data class Source(
        val file: File,
        val displayName: String,
        val size: ImageSize,
        val bytes: Long,
        val rotationDegrees: Int,
    )

    /** How a single image should be transformed. */
    data class Recipe(
        val longEdge: Int? = null,
        val cropRatio: Pair<Int, Int>? = null,
        val rotation: Int = 0,
        val format: ImageFormat = ImageFormat.Jpeg,
        val quality: Int = 88,
    )

    /** Reads the dimensions without decoding the pixels. */
    suspend fun inspect(context: Context, uri: Uri): Source? = withContext(Dispatchers.IO) {
        val name = DocumentStore.displayName(context, uri) ?: "image"
        val copied = DocumentStore.copyIn(context, uri, fallbackName = name) ?: return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            copied.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            Source(
                file = copied,
                displayName = name,
                size = ImageSize(bounds.outWidth, bounds.outHeight),
                bytes = copied.length(),
                rotationDegrees = exifRotation(copied),
            )
        }.getOrNull()
    }

    /** The size the result will be, without doing the work. */
    fun previewSize(source: Source, recipe: Recipe): ImageSize {
        val upright = ImageMath.rotated(source.size, source.rotationDegrees)
        val cropped = recipe.cropRatio?.let { (w, h) ->
            val rect = ImageMath.centreCrop(upright, w, h)
            ImageSize(rect.width, rect.height)
        } ?: upright
        val scaled = recipe.longEdge?.let { ImageMath.scaleToLongEdge(cropped, it) } ?: cropped
        return ImageMath.rotated(scaled, recipe.rotation)
    }

    suspend fun process(
        context: Context,
        source: Source,
        recipe: Recipe,
        outputName: String,
    ): Result = withContext(Dispatchers.IO) {
        runCatching {
            val target = ImageMath.rotated(source.size, source.rotationDegrees)
                .let { upright ->
                    recipe.cropRatio?.let { ratio ->
                        val rect = ImageMath.centreCrop(upright, ratio.first, ratio.second)
                        ImageSize(rect.width, rect.height)
                    } ?: upright
                }
                .let { cropped ->
                    recipe.longEdge?.let { ImageMath.scaleToLongEdge(cropped, it) } ?: cropped
                }

            // Decoding at a sample size keeps peak memory near the output size
            // rather than the input size, which is what stops a big photo from
            // running the app out of memory.
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(source.size, target)
                inPreferredConfig =
                    if (recipe.format.keepsTransparency) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            }
            var bitmap = source.file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching Result.Failure("That image could not be decoded")

            // The camera's own rotation first, then anything the user asked for.
            val totalRotation = (source.rotationDegrees + recipe.rotation) % 360
            if (totalRotation != 0) {
                bitmap = transform(bitmap) { postRotate(totalRotation.toFloat()) }
            }

            recipe.cropRatio?.let { ratio ->
                val rect = ImageMath.centreCrop(
                    ImageSize(bitmap.width, bitmap.height),
                    ratio.first,
                    ratio.second,
                )
                val cropped = Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
                if (cropped !== bitmap) bitmap.recycle()
                bitmap = cropped
            }

            if (bitmap.width != target.width || bitmap.height != target.height) {
                val fitted = ImageMath.fitWithin(
                    ImageSize(bitmap.width, bitmap.height),
                    target.width,
                    target.height,
                )
                if (fitted.width != bitmap.width || fitted.height != bitmap.height) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, fitted.width, fitted.height, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    bitmap = scaled
                }
            }

            val name = ensureExtension(outputName, recipe.format)
            val file = DocumentStore.reserve(context, name)
            file.outputStream().use { output ->
                bitmap.compress(compressFormat(recipe.format), recipe.quality.coerceIn(1, 100), output)
            }
            val finalSize = ImageSize(bitmap.width, bitmap.height)
            bitmap.recycle()

            Result.Success(file, finalSize, file.length())
        }.getOrElse { error ->
            Result.Failure(error.message ?: "That image could not be processed")
        }
    }

    private inline fun transform(bitmap: Bitmap, block: Matrix.() -> Unit): Bitmap {
        val matrix = Matrix().apply(block)
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    /** The largest power of two that still leaves the image above the target. */
    private fun sampleSizeFor(source: ImageSize, target: ImageSize): Int {
        if (target.width <= 0 || target.height <= 0) return 1
        var sample = 1
        while (
            source.width / (sample * 2) >= target.width &&
            source.height / (sample * 2) >= target.height
        ) {
            sample *= 2
        }
        return sample
    }

    private fun exifRotation(file: File): Int = runCatching {
        when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun compressFormat(format: ImageFormat): Bitmap.CompressFormat = when (format) {
        ImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
        ImageFormat.Png -> Bitmap.CompressFormat.PNG
        ImageFormat.Webp -> Bitmap.CompressFormat.WEBP
    }

    private fun ensureExtension(name: String, format: ImageFormat): String {
        val cleaned = FileFormat.sanitise(name, fallback = "image")
        return if (FileFormat.extension(cleaned) == format.extension) {
            cleaned
        } else {
            "${FileFormat.baseName(cleaned)}.${format.extension}"
        }
    }
}
