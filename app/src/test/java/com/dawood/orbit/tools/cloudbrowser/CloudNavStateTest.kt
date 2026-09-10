package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudNavStateTest {

    @Test
    fun initialDefaultsToHomeWithEmptyStack() {
        val nav = CloudNavState()

        assertEquals(CloudRoute.Home, nav.current)
        assertTrue(nav.backStack.isEmpty())
    }

    @Test
    fun customInitialIsRespected() {
        val nav = CloudNavState(CloudRoute.Files)

        assertEquals(CloudRoute.Files, nav.current)
        assertTrue(nav.backStack.isEmpty())
    }

    @Test
    fun pushKeepsHistoryOldestFirst() {
        val nav = CloudNavState()

        nav.push(CloudRoute.Connect)
        nav.push(CloudRoute.BrowserView)

        assertEquals(CloudRoute.BrowserView, nav.current)
        assertEquals(listOf(CloudRoute.Home, CloudRoute.Connect), nav.backStack)
    }

    @Test
    fun pushOfCurrentRouteIsNoOp() {
        val nav = CloudNavState()

        nav.push(CloudRoute.Home)

        assertEquals(CloudRoute.Home, nav.current)
        assertTrue(nav.backStack.isEmpty())

        nav.push(CloudRoute.Connect)
        nav.push(CloudRoute.Connect)

        assertEquals(CloudRoute.Connect, nav.current)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)
    }

    @Test
    fun popRestoresPreviousRoute() {
        val nav = CloudNavState()
        nav.push(CloudRoute.Connect)
        nav.push(CloudRoute.BrowserView)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.Connect, nav.current)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.Home, nav.current)
        assertTrue(nav.backStack.isEmpty())
    }

    @Test
    fun popOnEmptyStackReturnsFalseAndKeepsCurrent() {
        val nav = CloudNavState()

        assertFalse(nav.pop())
        assertEquals(CloudRoute.Home, nav.current)
    }

    @Test
    fun navigateToTabResetsStack() {
        val nav = CloudNavState()
        nav.push(CloudRoute.Connect)
        nav.push(CloudRoute.BrowserView)

        nav.navigate(CloudRoute.Sessions)

        assertEquals(CloudRoute.Sessions, nav.current)
        assertTrue(nav.backStack.isEmpty())
        assertFalse(nav.pop())
    }

    @Test
    fun navigateToSameTabClearsStack() {
        val nav = CloudNavState()
        nav.push(CloudRoute.Connect)

        nav.navigate(CloudRoute.Home)

        assertEquals(CloudRoute.Home, nav.current)
        assertTrue(nav.backStack.isEmpty())
    }

    @Test
    fun navigateToNonTabPushes() {
        val nav = CloudNavState()

        nav.navigate(CloudRoute.Connect)

        assertEquals(CloudRoute.Connect, nav.current)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)

        nav.navigate(CloudRoute.BrowserView)

        assertEquals(CloudRoute.BrowserView, nav.current)
        assertEquals(listOf(CloudRoute.Home, CloudRoute.Connect), nav.backStack)
    }

    @Test
    fun navigateToNonTabMatchingCurrentIsNoOp() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.Connect)

        nav.navigate(CloudRoute.Connect)

        assertEquals(CloudRoute.Connect, nav.current)
        assertEquals(listOf(CloudRoute.Home), nav.backStack)
    }

    @Test
    fun tabRoutesAreExactlyHomeSessionsFilesSettings() {
        assertEquals(
            setOf(CloudRoute.Home, CloudRoute.Sessions, CloudRoute.Files, CloudRoute.Settings),
            CloudNavState.TabRoutes,
        )
        assertFalse(CloudRoute.Connect in CloudNavState.TabRoutes)
        assertFalse(CloudRoute.BrowserView in CloudNavState.TabRoutes)
        assertFalse(CloudRoute.Monitor in CloudNavState.TabRoutes)
        assertFalse(CloudRoute.NewSession in CloudNavState.TabRoutes)
    }

    @Test
    fun fullJourneyTabResetThenBackLeavesTool() {
        val nav = CloudNavState()
        nav.navigate(CloudRoute.Connect)
        nav.navigate(CloudRoute.BrowserView)
        assertEquals(listOf(CloudRoute.Home, CloudRoute.Connect), nav.backStack)

        nav.navigate(CloudRoute.Files)
        assertEquals(CloudRoute.Files, nav.current)
        assertTrue(nav.backStack.isEmpty())

        nav.navigate(CloudRoute.Downloads)
        assertEquals(CloudRoute.Downloads, nav.current)
        assertEquals(listOf(CloudRoute.Files), nav.backStack)

        assertTrue(nav.pop())
        assertEquals(CloudRoute.Files, nav.current)
        assertFalse(nav.pop())
    }

    @Test
    fun backStackIsACopy() {
        val nav = CloudNavState()
        nav.push(CloudRoute.Connect)

        val snapshot = nav.backStack
        assertEquals(listOf(CloudRoute.Home), snapshot)

        nav.push(CloudRoute.BrowserView)
        assertEquals(listOf(CloudRoute.Home), snapshot)
        assertEquals(listOf(CloudRoute.Home, CloudRoute.Connect), nav.backStack)
    }
}
