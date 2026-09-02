package com.dawood.orbit.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dawood.orbit.app.OrbitAppState
import com.dawood.orbit.core.designsystem.component.OrbitToastState
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.feature.designsystem.DesignSystemScreen
import com.dawood.orbit.feature.home.HomeScreen
import com.dawood.orbit.feature.notes.NotesScreen
import com.dawood.orbit.feature.projects.ProjectsScreen
import com.dawood.orbit.feature.settings.SettingsScreen
import com.dawood.orbit.feature.tools.ToolPlaceholderScreen
import com.dawood.orbit.feature.tools.ToolsScreen
import com.dawood.orbit.tools.ai.AiAssistantTool
import com.dawood.orbit.tools.ai.DocumentAnalysisTool
import com.dawood.orbit.tools.audio.AudioExtractTool
import com.dawood.orbit.tools.bookmarks.BookmarksTool
import com.dawood.orbit.tools.clipboard.ClipboardTool
import com.dawood.orbit.tools.codes.BarcodeTool
import com.dawood.orbit.tools.convert.ConverterTool
import com.dawood.orbit.tools.files.FileManagerTool
import com.dawood.orbit.tools.calculator.CalculatorTool
import com.dawood.orbit.tools.converter.UnitConverterTool
import com.dawood.orbit.tools.engineering.ConcreteCalculatorTool
import com.dawood.orbit.tools.engineering.RebarCalculatorTool
import com.dawood.orbit.tools.image.ImageTool
import com.dawood.orbit.tools.knowledge.KnowledgeBaseTool
import com.dawood.orbit.tools.media.MediaLibraryTool
import com.dawood.orbit.tools.notes.NotebookTool
import com.dawood.orbit.tools.notes.QuickCaptureTool
import com.dawood.orbit.tools.pdf.PdfCompressTool
import com.dawood.orbit.tools.pdf.PdfMergeTool
import com.dawood.orbit.tools.pdf.PdfSplitTool
import com.dawood.orbit.tools.pdf.WatermarkTool
import com.dawood.orbit.tools.projects.ProjectManagerTool
import com.dawood.orbit.tools.roadmap.CourseRoadmapTool
import com.dawood.orbit.tools.sections.LoadTablesTool
import com.dawood.orbit.tools.takeoff.TakeoffTool
import com.dawood.orbit.tools.time.TimeTrackerTool
import com.dawood.orbit.tools.tasks.TasksTool
import com.dawood.orbit.tools.password.PasswordGeneratorTool
import com.dawood.orbit.tools.videodownloader.ui.VideoDownloaderTool
import com.dawood.orbit.tools.registry.ToolRegistry
import com.dawood.orbit.tools.shell.ToolShell

/**
 * The navigation graph.
 *
 * Top-level destinations cross-fade — they are siblings, so movement between
 * them should not imply depth. Opening a tool lifts and scales in, and closing
 * it reverses, so entering a workspace reads as going somewhere.
 */
@Composable
fun OrbitNavHost(
    navController: NavHostController,
    appState: OrbitAppState,
    toastState: OrbitToastState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = OrbitTheme.motion

    NavHost(
        navController = navController,
        startDestination = OrbitDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(motion.normal, easing = motion.enterEasing)) },
        exitTransition = { fadeOut(tween(motion.fast, easing = motion.exitEasing)) },
        popEnterTransition = { fadeIn(tween(motion.normal, easing = motion.enterEasing)) },
        popExitTransition = { fadeOut(tween(motion.fast, easing = motion.exitEasing)) },
    ) {
        composable(OrbitDestination.Home.route) {
            HomeScreen(
                pinnedTools = appState.pinnedTools,
                recentTools = appState.recentTools,
                onOpenTool = { onNavigate(it.route) },
                onQuickAction = { onNavigate(quickActionRoute(it)) },
                onSeeAllTools = { onNavigate(OrbitDestination.Tools.route) },
                onOpenNotes = { onNavigate(OrbitDestination.Notes.route) },
                onOpenProjects = { onNavigate(OrbitDestination.Projects.route) },
            )
        }

        composable(OrbitDestination.Tools.route) {
            ToolsScreen(
                favouriteTools = appState.favouriteTools,
                recentTools = appState.recentTools,
                isFavourite = appState::isFavourite,
                onToggleFavourite = { appState.toggleFavourite(it.id) },
                onOpenTool = { onNavigate(it.route) },
            )
        }

        composable(OrbitDestination.Projects.route) {
            ProjectsScreen(onOpenTool = { onNavigate(OrbitRoutes.tool(it)) })
        }

        composable(OrbitDestination.Notes.route) {
            NotesScreen(onOpenNotebook = { onNavigate(OrbitRoutes.tool(ToolRegistry.Ids.NOTEBOOK)) })
        }

        composable(OrbitDestination.Settings.route) {
            SettingsScreen(
                themeMode = appState.themeMode,
                onThemeModeChange = appState::updateThemeMode,
                accent = appState.accent,
                onAccentChange = appState::updateAccent,
                compactDensity = appState.compactDensity,
                onCompactDensityChange = appState::updateCompactDensity,
                onOpenDesignSystem = { onNavigate(OrbitRoutes.DESIGN_SYSTEM) },
            )
        }

        composable(
            route = OrbitRoutes.DESIGN_SYSTEM,
            enterTransition = {
                fadeIn(tween(motion.slow, easing = motion.enterEasing)) +
                    slideInVertically(tween(motion.slow, easing = motion.enterEasing)) { it / 14 }
            },
            exitTransition = { fadeOut(tween(motion.fast, easing = motion.exitEasing)) },
            popExitTransition = {
                fadeOut(tween(motion.normal, easing = motion.exitEasing)) +
                    slideOutVertically(tween(motion.normal, easing = motion.exitEasing)) { it / 14 }
            },
        ) {
            DesignSystemScreen(
                onBack = { navController.popBackStack() },
                toastState = toastState,
            )
        }

        // Every tool in the catalogue resolves through this one route. Adding a
        // tool means adding a registry entry and a branch in ToolWorkspaceHost —
        // the graph itself never grows.
        composable(
            route = OrbitRoutes.TOOL_PATTERN,
            arguments = listOf(navArgument(OrbitRoutes.TOOL_ID_ARG) { type = NavType.StringType }),
            enterTransition = {
                fadeIn(tween(motion.slower, easing = motion.enterEasing)) +
                    scaleIn(tween(motion.slower, easing = motion.enterEasing), initialScale = 0.97f) +
                    slideInVertically(tween(motion.slower, easing = motion.enterEasing)) { it / 16 }
            },
            exitTransition = { fadeOut(tween(motion.fast, easing = motion.exitEasing)) },
            popEnterTransition = { fadeIn(tween(motion.normal, easing = motion.enterEasing)) },
            popExitTransition = {
                fadeOut(tween(motion.normal, easing = motion.exitEasing)) +
                    scaleOut(tween(motion.normal, easing = motion.exitEasing), targetScale = 0.98f) +
                    slideOutVertically(tween(motion.normal, easing = motion.exitEasing)) { it / 16 }
            },
        ) { entry ->
            val toolId = entry.arguments?.getString(OrbitRoutes.TOOL_ID_ARG).orEmpty()
            ToolWorkspaceHost(
                toolId = toolId,
                onBack = { navController.popBackStack() },
                onBrowseTools = { onNavigate(OrbitDestination.Tools.route) },
                onOpenTool = { onNavigate(OrbitRoutes.tool(it)) },
            )
        }
    }
}

