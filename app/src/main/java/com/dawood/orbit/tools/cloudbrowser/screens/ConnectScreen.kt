package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dawood.orbit.tools.cloudbrowser.AuthMethod
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudField
import com.dawood.orbit.tools.cloudbrowser.CloudLabel
import com.dawood.orbit.tools.cloudbrowser.CloudOutlineButton
import com.dawood.orbit.tools.cloudbrowser.CloudPill
import com.dawood.orbit.tools.cloudbrowser.CloudPrimaryButton
import com.dawood.orbit.tools.cloudbrowser.CloudSmall
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.Protocol
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VPS connection form.
 *
 * Exact visual copy of mockup slot 2. Owns every field in [rememberSaveable]
 * so rotation never discards input — except the password, which stays in a
 * RAM-only [remember] so it is never saved, logged, or persisted. Validation
 * runs through [CloudBrowserEngine.validateServer] with per-field errors.
 * [onTestConnection] and [onConnect] are supplied by the tool, which calls
 * the blocking [VpsApi] directly — this screen never touches the API. Only
 * the key *path* is kept; key contents are never read or stored. The
 * password is handed to [onConnect] as a plain String; the tool copies it
 * into EphemeralCredentials and zeroes its own copy, and this screen clears
 * the field after a successful connect.
 */
