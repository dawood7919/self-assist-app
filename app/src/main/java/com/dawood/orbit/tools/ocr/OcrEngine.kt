package com.dawood.orbit.tools.ocr

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What was read out of an image. */
@Immutable
data class OcrResult(
    val text: String,
    val blockCount: Int,
    val lineCount: Int,
) {
    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.isNotBlank() }
    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * Reads text out of a picture, on the device.
 *
 * The bundled recognition model is used rather than the Play Services one, so
 * the tool works on a device with no Google services and nothing is uploaded.
 * The cost is a noticeably larger APK, which is the right trade for a tool
 * whose whole premise is that the photo stays on the phone.
 */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognises Latin-script text. Returns null when the recogniser itself
     * failed, and an empty result when it simply found nothing — those are
     * different things and the UI says so differently.
     */
    suspend fun read(bitmap: Bitmap): OcrResult? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.sumOf { it.lines.size }
                continuation.resume(
                    OcrResult(
                        text = visionText.text,
                        blockCount = visionText.textBlocks.size,
                        lineCount = lines,
                    ),
                )
            }
            .addOnFailureListener { continuation.resume(null) }
    }

    /**
     * Joins lines that were wrapped by the page rather than by the writer.
     *
     * OCR returns one line per visual row, so a paragraph comes back as a
     * column of fragments. A line that does not end in sentence punctuation is
     * treated as continuing.
     */
    fun reflow(text: String): String {
        val lines = text.lines().map { it.trim() }
        val out = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (line.isEmpty()) {
                out.append("\n\n")
                return@forEachIndexed
            }
            out.append(line)
            val continues = index < lines.lastIndex &&
                lines[index + 1].isNotEmpty() &&
                !line.endsWith(".") && !line.endsWith("!") && !line.endsWith("?") &&
                !line.endsWith(":") && !line.endsWith(";")
            out.append(if (continues) " " else "\n")
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
