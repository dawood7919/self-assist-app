package com.dawood.orbit.tools.codes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch

private val PRESETS = listOf(
    "Link" to "https://",
    "Wi-Fi" to "WIFI:T:WPA;S:NetworkName;P:password;;",
    "Text" to "",
)

/**
 * QR & Barcodes — write a code, or read one out of a picture.
 *
 * Reading deliberately works on a picked image rather than a live camera. It
 * needs no camera permission, it reads a screenshot somebody sent you, and a
 * photo of a label on a pallet decodes just as well as a live frame would.
 */
@Composable
fun BarcodeTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var modeIndex by remember { mutableStateOf(0) }
    var content by remember { mutableStateOf("https://") }
    var kind by remember { mutableStateOf(CodeKind.Qr) }
    var correctionIndex by remember { mutableStateOf(1) }
    var code by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanned by remember { mutableStateOf<ScanResult?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val correction = ErrorCorrection.entries[correctionIndex]

    // Re-encode whenever anything that shapes the code changes.
    LaunchedEffect(content, kind, correctionIndex, modeIndex) {
        if (modeIndex != 0) return@LaunchedEffect
        when (val outcome = BarcodeEngine.encode(kind, content, errorCorrection = correction)) {
            is BarcodeEngine.EncodeResult.Success -> {
                code = outcome.bitmap
                error = null
            }
            is BarcodeEngine.EncodeResult.Failure -> {
                code = null
                error = outcome.message
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            scanned = null
            val copied = DocumentStore.copyIn(context, uri, fallbackName = "code.png")
            val bitmap = copied?.let {
                runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            }
            when {
                bitmap == null -> error = "That file could not be read as an image"
                else -> {
                    val result = BarcodeEngine.decode(bitmap)
                    if (result == null) {
                        error = "No code found in that image. A sharper, straighter photo usually helps."
                    } else {
                        scanned = result
                        status = "Read a ${result.format} code"
                    }
                }
            }
        }
    }
    val imageTypes = remember { arrayOf("image/*") }

    fun saveCode() {
        val bitmap = code ?: return
        val file = DocumentStore.reserve(context, "${kind.label} code.png")
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        status = "Saved ${file.name}"
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (modeIndex == 0) "Writing a ${kind.label} code" else "Reading a code",
        menuContent = { dismiss ->
            if (modeIndex == 0 && code != null) {
                OrbitMenuItem("Save as PNG", { dismiss(); saveCode() }, icon = OrbitIcons.Save)
            }
            OrbitMenuItem(
                text = "Read a code from an image",
                onClick = { dismiss(); modeIndex = 1; picker.launch(imageTypes) },
                icon = OrbitIcons.ImageFile,
            )
        },
        settingsContent = if (modeIndex == 0 && kind == CodeKind.Qr) {
            {
                OrbitText("Error correction", style = OrbitTheme.typography.h4)
                OrbitSegmentedControl(
                    options = ErrorCorrection.entries.map { it.label },
                    selectedIndex = correctionIndex,
                    onSelect = { correctionIndex = it },
                )
                OrbitText(
                    text = correction.description,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: if (modeIndex == 0) kind.hint else "Pick an image to read",
                    modifier = Modifier.weight(1f),
                )
                if (modeIndex == 0) {
                    OrbitButton(
                        text = "Save",
                        onClick = ::saveCode,
                        leadingIcon = OrbitIcons.Save,
                        enabled = code != null,
                    )
                } else {
                    OrbitButton(
                        text = "Pick image",
                        onClick = { picker.launch(imageTypes) },
                        leadingIcon = OrbitIcons.ImageFile,
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
                    OrbitSegmentedControl(
                        options = listOf("Create", "Read"),
                        selectedIndex = modeIndex,
                        onSelect = {
                            modeIndex = it
                            error = null
                        },
                    )

                    if (modeIndex == 0) {
                        ToolWorkspace(label = "Content") {
                            OrbitTextField(
                                value = content,
                                onValueChange = { content = it },
                                label = "What the code says",
                                placeholder = "https://example.com",
                                singleLine = false,
                                minLines = 2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                                PRESETS.forEach { preset ->
                                    OrbitChip(
                                        text = preset.first,
                                        selected = false,
                                        onClick = { content = preset.second },
                                    )
                                }
                            }
                            OrbitText("Format", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                CodeKind.entries.forEach { candidate ->
                                    OrbitChip(
                                        text = candidate.label,
                                        selected = kind == candidate,
                                        onClick = { kind = candidate },
                                    )
                                }
                            }
                            OrbitText(
                                text = kind.hint,
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }

                        code?.let { bitmap ->
                            ToolWorkspace(label = "Code") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "The generated ${kind.label} code",
                                        modifier = Modifier
                                            .widthIn(max = 320.dp)
                                            .clip(OrbitTheme.radius.shapeMd)
                                            .background(OrbitTheme.colors.surface)
                                            .padding(OrbitTheme.spacing.md),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                    OrbitButton(
                                        text = "Save as PNG",
                                        onClick = ::saveCode,
                                        leadingIcon = OrbitIcons.Save,
                                    )
                                    OrbitButton(
                                        text = "Copy the content",
                                        onClick = {
                                            clipboard.setText(AnnotatedString(content))
                                            status = "Copied"
                                        },
                                        variant = OrbitButtonVariant.Secondary,
                                        leadingIcon = OrbitIcons.Copy,
                                    )
                                }
                            }
                        }
                    } else {
                        ToolWorkspace(label = "Read a code") {
                            OrbitText(
                                text = "Pick a photo or a screenshot with a code in it. There is no " +
                                    "camera preview and no camera permission — the decoder works on the " +
                                    "picture itself.",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary,
                            )
                            OrbitButton(
                                text = "Choose an image",
                                onClick = { picker.launch(imageTypes) },
                                leadingIcon = OrbitIcons.ImageFile,
                            )
                        }

                        scanned?.let { result ->
                            ToolWorkspace(label = "Found") {
                                OrbitBadge(result.format, tone = OrbitTone.Success)
                                OrbitText(result.text, style = OrbitTheme.typography.mono)
                                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                    OrbitButton(
                                        text = "Copy",
                                        onClick = {
                                            clipboard.setText(AnnotatedString(result.text))
                                            status = "Copied"
                                        },
                                        leadingIcon = OrbitIcons.Copy,
                                    )
                                    OrbitButton(
                                        text = "Use as content",
                                        onClick = {
                                            content = result.text
                                            modeIndex = 0
                                        },
                                        variant = OrbitButtonVariant.Secondary,
                                        leadingIcon = OrbitIcons.Swap,
                                    )
                                }
                            }
                        }
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = if (modeIndex == 0) "Cannot encode that" else "Nothing found",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                            compact = true,
                        )
                    }

                    ToolFooter(
                        text = "Codes are generated on this device and never uploaded. A QR code with " +
                            "high error correction survives being printed, folded and rained on, at the " +
                            "cost of being a little denser.",
                    )
                }
            }
        }
    }
}
