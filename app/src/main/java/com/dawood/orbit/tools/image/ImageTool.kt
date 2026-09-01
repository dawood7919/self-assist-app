package com.dawood.orbit.tools.image

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileItem
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch

private val SIZE_PRESETS = listOf(
    "Original" to null,
    "4K" to 3840,
    "1080p" to 1920,
    "720p" to 1280,
    "Web" to 800,
)

private val CROP_PRESETS = listOf<Pair<String, Pair<Int, Int>?>>(
    "Free" to null,
    "1:1" to (1 to 1),
    "4:3" to (4 to 3),
    "16:9" to (16 to 9),
    "3:4" to (3 to 4),
    "9:16" to (9 to 16),
)

/**
 * Image Tools — resize, crop to a ratio, rotate and change format.
 *
 * Every control changes the predicted output size shown underneath, so the
 * effect of a choice is visible before the work is done rather than after.
 */
@Composable
fun ImageTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<ImageEngine.Source?>(null) }
    var sizeIndex by remember { mutableStateOf(0) }
    var cropIndex by remember { mutableStateOf(0) }
    var rotation by remember { mutableStateOf(0) }
    var formatIndex by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(88) }
    var outputName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImageEngine.Result.Success?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val format = ImageFormat.entries[formatIndex]

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            result = null
            val inspected = ImageEngine.inspect(context, uri)
            if (inspected == null) {
                error = "That file could not be read as an image"
            } else {
                source = inspected
                outputName = "${FileFormat.baseName(inspected.displayName)} — edited"
                rotation = 0
            }
            busy = false
        }
    }
    val imageTypes = remember { arrayOf("image/*") }
    val pickImage = { picker.launch(imageTypes) }

    val current = source
    val recipe = ImageEngine.Recipe(
        longEdge = SIZE_PRESETS[sizeIndex].second,
        cropRatio = CROP_PRESETS[cropIndex].second,
        rotation = rotation,
        format = format,
        quality = quality,
    )
    val predicted = current?.let { ImageEngine.previewSize(it, recipe) }

    fun run() {
        val chosen = current ?: return
        scope.launch {
            busy = true
            error = null
            when (val outcome = ImageEngine.process(context, chosen, recipe, outputName)) {
                is ImageEngine.Result.Success -> {
                    result = outcome
                    status = "${outcome.size.label} · ${FileFormat.size(outcome.bytes)}"
                }
                is ImageEngine.Result.Failure -> error = outcome.message
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = current?.let { "${it.displayName} · ${it.size.label}" } ?: "No image yet",
        actions = {
            if (current != null) {
                OrbitButton(
                    text = "Rotate",
                    onClick = { rotation = (rotation + 90) % 360 },
                    variant = OrbitButtonVariant.Ghost,
                    leadingIcon = OrbitIcons.Refresh,
                    enabled = !busy,
                )
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Choose an image", { dismiss(); pickImage() }, icon = OrbitIcons.ImageFile)
            if (current != null) {
                OrbitMenuItem(
                    text = "Reset settings",
                    onClick = {
                        dismiss()
                        sizeIndex = 0
                        cropIndex = 0
                        rotation = 0
                        formatIndex = 0
                        quality = 88
                    },
                    icon = OrbitIcons.Refresh,
                )
                OrbitMenuItem(
                    text = "Start over",
                    onClick = {
                        dismiss()
                        source = null
                        result = null
                        error = null
                        status = null
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        settingsContent = if (current != null) {
            {
                OrbitText("Output", style = OrbitTheme.typography.h4)
                OrbitTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = "File name",
                    helperText = "Saved as ${FileFormat.baseName(outputName.ifBlank { "image" })}.${format.extension}",
                )
                if (format.usesQuality) {
                    OrbitText("Quality: $quality", style = OrbitTheme.typography.h4)
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                        listOf(60, 75, 88, 95).forEach { value ->
                            OrbitChip(
                                text = "$value",
                                selected = quality == value,
                                onClick = { quality = value },
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact && current != null) {
            {
                ToolStatusLine(
                    text = status ?: predicted?.label.orEmpty(),
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Export",
                    onClick = ::run,
                    leadingIcon = OrbitIcons.Save,
                    enabled = !busy,
                    loading = busy,
                )
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
                    if (current == null) {
                        FileDropZone(
                            onPickFiles = pickImage,
                            title = "Choose an image",
                            description = "Resize it, crop it to a ratio, turn it and save it in another format.",
                            actionLabel = "Choose an image",
                            hint = "Nothing is uploaded — the work happens on this device",
                            enabled = !busy,
                        )
                    } else {
                        ToolWorkspace(label = "Image") {
                            FileItem(
                                file = OrbitFile(
                                    id = current.file.absolutePath,
                                    name = current.displayName,
                                    sizeLabel = FileFormat.size(current.bytes),
                                    kind = FileKind.Image,
                                    meta = "${current.size.label} · " +
                                        String.format("%.1f MP", current.size.megapixels),
                                    state = FileState.Selected,
                                ),
                            )

                            OrbitText("Size", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                SIZE_PRESETS.forEachIndexed { index, preset ->
                                    OrbitChip(
                                        text = preset.first,
                                        selected = sizeIndex == index,
                                        onClick = { sizeIndex = index },
                                    )
                                }
                            }

                            OrbitText("Crop", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                CROP_PRESETS.forEachIndexed { index, preset ->
                                    OrbitChip(
                                        text = preset.first,
                                        selected = cropIndex == index,
                                        onClick = { cropIndex = index },
                                    )
                                }
                            }

                            OrbitText("Format", style = OrbitTheme.typography.h4)
                            OrbitSegmentedControl(
                                options = ImageFormat.entries.map { it.label },
                                selectedIndex = formatIndex,
                                onSelect = { formatIndex = it },
                            )

                            predicted?.let { size ->
                                OrbitText(
                                    text = "Result: ${size.label} · about " +
                                        FileFormat.size(ImageMath.estimateBytes(size, format, quality)) +
                                        if (rotation != 0) " · turned $rotation°" else "",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                            if (!format.keepsTransparency) {
                                OrbitText(
                                    text = "JPEG has no transparency, so any transparent areas become black.",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Export",
                                    onClick = ::run,
                                    leadingIcon = OrbitIcons.Save,
                                    enabled = !busy,
                                    loading = busy,
                                )
                                OrbitButton(
                                    text = "Choose another",
                                    onClick = pickImage,
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.ImageFile,
                                    enabled = !busy,
                                )
                            }
                        }
                    }

                    if (busy) {
                        FileProgress(label = "Working", progress = null, detail = predicted?.label)
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = "That did not work",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                        )
                    }

                    result?.let { success ->
                        FileResult(
                            file = DocumentStore.describe(
                                file = success.file,
                                state = FileState.Completed,
                                meta = success.size.label,
                            ),
                            title = "Exported",
                        ) {
                            OrbitButton(
                                text = "Open",
                                onClick = { DocumentStore.open(context, success.file) },
                                leadingIcon = OrbitIcons.OpenExternal,
                            )
                            OrbitButton(
                                text = "Share",
                                onClick = { DocumentStore.share(context, success.file) },
                                variant = OrbitButtonVariant.Secondary,
                                leadingIcon = OrbitIcons.Share,
                            )
                            OrbitButton(
                                text = "Save to Downloads",
                                onClick = {
                                    status = DocumentStore.publish(context, success.file)
                                        ?.let { "Saved to $it" }
                                        ?: "Kept in the app's files — share it to move it out"
                                },
                                variant = OrbitButtonVariant.Ghost,
                                leadingIcon = OrbitIcons.Download,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Images are decoded at a reduced sample size first, so a very large photo " +
                            "can be resized without running the app out of memory. Cropping is centred on " +
                            "the chosen ratio.",
                    )
                }
            }
        }
    }
}
