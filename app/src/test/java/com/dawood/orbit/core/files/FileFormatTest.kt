package com.dawood.orbit.core.files

import com.dawood.orbit.tools.file.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFormatTest {

    @Test
    fun `bytes stay bytes below a kilobyte`() {
        assertEquals("512 B", FileFormat.size(512))
    }

    @Test
    fun `kilobytes and megabytes get one decimal`() {
        assertEquals("1.0 KB", FileFormat.size(1024))
        assertEquals("1.0 MB", FileFormat.size(1024L * 1024))
        assertEquals("4.2 MB", FileFormat.size((4.2 * 1024 * 1024).toLong()))
    }

    @Test
    fun `gigabytes get two decimals`() {
        assertEquals("1.50 GB", FileFormat.size((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `an unknown size is a dash rather than a lie`() {
        assertEquals("—", FileFormat.size(-1))
    }

    @Test
    fun `extension is the part after the last dot`() {
        assertEquals("pdf", FileFormat.extension("report.final.PDF"))
        assertEquals("", FileFormat.extension("README"))
        assertEquals("", FileFormat.extension("trailing."))
        assertEquals("", FileFormat.extension(".hidden"))
    }

    @Test
    fun `base name drops the extension only`() {
        assertEquals("report.final", FileFormat.baseName("report.final.pdf"))
        assertEquals("README", FileFormat.baseName("README"))
    }

    @Test
    fun `kind follows the extension`() {
        assertEquals(FileKind.Pdf, FileFormat.kindOf("a.pdf"))
        assertEquals(FileKind.Image, FileFormat.kindOf("a.JPEG"))
        assertEquals(FileKind.Video, FileFormat.kindOf("a.mkv"))
        assertEquals(FileKind.Audio, FileFormat.kindOf("a.m4a"))
        assertEquals(FileKind.Spreadsheet, FileFormat.kindOf("a.xlsx"))
        assertEquals(FileKind.Archive, FileFormat.kindOf("a.zip"))
        assertEquals(FileKind.Other, FileFormat.kindOf("a.qqq"))
    }

    @Test
    fun `mime types cover what the tools produce`() {
        assertEquals("application/pdf", FileFormat.mimeType("a.pdf"))
        assertEquals("image/jpeg", FileFormat.mimeType("a.jpg"))
        assertEquals("audio/mp4", FileFormat.mimeType("a.m4a"))
        assertEquals("application/octet-stream", FileFormat.mimeType("a.qqq"))
    }

    @Test
    fun `sanitise removes path separators and control characters`() {
        assertEquals("north tower rev C", FileFormat.sanitise("north/tower: rev C"))
        assertTrue(FileFormat.sanitise("").isNotEmpty())
        assertEquals("file", FileFormat.sanitise("///", fallback = "file"))
    }

    @Test
    fun `sanitise collapses whitespace and trims trailing dots`() {
        assertEquals("a b", FileFormat.sanitise("  a    b.. "))
    }

    @Test
    fun `a free name is returned unchanged`() {
        assertEquals("merged.pdf", FileFormat.uniqueName("merged.pdf", listOf("other.pdf")))
    }

    @Test
    fun `a taken name gets a counter before the extension`() {
        assertEquals("merged (2).pdf", FileFormat.uniqueName("merged.pdf", listOf("merged.pdf")))
        assertEquals(
            "merged (3).pdf",
            FileFormat.uniqueName("merged.pdf", listOf("merged.pdf", "merged (2).pdf")),
        )
    }

    @Test
    fun `the clash check ignores case`() {
        assertEquals("merged (2).pdf", FileFormat.uniqueName("merged.pdf", listOf("MERGED.PDF")))
    }

    @Test
    fun `a name without an extension still gets a counter`() {
        assertEquals("notes (2)", FileFormat.uniqueName("notes", listOf("notes")))
    }
}