/**
 * Resolves a tool id to its workspace.
 *
 * A tool with no workspace yet still opens — it gets the standard shell and an
 * honest empty state instead of a dead link, which is what keeps the catalogue
 * safe to publish ahead of the implementation.
 */
@Composable
private fun ToolWorkspaceHost(
    toolId: String,
    onBack: () -> Unit,
    onBrowseTools: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val tool = ToolRegistry.tool(toolId)
    if (tool == null) {
        ToolPlaceholderScreen(
            tool = ToolRegistry.tools.first(),
            onBrowseTools = onBrowseTools,
        )
        return
    }

    when (tool.id) {
        ToolRegistry.Ids.NOTEBOOK -> NotebookTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.PDF_MERGE -> PdfMergeTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.PDF_SPLIT -> PdfSplitTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.PDF_COMPRESS -> PdfCompressTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.WATERMARK -> WatermarkTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.FILE_MANAGER -> FileManagerTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.IMAGE_TOOLS -> ImageTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.AUDIO_EXTRACT -> AudioExtractTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.MEDIA_LIBRARY -> MediaLibraryTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.PROJECT_MANAGER -> ProjectManagerTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.TIME_TRACKER -> TimeTrackerTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.CLIPBOARD -> ClipboardTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.LOAD_TABLES -> LoadTablesTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.TAKEOFF -> TakeoffTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.QR_TOOLS -> BarcodeTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.DOC_CONVERT -> ConverterTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.AI_ASSISTANT -> AiAssistantTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.DOC_ANALYSIS -> DocumentAnalysisTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.COURSE_ROADMAP -> CourseRoadmapTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.VIDEO_DOWNLOADER -> VideoDownloaderTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.CALCULATOR -> CalculatorTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.UNIT_CONVERTER -> UnitConverterTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.CONCRETE -> ConcreteCalculatorTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.REBAR -> RebarCalculatorTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.PASSWORD -> PasswordGeneratorTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.TASKS -> TasksTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.QUICK_CAPTURE -> QuickCaptureTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.BOOKMARKS -> BookmarksTool(tool = tool, onBack = onBack)
        ToolRegistry.Ids.KNOWLEDGE_BASE ->
            KnowledgeBaseTool(tool = tool, onBack = onBack, onOpenTool = onOpenTool)
        else -> ToolShell(tool = tool, onBack = onBack) {
            ToolPlaceholderScreen(tool = tool, onBrowseTools = onBrowseTools)
        }
    }
}

private fun quickActionRoute(actionId: String): String = when (actionId) {
    "qa1" -> OrbitRoutes.tool(ToolRegistry.Ids.NOTEBOOK)
    "qa2" -> OrbitRoutes.tool(ToolRegistry.Ids.PDF_MERGE)
    "qa3" -> OrbitRoutes.tool(ToolRegistry.Ids.TASKS)
    "qa4" -> OrbitRoutes.tool(ToolRegistry.Ids.VIDEO_DOWNLOADER)
    else -> OrbitDestination.Tools.route
}
