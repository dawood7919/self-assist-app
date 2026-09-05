package com.dawood.orbit.tools.cloudbrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolShell

/**
 * Cloud Browser — remote Chromium running on the VPS, streamed into a WebView.
 *
 * The phone only displays the Selkies/noVNC-style session and forwards touch
 * and keyboard. Page rendering, networking and CPU all stay on the server.
 */
@Composable
fun CloudBrowserTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { CloudBrowserSettings.get(context) }

    var showSettings by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ConnectionStatus.Connecting) }
    var statusMessage by remember { mutableStateOf("Connecting to VPS…") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        actions = {
            OrbitIconButton(
                icon = OrbitIcons.Refresh,
                contentDescription = "Reconnect",
                onClick = {
                    status = ConnectionStatus.Connecting
                    statusMessage = "Reconnecting…"
                    reloadToken++
                },
            )
            OrbitIconButton(
                icon = OrbitIcons.Settings,
                contentDescription = "Server settings",
                onClick = { showSettings = !showSettings },
            )
        },
    ) {
        Column(Modifier = Modifier.fillMaxSize()) {
            StatusBar(status = status, message = statusMessage)

            if (showSettings) {
                ServerSettingsPanel(
                    settings = settings,
                    onSave = {
                        showSettings = false
                        status = ConnectionStatus.Connecting
                        statusMessage = "Connecting to VPS…"
                        reloadToken++
                    },
                    onCancel = { showSettings = false },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(OrbitTheme.colors.backgroundElevated),
            ) {
                RemoteBrowserView(
                    settings = settings,
                    reloadToken = reloadToken,
                    onStatus = { s, msg ->
                        status = s
                        statusMessage = msg
                    },
                    onWebViewReady = { webView = it },
                )

                if (status == ConnectionStatus.Error) {
                    ErrorOverlay(
                        message = statusMessage,
                        onRetry = {
                            status = ConnectionStatus.Connecting
                            statusMessage = "Reconnecting…"
                            reloadToken++
                        },
                    )
                }
            }
        }
    }
}

enum class ConnectionStatus { Connecting, Connected, Error }

@Composable
private fun StatusBar(status: ConnectionStatus, message: String) {
    val color = when (status) {
        ConnectionStatus.Connected -> OrbitTheme.colors.success
        ConnectionStatus.Connecting -> OrbitTheme.colors.warning
        ConnectionStatus.Error -> OrbitTheme.colors.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OrbitTheme.colors.backgroundBase)
            .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, OrbitTheme.radius.pill),
        )
        OrbitText(
            text = message,
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServerSettingsPanel(
    settings: CloudBrowserSettings,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var host by remember { mutableStateOf(settings.host) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var user by remember { mutableStateOf(settings.username) }
    var pass by remember { mutableStateOf(settings.password) }

    OrbitCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(OrbitTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(OrbitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitText("VPS connection", style = OrbitTheme.typography.h4)
            OrbitText(
                "Chromium runs on the server. The phone only streams the screen.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            SettingField(label = "Host / IP", value = host, onValueChange = { host = it })
            SettingField(
                label = "Port",
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                keyboardType = KeyboardType.Number,
            )
            SettingField(label = "Username", value = user, onValueChange = { user = it })
            SettingField(
                label = "Password",
                value = pass,
                onValueChange = { pass = it },
                isPassword = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OrbitButton(
                    text = "Cancel",
                    onClick = onCancel,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Save & connect",
                    onClick = {
                        settings.host = host
                        settings.port = port.toIntOrNull() ?: CloudBrowserSettings.DEFAULT_PORT
                        settings.username = user
                        settings.password = pass
                        onSave()
                    },
                    size = OrbitButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OrbitText(label, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = OrbitTheme.typography.body.copy(color = OrbitTheme.colors.textPrimary),
            cursorBrush = SolidColor(OrbitTheme.colors.accent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .background(OrbitTheme.colors.backgroundElevated, OrbitTheme.radius.md)
                .padding(OrbitTheme.spacing.sm),
        )
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.backgroundBase.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            modifier = Modifier.padding(OrbitTheme.spacing.lg),
        ) {
            OrbitIcon(
                icon = OrbitIcons.Warning,
                contentDescription = null,
                size = 40.dp,
                tint = OrbitTheme.colors.error,
            )
            OrbitText("Unable to reach VPS", style = OrbitTheme.typography.h3)
            OrbitText(
                message,
                style = OrbitTheme.typography.body,
                color = OrbitTheme.colors.textMuted,
            )
            OrbitButton(text = "Retry", onClick = onRetry)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RemoteBrowserView(
    settings: CloudBrowserSettings,
    reloadToken: Int,
    onStatus: (ConnectionStatus, String) -> Unit,
    onWebViewReady: (WebView) -> Unit,
) {
    val url = remember(reloadToken, settings.host, settings.port) { settings.baseUrl() }
    val user = settings.username
    val pass = settings.password

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setSupportZoom(false)
                // Keep the remote desktop interactive; do not let the WebView
                // steal long-press for text selection of the stream canvas.
                isLongClickable = false
                setOnLongClickListener { true }

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: HttpAuthHandler?,
                        host: String?,
                        realm: String?,
                    ) {
                        handler?.proceed(user, pass)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onStatus(ConnectionStatus.Connecting, "Starting browser…")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onStatus(ConnectionStatus.Connected, "Online · VPS Chromium")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            onStatus(
                                ConnectionStatus.Error,
                                error?.description?.toString() ?: "Connection failed",
                            )
                        }
                    }
                }
                onWebViewReady(this)
                loadUrl(url)
            }
        },
        update = { view ->
            // Reload when settings / reconnect token change.
            if (view.url != url) {
                view.loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(Unit) {
        onDispose {
            // WebView is destroyed with the AndroidView lifecycle.
        }
    }
}
