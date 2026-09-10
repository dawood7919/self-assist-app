package com.dawood.orbit.tools.cloudbrowser

/**
 * Pure polling state machine for VPS metrics (Workstream A).
 *
 * Pure Kotlin with no coroutines machinery inside, so the fold is trivially
 * unit testable: the UI (or a ViewModel) owns the loop, calls [VpsApi.pollMetrics],
 * and feeds the [Result] back through [foldMetrics]. Backoff comes from
 * [CloudBrowserEngine.nextPollDelayMs], so the delay schedule has one owner.
 */

/** Snapshot of the metrics poll loop: latest data, failure streak, next delay. */
data class PollState(
    val metrics: VpsMetrics? = null,
    val failures: Int = 0,
    val delayMs: Long = 1_000L,
)

object CloudPolling {

    /** First state before any poll has completed. */
    fun initial(): PollState = PollState()

    /**
     * Folds one poll [next] into the loop state.
     *
     * Success resets the failure streak to zero and restores the 1s delay.
     * Failure keeps the previous [prev] metrics on screen, bumps the streak
     * by one, and backs off via [CloudBrowserEngine.nextPollDelayMs].
     *
     * @param prev last metrics shown, or null when none arrived yet.
     * @param failures consecutive failures before this poll.
     */
    fun foldMetrics(prev: VpsMetrics?, next: Result<VpsMetrics>, failures: Int): PollState {
        val safeFailures = failures.coerceAtLeast(0)
        return if (next.isSuccess) {
            PollState(
                metrics = next.getOrNull(),
                failures = 0,
                delayMs = CloudBrowserEngine.nextPollDelayMs(0),
            )
        } else {
            val streak = safeFailures + 1
            PollState(
                metrics = prev,
                failures = streak,
                delayMs = CloudBrowserEngine.nextPollDelayMs(streak),
            )
        }
    }
}
