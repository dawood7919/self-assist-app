package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full route-set coverage for [CloudNavState].
 *
 * The route set is 12 entries; tabs are Home/Sessions/Files/Settings and
 * everything else is a stack push. Existing CloudNavStateTest covers the
 * Home/Connect/BrowserView/Sessions/Files/Downloads paths; this file locks
 * the remaining routes (ActiveSession, Monitor, NewSession, InputOverlay)
 * and asserts the tab/non-tab split exhaustively so a future route addition
 * fails loudly.
 *
 * JUnit4 only.
 */
class CloudNavStateRoutesTest {

    @Test
    fun routeSetHasTwelveEntries() {
        val routes = CloudRoute.entries
        assertEquals(12, routes.size)
        assertTrue(routes.containsAll(listOf(CloudRoute.Home, CloudRoute.Connect, CloudRoute.BrowserView, CloudRoute.ActiveSession, CloudRoute.Monitor, CloudRoute.Sessions, CloudRoute.NewSession, CloudRoute.Settings, CloudRoute.InputOverlay, CloudRoute.Downloads, CloudRoute.Files, CloudRoute.Bookmarks)))
    }

    @Test
    fun tabRoutesAreExactlyFour() {
        assertEquals(
            setOf(CloudRoute.Home, CloudRoute.Sessions, CloudRoute.Files, CloudRoute.Settings),
            CloudNavState.TabRoutes,
        )
        assertEquals(4, CloudNavState.TabRoutes.size)
    }

    @Test
    fun everyNonTabRouteIsOutsideTabRoutes() {
        val nonTabs = listOf(CloudRoute.Connect, CloudRoute.BrowserView, CloudRoute.ActiveSession, CloudRoute.Monitor, CloudRoute.NewSession, CloudRoute.InputOverlay, CloudRoute.Downloads, CloudRoute.Bookmarks)
        assertEquals(8, nonTabs.size)
        nonTabs.forEach { route ->
            assertFalse("$route should not be a tab", route in CloudNavState.TabRoutes)
        }
    }

    @Test
    fun everyNonTabPushesFromHome() {
        val nonTabs = listOf(CloudRoute.Connect, CloudRoute.BrowserView, CloudRoute.ActiveSession, CloudRoute.Monitor, CloudRoute.NewSession, CloudRoute.InputOverlay, CloudRoute.Downloads, CloudRoute.Bookmarks)
        nonTabs.forEach { route ->
            val nav = CloudNavState()
            nav.navigate(route)
            assertEquals(route, nav.current)
            assertEquals(listOf(CloudRoute.Home), nav.backStack)
        }
    }

    @Test
    fun everyTabResetsStack() {
        val tabs = listOf(CloudRoute.Home, CloudRoute.Sessions, CloudRoute.Files, CloudRoute.Settings)
        tabs.forEach { tab ->
            val nav = CloudNavState()
            nav.push(CloudRoute.Connect)
            nav.push(CloudRoute.BrowserView)
            nav.navigate(tab)
            assertEquals(tab, nav.current)
            assertTrue(nav.backStack.isEmpty())
            assertFalse(nav.pop())
        }
    }

    @Test
    fun activeSessionAndInputOverlayStackAndPop() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.NewSession)
        nav.navigate(CloudRoute.ActiveSession)
        nav.navigate(CloudRoute.InputOverlay)

        assertEquals(CloudRoute.InputOverlay, nav.current)
        assertEquals(
            listOf(CloudRoute.Home, CloudRoute.NewSession, CloudRoute.ActiveSession),
            nav.backStack,
        )

        assertTrue(nav.pop())
        assertEquals(CloudRoute.ActiveSession, nav.current)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.NewSession, nav.current)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.Home, nav.current)
        assertTrue(nav.backStack.isEmpty())
    }

    @Test
    fun monitorPushesOnTopOfSessionsTab() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.Sessions)

        nav.navigate(CloudRoute.Monitor)

        assertEquals(CloudRoute.Monitor, nav.current)
        assertEquals(listOf(CloudRoute.Sessions), nav.backStack)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.Sessions, nav.current)
        assertFalse(nav.pop())
    }

    @Test
    fun downloadsPushesOnTopOfFilesTab() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.Files)

        nav.navigate(CloudRoute.Downloads)

        assertEquals(CloudRoute.Downloads, nav.current)
        assertEquals(listOf(CloudRoute.Files), nav.backStack)
    }

    @Test
    fun navigatingToCurrentNonTabIsNoOp() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.ActiveSession)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)

        nav.navigate(CloudRoute.ActiveSession)
        assertEquals(CloudRoute.ActiveSession, nav.current)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)

        nav.navigate(CloudRoute.InputOverlay)
        nav.navigate(CloudRoute.InputOverlay)
        assertEquals(CloudRoute.InputOverlay, nav.current)
        assertEquals(listOf(CloudRoute.Home, CloudRoute.ActiveSession), nav.backStack)
    }
}
