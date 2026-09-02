package com.dawood.orbit.tools.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch

/**
 * Scan to Text — reads the words out of a photo or a screenshot.
 *
 * Recognition runs on the device with a bundled model, so nothing is uploaded
 * and it works with no network at all. There is no camera preview: a picture
 * taken a moment ago in the camera app reads just as well, and this way the
 * tool needs no camera permission.
 */
@Composable
fun ScanToTextTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val notes = remember(context) { NotesRepository.get(context) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<OcrResult?>(null) }
    var reflow by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            result = null
            val name = DocumentStore.displayName(context, uri) ?: "image"
            val copied = DocumentStore.copyIn(context, uri, fallbackName = name)
            // A very large photo is downsampled first: the recogniser gains
            // nothing from forty megapixels and the bitmap has to fit in memory.
            val bitmap = copied?.let { file ->
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
                    var sample = 1
                    while (longEdge / (sample * 2) >= 2400) sample *= 2
                    BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }.getOrNull()
            }

            when {
                bitmap == null -> error = "That file could not be read as an image"
                else -> {
                    preview = bitmap
                    sourceName = name
                    val read = OcrEngine.read(bitmap)
                    when {
                        read == null -> error = "The recogniser could not run on that image"
                        read.isEmpty -> error = "No text found. A straighter, sharper, better-lit " +
                            "picture usually helps more than a bigger one."
                        else -> {
                            result = read
                            status = "${read.wordCount} words in ${read.lineCount} lines"
                        }
                    }
                }
            }
            busy = false
        }
    }
    val imageTypes = remember { arrayOf("image/*") }

    val shownText = result?.let { if (reflow) OcrEngine.reflow(it.text) else it.text }.orEmpty()

    fun saveAsText() {
        if (shownText.isBlank()) return
        val file = DocumentStore.reserve(
            context,
            "${FileFormat.baseName(sourceName.ifBlank { "scan" })} — text.txt",
        )
        runCatching { file.writeText(shownText) }
        status = "Saved ${file.name}"
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = result?.let { "${it.wordCount} words" } ?: "No image yet",
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Choose an image",
                onClick = { dismiss(); picker.launch(imageTypes) },
                icon = OrbitIcons.ImageFile,
            )
            if (shownText.isNotBlank()) {
                OrbitMenuItem(
                    text = "Save to Notebook",
                    onClick = {
                        dismiss()
                        notes.create(body = shownText)
                        status = "Saved to Notebook"
                    },
                    icon = OrbitIcons.Notes,
                )
                OrbitMenuItem("Save as a text file", { dismiss(); saveAsText() }, icon = OrbitIcons.Save)
            }
        },
        settingsContent = if (result != null) {
            {
                OrbitSettingRow(
                    title = "Join wrapped lines",
                    description = "Recognition returns one line per visual row. Joining them turns a " +
                        "column of fragments back into paragraphs.",
                    trailing = {
                        OrbitSwitch(checked = reflow, onCheckedChange = { reflow = it })
                    },
                )
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: "Pick a photo or a screenshot",
                    modifier = Modifier.weight(1f),
                )
                if (shownText.isBlank()) {
                    OrbitButton(
                        text = "Choose",
                        onClick = { picker.launch(imageTypes) },
                        leadingIcon = OrbitIcons.ImageFile,
                        enabled = !busy,
                    )
                } else {
                    OrbitButton(
                        text = "Copy",
                        onClick = {
                            clipboard.setText(AnnotatedString(shownText))
                            status = "Copied"
                        },
                        leadingIcon = OrbitIcons.Copy,
                    )
                }
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    if (preview == null) {
                        FileDropZone(
                            onPickFiles = { picker.launch(imageTypes) },
                            title = "Choose a picture with text in it",
                            description = "A photo of a page, a whiteboard or a label, or a screenshot.",
                            actionLabel = "Choose an image",
                            hint = "Recognition runs on this device — nothing is uploaded",
                            enabled = !busy,
                        )
                    } else {
                        ToolWorkspace(label = "Image") {
                            preview?.let { bitmap ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "The picture being read",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = OrbitTheme.sizes.previewMaxHeight)
                                            .clip(OrbitTheme.radius.shapeMd),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                            OrbitButton(
                                text = "Choose another",
                                onClick = { picker.launch(imageTypes) },
                                variant = OrbitButtonVariant.Secondary,
                                leadingIcon = OrbitIcons.ImageFile,
                                enabled = !busy,
                            )
                        }
                    }

                    if (busy) {
                        FileProgress(label = "Reading the picture", progress = null)
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = "Nothing to read",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                            compact = true,
                        )
                    }

                    result?.let { read ->
                        ToolWorkspace(label = "Text") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitBadge("${read.wordCount} words", tone = OrbitTone.Accent)
                                OrbitBadge("${read.blockCount} blocks", tone = OrbitTone.Neutral)
                            }
                            OrbitText(text = shownText, style = OrbitTheme.typography.body)
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Copy",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(shownText))
                                        status = "Copied"
                                    },
                                    leadingIcon = OrbitIcons.Copy,
                                )
                                OrbitButton(
                                    text = "Save to Notebook",
                                    onClick = {
                                        notes.create(body = shownText)
                                        status = "Saved to Notebook"
                                    },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Notes,
                                )
                                OrbitButton(
                                    text = "Save as text",
                                    onClick = ::saveAsText,
                                    variant = OrbitButtonVariant.Ghost,
                                    leadingIcon = OrbitIcons.Save,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "Recognition covers Latin script and runs entirely on this device with a " +
                            "model bundled in the app, so it works with no network. Handwriting and " +
                            "heavily skewed photos are recognised much less reliably than printed text.",
                    )
                }
            }
        }
    }
}
