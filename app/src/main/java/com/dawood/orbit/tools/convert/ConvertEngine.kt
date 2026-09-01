package com.dawood.orbit.tools.convert

import android.content.Context
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.tools.pdf.PdfEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converting between the plain formats: PDF to text, and text or Markdown to
 * PDF.
 *
 * DOCX is deliberately not here. Reading it properly means a full OOXML
 * implementation, and a half-done one that loses tables and images would be
 * worse than an honest gap.
 */
object ConvertEngine {

    sealed interface Result {
        data class Success(val file: File, val note: String) : Result
        data class Failure(val message: String) : Result
    }

    private const val MARGIN = 56f
    private const val FONT_SIZE = 10.5f
    private const val LEADING = 14f

    /** Pulls the text out of a PDF, page by page. */
    suspend fun pdfToText(
        context: Context,
        input: File,
        outputName: String,
    ): Result = withContext(Dispatchers.IO) {
        PdfEngine.ensureReady(context)
        runCatching {
            PDDocument.load(input).use { document ->
                if (document.isEncrypted) {
                    return@runCatching Result.Failure("${input.name} is password protected")
                }
                val stripper = PDFTextStripper().apply {
                    // Reading order rather than the order the operators happen
                    // to appear in, which is often neither.
                    sortByPosition = true
                    lineSeparator = "\n"
                }
                val text = stripper.getText(document)
                if (text.isBlank()) {
                    return@runCatching Result.Failure(
                        "No text in that PDF. It is probably a scan, which needs Scan to Text instead.",
                    )
                }
                val target = DocumentStore.reserve(context, ensureExtension(outputName, "txt"))
                target.writeText(text)
                Result.Success(
                    file = target,
                    note = "${document.numberOfPages} pages · ${wordCount(text)} words",
                )
            }
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not read that PDF") }
    }

    /** Lays plain text or Markdown out onto A4 pages. */
    suspend fun textToPdf(
        context: Context,
        text: String,
        outputName: String,
        treatAsMarkdown: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        PdfEngine.ensureReady(context)
        val content = if (treatAsMarkdown) TextLayout.flattenMarkdown(text) else text
        if (content.isBlank()) return@withContext Result.Failure("There is nothing to convert")

        runCatching {
            val page = PDRectangle.A4
            val charsPerLine = TextLayout.charactersPerLine(page.width, MARGIN, FONT_SIZE)
            val linesPerPage = ((page.height - 2 * MARGIN) / LEADING).toInt()
            val pages = TextLayout.paginate(TextLayout.wrap(content, charsPerLine), linesPerPage)

            val target = DocumentStore.reserve(context, ensureExtension(outputName, "pdf"))
            PDDocument().use { document ->
                val font = PDType1Font.HELVETICA
                pages.forEachIndexed { index, lines ->
                    val pdPage = PDPage(page)
                    document.addPage(pdPage)
                    PDPageContentStream(document, pdPage).use { stream ->
                        stream.beginText()
                        stream.setFont(font, FONT_SIZE)
                        stream.setLeading(LEADING.toDouble())
                        stream.newLineAtOffset(MARGIN, page.height - MARGIN)
                        lines.forEach { line ->
                            // showText refuses characters the font cannot draw,
                            // so anything outside WinAnsi is replaced rather
                            // than failing the whole document.
                            stream.showText(sanitiseForFont(line))
                            stream.newLine()
                        }
                        stream.endText()
                    }
                    onProgress((index + 1f) / pages.size)
                }
                document.save(target)
                Result.Success(
                    file = target,
                    note = "${pages.size} pages · ${wordCount(content)} words",
                )
            }
        }.getOrElse { error -> Result.Failure(error.message ?: "Could not build that PDF") }
    }

    /**
     * Helvetica's built-in encoding covers Latin-1 and nothing else, so
     * anything outside it becomes a question mark instead of an exception.
     */
    private fun sanitiseForFont(line: String): String = buildString {
        line.forEach { character ->
            append(
                when {
                    character.code in 32..126 -> character
                    character.code in 160..255 -> character
                    character == '\t' -> ' '
                    character == '•' -> '-'
                    character == '—' || character == '–' -> '-'
                    character == '“' || character == '”' -> '"'
                    character == '‘' || character == '’' -> '\''
                    else -> '?'
                },
            )
        }
    }

    private fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun ensureExtension(name: String, extension: String): String {
        val cleaned = FileFormat.sanitise(name, fallback = "document")
        return if (FileFormat.extension(cleaned) == extension) {
            cleaned
        } else {
            "${FileFormat.baseName(cleaned)}.$extension"
        }
    }
}