@Composable
fun ConnectScreen(
    initial: SavedServer?,
    isDemo: Boolean,
    onTestConnection: (SavedServer, String) -> Result<Long>,
    onConnect: (SavedServer, String) -> Result<Unit>,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(initial?.host.orEmpty()) }
    var portText by rememberSaveable { mutableStateOf(initial?.port?.toString() ?: "22") }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var keyPath by rememberSaveable { mutableStateOf(initial?.keyPath.orEmpty()) }
    // Stable id for a brand-new server so a Test Connection and the later
    // Connect call share one credentials slot (the RAM-only password is keyed
    // by server id).
    val newServerId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var password by remember { mutableStateOf("") }
    var authIndex by rememberSaveable { mutableIntStateOf(AuthMethod.entries.indexOf(initial?.authMethod ?: AuthMethod.SshKey)) }
    var protocolIndex by rememberSaveable { mutableIntStateOf(Protocol.entries.indexOf(initial?.protocol ?: Protocol.Ssh)) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var testMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var connectMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initial?.id) {
        name = initial?.name.orEmpty()
        host = initial?.host.orEmpty()
        portText = initial?.port?.toString() ?: "22"
        username = initial?.username.orEmpty()
        keyPath = initial?.keyPath.orEmpty()
        authIndex = AuthMethod.entries.indexOf(initial?.authMethod ?: AuthMethod.SshKey)
        protocolIndex = Protocol.entries.indexOf(initial?.protocol ?: Protocol.Ssh)
    }

    val auth = AuthMethod.entries.getOrElse(authIndex) { initial?.authMethod ?: AuthMethod.SshKey }
    val protocol = Protocol.entries.getOrElse(protocolIndex) { initial?.protocol ?: Protocol.Ssh }

    fun draft(): SavedServer = SavedServer(
        id = initial?.id ?: newServerId,
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
        verticalArrangement = Arrangement.spacedBy(CloudColors.CardPadding),
    ) {
        CloudLabel(text = "Server Name")
        CloudField(
            value = name,
            onValueChange = { name = it; if (submitted) validate() },
            placeholder = "My VPS",
            errorText = errors["name"],
        )

        CloudLabel(text = "Host/IP")
        CloudField(
            value = host,
            onValueChange = { host = it; if (submitted) validate() },
            placeholder = "example.com",
            errorText = errors["host"],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            Column(Modifier.weight(1f)) {
                CloudLabel(text = "Port")
                CloudField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit); if (submitted) validate() },
                    placeholder = "22",
                    errorText = errors["port"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Column(Modifier.weight(2f)) {
                CloudLabel(text = "Username")
                CloudField(
                    value = username,
                    onValueChange = { username = it; if (submitted) validate() },
                    placeholder = "root",
                    errorText = errors["username"],
                )
            }
        }

        CloudLabel(text = "Authentication Method")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            CloudPill(
                text = "SSH Key",
                selected = auth == AuthMethod.SshKey,
                onSelect = { authIndex = AuthMethod.entries.indexOf(AuthMethod.SshKey) },
                modifier = Modifier.weight(1f),
            )
            CloudPill(
                text = "Password",
                selected = auth == AuthMethod.Password,
                onSelect = { authIndex = AuthMethod.entries.indexOf(AuthMethod.Password) },
                modifier = Modifier.weight(1f),
            )
        }

        if (auth == AuthMethod.SshKey) {
            CloudLabel(text = "SSH Key Path")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            ) {
                CloudField(
                    value = keyPath,
                    onValueChange = { keyPath = it; if (submitted) validate() },
                    placeholder = "/storage/keys/id_ed25519",
                    errorText = errors["keyPath"],
                    modifier = Modifier.weight(1f),
                )
                CloudOutlineButton(
                    text = "📁",
                    onClick = {
                        // No file picker on this backend: fill the sample path
                        // when empty, clear it when one is already set.
                        keyPath = if (keyPath.isBlank()) "/storage/keys/id_ed25519" else ""
                        if (submitted) validate()
                    },
                    modifier = Modifier.weight(0.6f),
                )
            }
            CloudSmall(text = "Only the path is stored, never the key itself.")
        } else {
            CloudLabel(text = "Password")
            CloudField(
                value = password,
                onValueChange = { password = it; if (submitted) validate() },
                placeholder = "Server password",
                errorText = errors["password"],
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            CloudSmall(text = "The password is used once to connect and never stored.")
        }

        CloudLabel(text = "Connection Protocol")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            CloudPill(
                text = "SSH",
                selected = protocol == Protocol.Ssh,
                onSelect = { protocolIndex = Protocol.entries.indexOf(Protocol.Ssh) },
                modifier = Modifier.weight(1f),
            )
            CloudPill(
                text = "WebSocket",
                selected = protocol == Protocol.WebSocket,
                onSelect = { protocolIndex = Protocol.entries.indexOf(Protocol.WebSocket) },
                modifier = Modifier.weight(1f),
            )
            CloudPill(
                text = "Secure Tunnel",
                selected = protocol == Protocol.SecureTunnel,
                onSelect = { protocolIndex = Protocol.entries.indexOf(Protocol.SecureTunnel) },
                modifier = Modifier.weight(1f),
            )
        }

        if (testMessage != null) {
            TestResultCard(message = testMessage!!, isDemo = isDemo)
        }
        if (connectMessage != null) {
            BasicText(
                text = connectMessage!!,
                style = TextStyle(
                    color = CloudColors.Red,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        }

        CloudOutlineButton(
            text = "Test Connection",
            onClick = {
                connectMessage = null
                if (!validate()) {
                    testMessage = null
                    return@CloudOutlineButton
                }
                val snapshot = draft()
                scope.launch {
                    val result = withContext(Dispatchers.IO) { onTestConnection(snapshot, password) }
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
        )
        CloudPrimaryButton(
            text = "Connect VPS",
            onClick = {
                if (!validate()) return@CloudPrimaryButton
                val snapshot = draft()
                scope.launch {
                    val result = withContext(Dispatchers.IO) { onConnect(snapshot, password) }
                    result.fold(
                        onSuccess = {
                            password = ""
                            onConnected()
                        },
                        onFailure = { connectMessage = it.message ?: "Could not connect" },
                    )
                }
            },
        )
    }
}

@Composable
private fun TestResultCard(message: String, isDemo: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        BasicText(
            text = if (message.startsWith("Unreachable")) "✕" else "✓",
            style = TextStyle(
                color = if (message.startsWith("Unreachable")) CloudColors.Red else CloudColors.Green,
                fontSize = CloudColors.BodySize,
            ),
        )
        Column {
            CloudSmall(
                text = if (isDemo) "Demo result" else "Server online",
            )
            CloudSmall(text = message)
        }
    }
}
