package com.dawood.orbit.tools.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile
import java.io.File

/**
 * A PDF the user has chosen, already copied somewhere the engine can read it.
 *
 * Documents arrive as content:// URIs that other apps may revoke, so every tool
 * takes its own working copy before doing anything, and works from this.
 */
@Immutable
data class PickedPdf(
    val file: File,
    val displayName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val encrypted: Boolean = false,
) {
    val id: String get() = file.absolutePath

    val pageLabel: String
        get() = when {
            encrypted -> "Password protected"
            pageCount == 1 -> "1 page"
            else -> "$pageCount pages"
        }

    fun asOrbitFile(state: FileState = FileState.Selected): OrbitFile = OrbitFile(
        id = id,
        name = displayName,
        sizeLabel = FileFormat.size(sizeBytes),
        kind = FileKind.Pdf,
        meta = pageLabel,
        state = state,
    )
}

/**
 * Opens the system document picker for PDFs.
 *
 * Returns a lambda to call from a button. Multi-select is a parameter rather
 * than two separate helpers so every document tool picks files the same way.
 */
@Composable
fun rememberPdfPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    val types = remember { arrayOf("application/pdf") }
    return { launcher.launch(types) }
}

/** The same, for tools that only ever work on one document at a time. */
@Composable
fun rememberSinglePdfPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onPicked(uri) }
    val types = remember { arrayOf("application/pdf") }
    return { launcher.launch(types) }
}
