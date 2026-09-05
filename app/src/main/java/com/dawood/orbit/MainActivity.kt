package com.dawood.orbit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dawood.orbit.app.rememberOrbitAppState
import com.dawood.orbit.core.designsystem.component.LocalOrbitToastState
import com.dawood.orbit.core.designsystem.component.OrbitToastState
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.feature.shell.OrbitAppShell
import com.dawood.orbit.update.AppUpdateManager
import com.dawood.orbit.update.UpdateScheduler

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Schedule background update checks when the user left auto-update on.
        val updater = AppUpdateManager.get(this)
        if (updater.autoUpdateEnabled) {
            UpdateScheduler.schedule(this)
        }

        setContent {
            val appState = rememberOrbitAppState()
            val toastState = remember { OrbitToastState() }
            val navController = rememberNavController()

            OrbitTheme(themeMode = appState.themeMode, accent = appState.accent) {
                CompositionLocalProvider(LocalOrbitToastState provides toastState) {
                    OrbitAppShell(
                        appState = appState,
                        navController = navController,
                        toastState = toastState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
