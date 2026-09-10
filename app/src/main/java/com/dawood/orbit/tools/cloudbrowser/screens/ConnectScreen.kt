package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitTabs
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.AuthMethod
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.Protocol
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VPS connection form.
 *
 * Owns every field in [rememberSaveable] so rotation never discards input.
 * Validation runs through [CloudBrowserEngine.validateServer] with per-field
 * errors. [onTestConnection] and [onConnect] are supplied by the tool, which
 * calls the blocking [VpsApi] directly — this screen never touches the API.
 * Only the key *path* is kept; key contents are never read or stored.
 */
@Composable
fun ConnectScreen(
    initial: SavedServer?,
    isDemo: Boolean,
    onTestConnection: (SavedServer) -> Result<Long>,
    onConnect: (SavedServer) -> Result<Unit>,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(initial?.host.orEmpty()) }
    var portText by rememberSaveable { mutableStateOf(initial?.port?.toString() ?: "22") }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var keyPath by rememberSaveable { mutableStateOf(initial?.keyPath.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var authIndex by rememberSaveable { mutableIntStateOf(AuthMethod.entries.indexOf(initial?.authMethod ?: AuthMethod.SshKey)) }
    var protocolIndex by rememberSaveable { mutableIntStateOf(Protocol.entries.indexOf(initial?.protocol ?: Protocol.Ssh)) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var testMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var connectMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val auth = AuthMethod.entries.getOrElse(authIndex) { initial?.authMethod ?: AuthMethod.SshKey }
    val protocol = Protocol.entries.getOrElse(protocolIndex) { initial?.protocol ?: Protocol.Ssh }

    fun draft(): SavedServer = SavedServer(
        id = initial?.id ?: "",
        name = name.trim(),
        host = host.trim(),
        port = CloudBrowserEngine.parsePort(portText) ?: -1,
        username = username.trim(),
        authMethod = auth,
        keyPath = keyPath.trim().ifBlank { null },
        protocol = protocol,
        lastLatencyMs = initial?.lastLatencyMs,
    )

    fun validate(): Boolean {
        val found = CloudBrowserEngine.validateServer(
            name = name.trim(),
            host = host.trim(),
            port = CloudBrowserEngine.parsePort(portText) ?: -1,
            username = username.trim(),
            auth = auth,
            keyPath = keyPath.trim().ifBlank { null },
        ).toMutableMap()
        if (auth == AuthMethod.Password && password.isBlank()) {
            found["password"] = "Enter the server password"
        }
        errors = found
        submitted = true
        return found.isEmpty()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitText(text = "Connect VPS", style = OrbitTheme.typography.h2)
        OrbitText(
            text = "Your phone becomes the remote control; browsing happens on the server.",
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textMuted,
        )

        OrbitText(text = "Authentication", style = OrbitTheme.typography.label)
        OrbitTabs(
            tabs = listOf("SSH key", "Password"),
            selectedIndex = authIndex.coerceIn(0, 1),
            onSelect = { authIndex = it },
        )
        OrbitText(text = "Protocol", style = OrbitTheme.typography.label)
        OrbitTabs(
            tabs = listOf("SSH", "WebSocket", "Secure tunnel"),
            selectedIndex = protocolIndex.coerceIn(0, 2),
            onSelect = { protocolIndex = it },
        )

        OrbitTextField(
            value = name,
            onValueChange = { name = it; if (submitted) validate() },
            label = "Server name",
            placeholder = "My VPS",
            errorText = errors["name"],
        )
        OrbitTextField(
            value = host,
            onValueChange = { host = it; if (submitted) validate() },
            label = "Host / IP address",
            placeholder = "example.com",
            errorText = errors["host"],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit); if (submitted) validate() },
                modifier = Modifier.weight(1f),
                label = "Port",
                placeholder = "22",
                errorText = errors["port"],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OrbitTextField(
                value = username,
                onValueChange = { username = it; if (submitted) validate() },
                modifier = Modifier.weight(2f),
                label = "Username",
                placeholder = "root",
                errorText = errors["username"],
            )
        }
        if (auth == AuthMethod.SshKey) {
            OrbitTextField(
                value = keyPath,
                onValueChange = { keyPath = it; if (submitted) validate() },
                label = "Private key path",
                placeholder = "/storage/keys/id_ed25519",
                helperText = "Only the path is stored, never the key itself.",
                errorText = errors["keyPath"],
            )
        } else {
            OrbitTextField(
                value = password,
                onValueChange = { password = it; if (submitted) validate() },
                label = "Password",
                placeholder = "Server password",
                helperText = "The password is used once to connect and never stored.",
                errorText = errors["password"],
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }

        if (testMessage != null) {
            TestResultCard(message = testMessage!!, isDemo = isDemo)
        }
        if (connectMessage != null) {
            OrbitText(
                text = connectMessage!!,
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.error,
            )
        }

        OrbitButton(
            text = "Test connection",
            onClick = {
                connectMessage = null
                if (!validate()) {
                    testMessage = null
                    return@OrbitButton
                }
                val snapshot = draft()
                scope.launch {
                    val result = withContext(Dispatchers.IO) { onTestConnection(snapshot) }
                    result.fold(
                        onSuccess = { latency ->
                            testMessage = if (isDemo) {
                                "Demo • Server answered in $latency ms (no packets left the phone)"
                            } else {
                                "Server online • $latency ms"
                            }
                        },
                        onFailure = { testMessage = "Unreachable: ${it.message}" },
                    )
                }
            },
            fullWidth = true,
            variant = OrbitButtonVariant.Secondary,
            leadingIcon = OrbitIcons.Sync,
        )
        OrbitButton(
            text = "Connect VPS",
            onClick = {
                if (!validate()) return@OrbitButton
                // Keep on Main: tool handler updates tool state synchronously;
                // FakeVpsApi answers instantly, so no blocking concern.
                onConnect(draft()).fold(
                    onSuccess = { onConnected() },
                    onFailure = { connectMessage = it.message ?: "Could not connect" },
                )
            },
            fullWidth = true,
            size = OrbitButtonSize.Large,
            leadingIcon = OrbitIcons.Link,
        )
    }
}

@Composable
private fun TestResultCard(message: String, isDemo: Boolean) {
    OrbitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitIcon(
                icon = OrbitIcons.Success,
                contentDescription = null,
                tint = OrbitTheme.colors.success,
            )
            Column {
                OrbitText(text = if (isDemo) "Demo result" else "Server online", style = OrbitTheme.typography.h3)
                OrbitText(
                    text = message,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}
