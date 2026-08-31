package com.dawood.orbit.tools.file

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons

/**
 * The file model shared by every file-based tool.
 *
 * A PDF merger, an image resizer and a video downloader all describe their work
 * with this one type, which is why they can share a drop zone, a list and a
 * result card without any of them knowing about the others.
 */
@Immutable
data class OrbitFile(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val kind: FileKind,
    val meta: String? = null,
    val state: FileState = FileState.Idle,
    val progress: Float? = null,
    val errorMessage: String? = null,
)

enum class FileKind {
    Pdf, Image, Video, Audio, Document, Spreadsheet, Archive, Other;

    val icon: ImageVector
        get() = when (this) {
            Pdf -> OrbitIcons.Pdf
            Image -> OrbitIcons.ImageFile
            Video -> OrbitIcons.Video
            Audio -> OrbitIcons.Audio
            Document -> OrbitIcons.Notes
            Spreadsheet -> OrbitIcons.Sheet
            Archive -> OrbitIcons.Zip
            Other -> OrbitIcons.File
        }

    val tone: OrbitTone
        get() = when (this) {
            Pdf -> OrbitTone.Info
            Image -> OrbitTone.Accent
            Video -> OrbitTone.Error
            Audio -> OrbitTone.Warning
            Spreadsheet -> OrbitTone.Success
            else -> OrbitTone.Neutral
        }
}

/** The lifecycle a file goes through inside any tool. */
enum class FileState { Idle, Selected, Processing, Completed, Error }
