package com.dawood.orbit.tools.codes

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Immutable
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The code shapes the tool can write, with what each one is actually for. */
enum class CodeKind(
    val label: String,
    val format: BarcodeFormat,
    val square: Boolean,
    val hint: String,
) {
    Qr("QR", BarcodeFormat.QR_CODE, true, "Anything up to a few thousand characters"),
    DataMatrix("Data Matrix", BarcodeFormat.DATA_MATRIX, true, "Small labels and parts marking"),
    Code128("Code 128", BarcodeFormat.CODE_128, false, "Letters and digits, the usual logistics barcode"),
    Ean13("EAN-13", BarcodeFormat.EAN_13, false, "Exactly 13 digits, retail products"),
}

/** How much damage a QR code can take and still read. */
enum class ErrorCorrection(val label: String, val level: String, val description: String) {
    Low("L", "L", "About 7% recoverable — smallest code"),
    Medium("M", "M", "About 15% recoverable"),
    Quartile("Q", "Q", "About 25% recoverable"),
    High("H", "H", "About 30% recoverable — best for printing on site"),
}

@Immutable
data class ScanResult(val text: String, val format: String)

/**
 * Encoding and decoding codes, without a camera.
 *
 * Reading works on a picked photo rather than a live preview: it needs no
 * camera permission, it works on a screenshot someone sent you, and a photo of
 * a label taken a minute ago decodes exactly as well as a live frame.
 */
object BarcodeEngine {

    sealed interface EncodeResult {
        data class Success(val bitmap: Bitmap) : EncodeResult
        data class Failure(val message: String) : EncodeResult
    }

    /** Checks the content suits the format before the encoder complains. */
    fun validate(kind: CodeKind, content: String): String? {
        val text = content.trim()
        return when {
            text.isEmpty() -> "Type something to encode"
            kind == CodeKind.Ean13 && text.length !in 12..13 ->
                "EAN-13 needs 12 or 13 digits, not ${text.length}"
            kind == CodeKind.Ean13 && !text.all { it.isDigit() } ->
                "EAN-13 holds digits only"
            kind == CodeKind.Qr && text.length > 2900 ->
                "That is too long for a QR code — about 2900 characters is the practical limit"
            else -> null
        }
    }

    suspend fun encode(
        kind: CodeKind,
        content: String,
        sizePx: Int = 720,
        errorCorrection: ErrorCorrection = ErrorCorrection.Medium,
        margin: Int = 1,
    ): EncodeResult = withContext(Dispatchers.Default) {
        validate(kind, content)?.let { return@withContext EncodeResult.Failure(it) }

        runCatching {
            val hints = buildMap<EncodeHintType, Any> {
                put(EncodeHintType.MARGIN, margin)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                if (kind == CodeKind.Qr) {
                    put(EncodeHintType.ERROR_CORRECTION, errorCorrection.level)
                }
            }
            // A linear barcode drawn square is unreadable; it needs to be wide.
            val width = sizePx
            val height = if (kind.square) sizePx else (sizePx * 0.42f).toInt()

            val matrix = MultiFormatWriter().encode(content.trim(), kind.format, width, height, hints)
            val pixels = IntArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                val offset = y * matrix.width
                for (x in 0 until matrix.width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            EncodeResult.Success(bitmap)
        }.getOrElse { error ->
            EncodeResult.Failure(error.message ?: "That content could not be encoded as ${kind.label}")
        }
    }

    /** Reads whatever code is in [bitmap], or null when there is none. */
    suspend fun decode(bitmap: Bitmap): ScanResult? = withContext(Dispatchers.Default) {
        runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.TRY_HARDER to true))
            }
            val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
            ScanResult(text = result.text, format = result.barcodeFormat.name.replace('_', ' '))
        }.getOrNull()
    }
}
