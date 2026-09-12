package com.dawood.orbit.cloudbrowserapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.theme.ThemeMode
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserTool
import com.dawood.orbit.tools.model.Tool

/**
 * Launcher for the standalone Cloud Browser app.
 *
 * The entire browser experience is the shared [CloudBrowserTool] composable
 * used inside the full Orbit app; this activity only supplies the theme and
 * an exit callback — the tool keeps its own internal navigation stack.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OrbitTheme(themeMode = ThemeMode.System) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OrbitTheme.colors.backgroundBase),
                ) {
                    CloudBrowserTool(
                        tool = Tool(
                            id = "cloud-browser",
                            name = "Cloud Browser",
                            description = "Browse through your VPS",
                            icon = OrbitIcons.CloudUpload,
                        ),
                        onBack = {
                            // CloudBrowserTool pops its own internal routes;
                            // the callback only fires when its stack is empty.
                            finish()
                        },
                    )
                }
            }
        }
    }
}
