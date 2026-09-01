package com.dawood.orbit.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * The PDF operations behind the document tools.
 *
 * Everything works on the page objects rather than on pictures of the pages,
 * so merging or splitting a text PDF gives back a text PDF: the output is
 * still selectable and still searchable. Compression is the one place that
 * touches pixels, and only the pixels that were already there.
 */
object PdfEngine {

    /** What a finished operation produced. */
    sealed interface Result {
        data class Success(
            val file: File,
            val pageCount: Int,
            val sizeBytes: Long,
            val note: String? = null,
        ) : Result

        data class Failure(val message: String) : Result
    }

    /** What a document is, before anything is done to it. */
    data class Info(val name: String, val pageCount: Int, val sizeBytes: Long, val encrypted: Boolean)

    private var initialised = false

    /** PDFBox needs its resources unpacked once per process before any use. */
    fun ensureReady(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (!initialised) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialised = true
            }
        }
    }

    suspend fun inspect(context: Context, file: File): Info? = withContext(Dispatchers.IO) {
        ensureReady(context)
        runCatching {
            PDDocument.load(file).use { document ->
                Info(
                    name = file.name,
                    pageCount = document.numberOfPages,
                    sizeBytes = file.length(),
                    encrypted = document.isEncrypted,
                )
            }
        }.getOrNull()
    }

    /**
     * Concatenates [inputs] in order. Bookmarks and form fields come along,
     * which is the reason for using the document model rather than copying
     * pages by hand.
     */
    suspend fun merge(
        context: Context,
        inputs: List<File>,
        outputName: String,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        ensureReady(context)
        if (inputs.size < 2) return@withContext Result.Failure("Pick at least two PDFs to merge")

        runCatching {
            // Every source must be readable before anything is written, so a
            // password-protected file is reported instead of producing a
            // half-merged document.
            inputs.forEach { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) {
                        throw IllegalStateException("${input.name} is password protected")
                    }
                }
            }

            val target = DocumentStore.reserve(context, ensurePdf(outputName))
            val merger = PDFMergerUtility().apply {
                destinationFileName = target.absolutePath
            }
            inputs.forEachIndexed { index, input ->
                merger.addSource(input)
                onProgress((index + 1f) / (inputs.size + 1))
            }
            // Main memory rather than temp files: Android's java.io.tmpdir is
            // not reliably writable by an app, and phone-sized PDFs fit.
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            onProgress(1f)

            val pageCount = PDDocument.load(target).use { it.numberOfPages }
            Result.Success(target, pageCount, target.length())
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not merge those files") }
    }

    /**
     * Writes the chosen [pages] (one-based) of [input] to a new document.
     */
    suspend fun extract(
        context: Context,
        input: File,
        pages: List<Int>,
        outputName: String,
    ): Result = withContext(Dispatchers.IO) {
        ensureReady(context)
        if (pages.isEmpty()) return@withContext Result.Failure("Choose at least one page")

        runCatching {
            PDDocument.load(input).use { source ->
                if (source.isEncrypted) {
                    return@runCatching Result.Failure("${input.name} is password protected")
                }
                val count = source.numberOfPages
                val outOfRange = pages.filter { it < 1 || it > count }
                if (outOfRange.isNotEmpty()) {
                    return@runCatching Result.Failure(
                        "This document has $count pages, so ${outOfRange.first()} does not exist",
                    )
                }
                val target = DocumentStore.reserve(context, ensurePdf(outputName))
                PDDocument().use { output ->
                    pages.forEach { page -> output.addPage(source.getPage(page - 1)) }
                    output.save(target)
                    Result.Success(
                        file = target,
                        pageCount = output.numberOfPages,
                        sizeBytes = target.length(),
                        note = "Pages ${PageRanges.describe(pages)} of ${input.name}",
                    )
                }
            }
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not split that file") }
    }

    /**
     * Stamps [text] diagonally across every page.
     *
     * Drawn in append mode over the existing content, so nothing already on the
     * page is rewritten or lost.
     */
    suspend fun watermark(
        context: Context,
        input: File,
        text: String,
        outputName: String,
        opacity: Float = 0.18f,
        fontSize: Float = 54f,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        ensureReady(context)
        val stamp = text.trim()
        if (stamp.isEmpty()) return@withContext Result.Failure("Type the text to stamp")

        runCatching {
            PDDocument.load(input).use { document ->
                if (document.isEncrypted) {
                    return@runCatching Result.Failure("${input.name} is password protected")
                }
                val font = PDType1Font.HELVETICA_BOLD
                val alpha = opacity.coerceIn(0.02f, 1f)
                val state = PDExtendedGraphicsState().apply {
                    setNonStrokingAlphaConstant(alpha)
                    setStrokingAlphaConstant(alpha)
                }

                document.pages.forEachIndexed { index, page ->
                    val box = page.mediaBox
                    val textWidth = font.getStringWidth(stamp) / 1000f * fontSize
                    val x = max(12f, (box.width - textWidth * 0.72f) / 2f)
                    val y = box.height / 2f - fontSize / 2f

                    PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true,
                    ).use { stream ->
                        stream.setGraphicsStateParameters(state)
                        stream.setNonStrokingColor(120, 120, 120)
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        // A 30° tilt is the conventional look and keeps a long
                        // stamp inside the page on portrait and landscape alike.
                        stream.setTextMatrix(Matrix.getRotateInstance(ROTATION_RADIANS, x, y))
                        stream.showText(stamp)
                        stream.endText()
                    }
                    onProgress((index + 1f) / document.numberOfPages)
                }

                val target = DocumentStore.reserve(context, ensurePdf(outputName))
                document.save(target)
                Result.Success(target, document.numberOfPages, target.length())
            }
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not stamp that file") }
    }

    /**
     * Re-encodes the images inside a PDF as JPEG at [quality], capping them at
     * [maxDimension] pixels on the long edge.
     *
     * Text and vector artwork are untouched, so a text-only document comes back
     * roughly the same size — which the caller reports honestly rather than
     * pretending something happened.
     */
    suspend fun compress(
        context: Context,
        input: File,
        outputName: String,
        quality: Float = 0.6f,
        maxDimension: Int = 1600,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        ensureReady(context)

        runCatching {
            PDDocument.load(input).use { document ->
                if (document.isEncrypted) {
                    return@runCatching Result.Failure("${input.name} is password protected")
                }
                var replaced = 0
                document.pages.forEachIndexed { index, page ->
                    val resources = page.resources ?: return@forEachIndexed
                    for (name in resources.xObjectNames.toList()) {
                        val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObject !is PDImageXObject) continue
                        val smaller = downscale(document, xObject, quality, maxDimension) ?: continue
                        resources.put(name, smaller)
                        replaced++
                    }
                    onProgress((index + 1f) / document.numberOfPages)
                }

                val target = DocumentStore.reserve(context, ensurePdf(outputName))
                document.save(target)
                val before = input.length()
                val after = target.length()
                val note = when {
                    replaced == 0 ->
                        "No images to compress — this document is text, so the size barely moves"
                    after >= before ->
                        "Already well compressed: the result is not smaller, so keep the original"
                    else -> {
                        val saved = ((before - after) * 100 / before).toInt()
                        "$saved% smaller — ${FileFormat.size(before)} down to ${FileFormat.size(after)}"
                    }
                }
                Result.Success(target, document.numberOfPages, after, note)
            }
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not compress that file") }
    }

    /**
     * Returns a JPEG-encoded replacement for [image], or null when re-encoding
     * would not be an improvement.
     */
    private fun downscale(
        document: PDDocument,
        image: PDImageXObject,
        quality: Float,
        maxDimension: Int,
    ): PDImageXObject? = runCatching {
        val original: Bitmap = image.image ?: return null
        val longEdge = max(original.width, original.height)
        if (longEdge <= 8) return null

        val scale = if (longEdge > maxDimension) maxDimension.toFloat() / longEdge else 1f
        val width = max(1, (original.width * scale).toInt())
        val height = max(1, (original.height * scale).toInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, width, height, true)
        } else {
            original
        }
        // An image with transparency loses it in JPEG, so those are left alone.
        if (scaled.hasAlpha()) return null

        val replacement = JPEGFactory.createFromImage(
            document,
            scaled,
            min(1f, max(0.1f, quality)),
        )
        if (scaled !== original) scaled.recycle()
        replacement
    }.getOrNull()

    /** Keeps callers from producing a "report" with no extension. */
    private fun ensurePdf(name: String): String {
        val cleaned = FileFormat.sanitise(name, fallback = "document")
        return if (FileFormat.extension(cleaned) == "pdf") cleaned else "$cleaned.pdf"
    }

    /** 30°, the conventional watermark tilt. */
    private val ROTATION_RADIANS = Math.toRadians(30.0)
}
