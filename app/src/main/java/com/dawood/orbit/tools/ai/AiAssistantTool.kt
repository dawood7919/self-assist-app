package com.dawood.orbit.tools.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.settings.AiSettings
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch

private const val SYSTEM_PROMPT =
    "You are a concise assistant inside a personal productivity app used by a civil engineer. " +
        "Answer directly and briefly. When a calculation is involved, show the working."

private val STARTERS = listOf(
    "Explain this in plain English",
    "Draft a short email about",
    "What could go wrong with",
    "Summarise these notes",
)

/**
 * AI Assistant — a conversation, using the user's own API key.
 *
 * There is no key bundled in the app and no proxy in between: the request goes
 * from this device to the API with a key the user entered, and nothing is sent
 * at all until they enter one.
 */
@Composable
fun AiAssistantTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val settings = remember(context) { AiSettings.get(context) }
    val notes = remember(context) { NotesRepository.get(context) }
    val apiKey by settings.apiKey.collectAsStateWithLifecycle()
    val model by settings.model.collectAsStateWithLifecycle()

    val messages = remember { mutableStateListOf<AiMessage>() }
    var draft by remember { mutableStateOf("") }
    var keyDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val configured = apiKey.isNotBlank()

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        messages += AiMessage(AiMessage.ROLE_USER, text)
        draft = ""
        error = null

        scope.launch {
            busy = true
            when (val outcome = AiClient.send(context, messages.toList(), SYSTEM_PROMPT)) {
                is AiClient.Result.Success -> {
                    messages += AiMessage(AiMessage.ROLE_ASSISTANT, outcome.text)
                    status = "${outcome.inputTokens} in · ${outcome.outputTokens} out"
                }
                is AiClient.Result.Failure -> {
                    error = outcome.message
                    // The question is put back so it is not lost with the error.
                    messages.removeAt(messages.lastIndex)
                    draft = text
                }
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (configured) model else "Needs your API key",
        menuContent = { dismiss ->
            if (messages.isNotEmpty()) {
                OrbitMenuItem(
                    text = "Save to Notebook",
                    onClick = {
                        dismiss()
                        notes.create(
                            body = messages.joinToString("\n\n") {
                                (if (it.isUser) "You: " else "Assistant: ") + it.content
                            },
                        )
                        status = "Saved to Notebook"
                    },
                    icon = OrbitIcons.Notes,
                )
                OrbitMenuItem(
                    text = "Clear the conversation",
                    onClick = {
                        dismiss()
                        messages.clear()
                        error = null
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        settingsContent = {
            OrbitText("API key", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Your own Anthropic key, from console.anthropic.com. It is kept in this app's " +
                    "private storage on this device and sent only to the API.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
            OrbitTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = "Key",
                placeholder = if (configured) settings.maskedKey() else "sk-ant-…",
                leadingIcon = OrbitIcons.Lock,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitButton(
                    text = "Save key",
                    onClick = {
                        settings.setApiKey(keyDraft)
                        keyDraft = ""
                        error = null
                        status = "Key saved"
                    },
                    size = OrbitButtonSize.Small,
                    enabled = keyDraft.isNotBlank(),
                )
                if (configured) {
                    OrbitButton(
                        text = "Remove key",
                        onClick = {
                            settings.clear()
                            status = "Key removed"
                        },
                        variant = OrbitButtonVariant.Ghost,
                        size = OrbitButtonSize.Small,
                    )
                }
            }
            OrbitText("Model", style = OrbitTheme.typography.h4)
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                AiSettings.MODELS.forEach { candidate ->
                    OrbitChip(
                        text = candidate.removePrefix("claude-"),
                        selected = model == candidate,
                        onClick = { settings.setModel(candidate) },
                    )
                }
            }
        },
        bottomBar = {
            OrbitTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = if (configured) "Ask anything" else "Add your API key first",
                modifier = Modifier.weight(1f),
                enabled = configured && !busy,
            )
            OrbitButton(
                text = "Send",
                onClick = ::send,
                leadingIcon = OrbitIcons.Send,
                enabled = configured && !busy && draft.isNotBlank(),
                loading = busy,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    if (!configured) {
                        OrbitEmptyState(
                            title = "Add your API key",
                            description = "This tool talks to the Anthropic API with a key you provide. " +
                                "There is no shared key in the app, so nothing is sent anywhere until " +
                                "you add one. Open the settings from the top bar.",
                            icon = OrbitIcons.Lock,
                        )
                    } else if (messages.isEmpty()) {
                        OrbitEmptyState(
                            title = "Ask anything",
                            description = "The conversation stays on this device and is not saved " +
                                "between visits unless you send it to the Notebook.",
                            icon = OrbitIcons.Ai,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                            STARTERS.take(2).forEach { starter ->
                                OrbitChip(
                                    text = starter,
                                    selected = false,
                                    onClick = { draft = "$starter " },
                                )
                            }
                        }
                    }

                    messages.forEach { message ->
                        MessageBubble(
                            message = message,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                status = "Copied"
                            },
                        )
                    }

                    if (busy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitSpinner()
                            OrbitText(
                                text = "Thinking",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = "That request failed",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                            compact = true,
                        )
                    }

                    status?.let { message ->
                        ToolStatusLine(text = message)
                    }

                    ToolFooter(
                        text = "Requests go straight from this device to the API using your key. " +
                            "The key is stored in the app's private preferences, which other apps " +
                            "cannot read, but it is not encrypted at rest.",
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AiMessage, onCopy: () -> Unit) {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(OrbitTheme.radius.shapeLg)
                .background(if (message.isUser) colors.accentSubtle else colors.surface)
                .padding(OrbitTheme.spacing.md),
        ) {
            OrbitText(
                text = message.content,
                style = OrbitTheme.typography.body,
                color = if (message.isUser) colors.accent else colors.textPrimary,
            )
        }
        if (!message.isUser) {
            OrbitButton(
                text = "Copy",
                onClick = onCopy,
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Copy,
            )
        }
    }
}
