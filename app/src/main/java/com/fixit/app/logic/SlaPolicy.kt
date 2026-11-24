package com.fixit.app.logic

import com.fixit.app.model.Priority
import com.fixit.app.model.Status
import com.fixit.app.model.Ticket
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * All the deadline (SLA) maths lives here, in plain Kotlin (no Android classes),
 * so it is easy to explain and to unit-test.
 */
object SlaPolicy {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Fraction of the SLA time under which a ticket is "due soon". */
    private const val DUE_SOON_FRACTION = 0.25

    /** BUSINESS RULE: deadline per priority (calendar time). Change them here only. */
    fun durationMs(priority: Priority): Long = when (priority) {
        Priority.CRITICAL -> 1 * HOUR
        Priority.HIGH -> 4 * HOUR
        Priority.MEDIUM -> 24 * HOUR
        Priority.LOW -> 72 * HOUR
    }

    /** Deadline for a clock that starts at [startMs] (creation time, or "now" on reopen). */
    fun dueAt(startMs: Long, priority: Priority): Long = startMs + durationMs(priority)

    /** The SLA clock only matters while the ticket is not RESOLVED / CLOSED / CANCELLED. */
    fun isActive(status: Status): Boolean = when (status) {
        Status.OPEN, Status.ASSIGNED, Status.IN_PROGRESS, Status.WAITING_FOR_USER -> true
        else -> false
    }

    enum class State { ON_TRACK, DUE_SOON, OVERDUE, PAUSED, NONE }

    /** [remainingMs] is negative when overdue. For PAUSED it is the time frozen at the pause. */
    data class Info(val state: State, val remainingMs: Long)

    fun evaluate(t: Ticket, now: Long): Info {
        if (!isActive(t.status)) return Info(State.NONE, 0L)
        // PAUSE RULE: while waiting for the user the clock is frozen at paused_at.
        if (t.status == Status.WAITING_FOR_USER) {
            return Info(State.PAUSED, t.dueAt - (t.pausedAt ?: now))
        }
        val remaining = t.dueAt - now
        val limit = durationMs(t.priority) * DUE_SOON_FRACTION
        val state = when {
            remaining < 0 -> State.OVERDUE
            remaining <= limit -> State.DUE_SOON
            else -> State.ON_TRACK
        }
        return Info(state, remaining)
    }

    /** "3h 20m", "40m", "2d 4h", "<1m". Always positive (the sign is shown by the label). */
    fun formatDuration(ms: Long): String {
        val a = abs(ms)
        val days = a / DAY
        val hours = (a % DAY) / HOUR
        val minutes = (a % HOUR) / MINUTE
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    /** Was the ticket resolved before its (possibly pause-adjusted) deadline? */
    fun metSla(t: Ticket): Boolean = t.resolvedAt != null && t.resolvedAt <= t.dueAt

    /** Sort key for "Most urgent first": soonest deadline first, paused next, finished tickets last. */
    fun urgencyKey(t: Ticket, now: Long): Long = when {
        !isActive(t.status) -> Long.MAX_VALUE
        t.status == Status.WAITING_FOR_USER -> Long.MAX_VALUE - 1
        else -> t.dueAt
    }

    data class Stats(
        val resolvedCount: Int,
        val withinSla: Int,
        val withinSlaPercent: Int?,
        val avgResolutionMs: Long?,
        val overdueCount: Int,
        val dueSoonCount: Int
    )

    fun stats(tickets: List<Ticket>, now: Long): Stats {
        // A ticket that was reopened has resolved_at = null again, so it is not counted until re-resolved.
        val resolved = tickets.filter { it.resolvedAt != null }
        val within = resolved.count { metSla(it) }
        val percent = if (resolved.isEmpty()) null else (within * 100.0 / resolved.size).roundToInt()
        val avg = if (resolved.isEmpty()) null
        else resolved.map { it.resolvedAt!! - it.createdAt }.average().toLong()
        return Stats(
            resolvedCount = resolved.size,
            withinSla = within,
            withinSlaPercent = percent,
            avgResolutionMs = avg,
            overdueCount = countState(tickets, now, State.OVERDUE),
            dueSoonCount = countState(tickets, now, State.DUE_SOON)
        )
    }

    fun countState(tickets: List<Ticket>, now: Long, state: State): Int =
        tickets.count { evaluate(it, now).state == state }
}
