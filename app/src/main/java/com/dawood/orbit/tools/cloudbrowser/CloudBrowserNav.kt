package com.dawood.orbit.tools.cloudbrowser

/**
 * Routes owned by the Cloud Browser tool.
 *
 * [Home], [Sessions], [Files] and [Settings] are bottom tabs on compact
 * widths; every other route is a stack push on top of a tab.
 */
enum class CloudRoute {
    Home,
    Connect,
    BrowserView,
    ActiveSession,
    Monitor,
    Sessions,
    NewSession,
    Settings,
    InputOverlay,
    Downloads,
    Files,
}

/**
 * Back-stack for the Cloud Browser tool.
 *
 * Pure Kotlin — no Compose — so pushes and pops are trivially unit testable.
 * The [CloudBrowserTool] composable owns one instance and mirrors [current]
 * into Compose state so navigation recomposes.
 */
class CloudNavState(initial: CloudRoute = CloudRoute.Home) {

    var current: CloudRoute = initial
        private set

    private val stack = mutableListOf<CloudRoute>()

    /** Read-only back stack, oldest first. */
    val backStack: List<CloudRoute> get() = stack.toList()

    /** Tab routes reset the stack; everything else pushes on top. */
    fun navigate(route: CloudRoute) {
        if (route in TabRoutes) {
            stack.clear()
            current = route
        } else {
            push(route)
        }
    }

    /** Pushes [route], keeping history so back returns to [current]. */
    fun push(route: CloudRoute) {
        if (route == current) return
        stack.add(current)
        current = route
    }

    /**
     * Pops one level. Returns false when the stack is empty and the caller
     * should leave the tool instead.
     */
    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        current = stack.removeAt(stack.lastIndex)
        return true
    }

    companion object {
        /** Routes shown in the compact bottom tab bar. */
        val TabRoutes: Set<CloudRoute> =
            setOf(CloudRoute.Home, CloudRoute.Sessions, CloudRoute.Files, CloudRoute.Settings)
    }
}
