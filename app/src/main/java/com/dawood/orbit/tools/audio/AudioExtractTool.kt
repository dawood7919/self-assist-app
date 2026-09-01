package com.dawood.orbit.tools.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
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

/**
 * Audio Extract — pulls the soundtrack out of a video into its own file.
 */
@Composable
fun AudioExtractTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<AudioExtractor.Source?>(null) }
    var outputName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<AudioExtractor.Result.Success?>(null) }
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
            val inspected = AudioExtractor.inspect(context, uri)
            when {
                inspected == null -> error = "That file could not be read"
                !inspected.hasAudio -> {
                    error = "${inspected.displayName} has no audio track to extract"
                    source = null
                }
                else -> {
                    source = inspected
                    outputName = "${FileFormat.baseName(inspected.displayName)} — audio"
                }
            }
            busy = false
        }
    }
    val videoTypes = remember { arrayOf("video/*") }
    val pickVideo = { picker.launch(videoTypes) }

    val current = source

    fun run() {
        val chosen = current ?: return
        scope.launch {
            busy = true
            error = null
            progress = 0f
            when (
                val outcome = AudioExtractor.extract(
                    context = context,
                    source = chosen,
                    outputName = outputName,
                    onProgress = { progress = it },
                )
            ) {
                is AudioExtractor.Result.Success -> {
                    result = outcome
                    status = "${AudioExtractor.formatDuration(outcome.durationMs)} · " +
                        FileFormat.size(outcome.bytes)
                }
                is AudioExtractor.Result.Failure -> error = outcome.message
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = current?.let { "${it.displayName} · ${it.durationLabel}" } ?: "No video yet",
        menuContent = { dismiss ->
            OrbitMenuItem("Choose a video", { dismiss(); pickVideo() }, icon = OrbitIcons.Video)
            if (current != null) {
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
                    helperText = "Saved as ${FileFormat.baseName(outputName.ifBlank { "audio" })}.m4a",
                )
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact && current != null) {
            {
                ToolStatusLine(text = status ?: current.durationLabel, modifier = Modifier.weight(1f))
                OrbitButton(
                    text = "Extract",
                    onClick = ::run,
                    leadingIcon = OrbitIcons.Audio,
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
                            onPickFiles = pickVideo,
                            title = "Choose a video",
                            description = "Its audio track is copied out as it is, with no re-encoding.",
                            actionLabel = "Choose a video",
                            hint = "Nothing is uploaded — the copy happens on this device",
                            enabled = !busy,
                        )
                    } else {
                        ToolWorkspace(label = "Video") {
                            FileItem(
                                file = OrbitFile(
                                    id = current.file.absolutePath,
                                    name = current.displayName,
                                    sizeLabel = FileFormat.size(current.bytes),
                                    kind = FileKind.Video,
                                    meta = "${current.durationLabel} · ${codecLabel(current.audioCodec)}",
                                    state = FileState.Selected,
                                ),
                            )
                            OrbitText(
                                text = "The track is copied sample for sample, so nothing is lost and " +
                                    "nothing is re-compressed. It lands in an .m4a container.",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Extract audio",
                                    onClick = ::run,
                                    leadingIcon = OrbitIcons.Audio,
                                    enabled = !busy,
                                    loading = busy,
                                )
                                OrbitButton(
                                    text = "Choose another",
                                    onClick = pickVideo,
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Video,
                                    enabled = !busy,
                                )
                            }
                        }
                    }

                    if (busy) {
                        FileProgress(
                            label = "Copying the audio track",
                            progress = if (progress > 0f) progress else null,
                            detail = current?.durationLabel,
                        )
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
                                meta = AudioExtractor.formatDuration(success.durationMs),
                            ),
                            title = "Extracted",
                        ) {
                            OrbitButton(
                                text = "Play",
                                onClick = { DocumentStore.open(context, success.file) },
                                leadingIcon = OrbitIcons.Play,
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
                        text = "Because the samples are copied rather than converted, the result keeps " +
                            "the original codec — usually AAC. There is no MP3 export, which would mean " +
                            "re-encoding and losing quality for no gain.",
                    )
                }
            }
        }
    }
}

private fun codecLabel(mime: String?): String = when {
    mime == null -> "No audio"
    mime.contains("mp4a") || mime.contains("aac") -> "AAC"
    mime.contains("opus") -> "Opus"
    mime.contains("vorbis") -> "Vorbis"
    mime.contains("mpeg") -> "MP3"
    mime.contains("flac") -> "FLAC"
    else -> mime.substringAfter('/').uppercase()
}
