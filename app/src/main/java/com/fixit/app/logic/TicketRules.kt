package com.fixit.app.logic

import com.fixit.app.model.Role
import com.fixit.app.model.Status
import com.fixit.app.model.Ticket
import com.fixit.app.model.User
import com.fixit.app.model.Violation

/**
 * The ticket workflow and permissions, in plain Kotlin (no Android classes).
 * DBHelper calls check() before every change, so the rules are enforced in code,
 * not only by hiding buttons in the UI.
 */
object TicketRules {

    /** BUSINESS RULE: the only allowed transitions. Anything else is refused. */
    private val allowed: Map<Status, Set<Status>> = mapOf(
        Status.OPEN to setOf(Status.ASSIGNED, Status.CANCELLED),
        Status.ASSIGNED to setOf(Status.IN_PROGRESS),
        Status.IN_PROGRESS to setOf(Status.WAITING_FOR_USER, Status.RESOLVED),
        Status.WAITING_FOR_USER to setOf(Status.IN_PROGRESS),
        Status.RESOLVED to setOf(Status.CLOSED, Status.IN_PROGRESS),
        Status.CLOSED to emptySet(),     // final
        Status.CANCELLED to emptySet()   // final
    )

    fun isTransitionAllowed(from: Status, to: Status): Boolean = allowed[from]?.contains(to) == true

    /** Returns null when the change is allowed, otherwise the rule that was broken. */
    fun check(t: Ticket, actor: User, to: Status, note: String?): Violation? {
        // Rule 1: the transition must exist in the table above.
        if (!isTransitionAllowed(t.status, to)) return Violation.BAD_TRANSITION

        val isTech = actor.role == Role.TECH
        val isOwner = t.createdBy == actor.id
        val isAssignedTech = isTech && t.assignedTo == actor.id

        when (to) {
            Status.ASSIGNED -> {
                // Rule 2: only a TECH can take a ticket.
                if (!isTech) return Violation.TECH_ONLY
                // Rule 3: a ticket belongs to ONE technician; a second one cannot take it.
                if (t.assignedTo != null) return Violation.ALREADY_TAKEN
            }
            Status.IN_PROGRESS -> when (t.status) {
                Status.ASSIGNED -> {
                    if (!isTech) return Violation.TECH_ONLY
                    if (!isAssignedTech) return Violation.NOT_ASSIGNED
                }
                // The user replies (owner) or the technician resumes.
                Status.WAITING_FOR_USER -> {
                    if (!isOwner && !isAssignedTech) {
                        return if (isTech) Violation.NOT_ASSIGNED else Violation.NOT_OWNER
                    }
                }
                // Reopen ("still broken"): only the owner.
                Status.RESOLVED -> if (!isOwner) return Violation.NOT_OWNER
                else -> {}
            }
            Status.WAITING_FOR_USER, Status.RESOLVED -> {
                if (!isTech) return Violation.TECH_ONLY
                if (!isAssignedTech) return Violation.NOT_ASSIGNED
                // Rule 4: RESOLVED needs a non-empty resolution note.
                if (to == Status.RESOLVED && note.isNullOrBlank()) return Violation.NOTE_REQUIRED
            }
            // Rule 5: only the owner can CLOSE or CANCEL (CANCEL only exists from OPEN, see table).
            Status.CLOSED, Status.CANCELLED -> if (!isOwner) return Violation.NOT_OWNER
            Status.OPEN -> return Violation.BAD_TRANSITION
        }
        return null
    }

    /** Everything that changes in the ticket row besides the status itself. */
    data class Effects(
        val assignedTo: Long?,
        val dueAt: Long,
        val pausedAt: Long?,
        val resolvedAt: Long?,
        val closedAt: Long?
    )

    /** SLA and ownership side effects of a status change (pause rule, reopen rule...). */
    fun effects(t: Ticket, to: Status, actorId: Long, now: Long): Effects {
        var due = t.dueAt
        var paused = t.pausedAt
        var resolved = t.resolvedAt
        var closed = t.closedAt
        var assigned = t.assignedTo

        // PAUSE RULE (leaving WAITING): push the deadline forward by the time spent paused.
        if (t.status == Status.WAITING_FOR_USER && t.pausedAt != null) {
            due += now - t.pausedAt
            paused = null
        }
        // PAUSE RULE (entering WAITING): freeze the clock.
        if (to == Status.WAITING_FOR_USER) paused = now
        if (to == Status.ASSIGNED) assigned = actorId
        // The countdown stops when the ticket becomes RESOLVED.
        if (to == Status.RESOLVED) resolved = now
        // REOPEN RULE: recompute the deadline from now, using the priority.
        if (t.status == Status.RESOLVED && to == Status.IN_PROGRESS) {
            due = SlaPolicy.dueAt(now, t.priority)
            resolved = null
        }
        if (to == Status.CLOSED) closed = now
        return Effects(assigned, due, paused, resolved, closed)
    }

    /** The buttons shown on the ticket details screen. */
    enum class Action(val from: Status, val target: Status) {
        TAKE(Status.OPEN, Status.ASSIGNED),
        START(Status.ASSIGNED, Status.IN_PROGRESS),
        ASK_USER(Status.IN_PROGRESS, Status.WAITING_FOR_USER),
        RESUME(Status.WAITING_FOR_USER, Status.IN_PROGRESS),
        RESOLVE(Status.IN_PROGRESS, Status.RESOLVED),
        CLOSE(Status.RESOLVED, Status.CLOSED),
        REOPEN(Status.RESOLVED, Status.IN_PROGRESS),
        CANCEL(Status.OPEN, Status.CANCELLED)
    }

    fun availableActions(t: Ticket, actor: User): List<Action> =
        Action.values().filter { a ->
            a.from == t.status &&
                (a != Action.RESUME || actor.role == Role.TECH) &&
                check(t, actor, a.target, "x") == null   // "x" = dummy note, only the permission matters here
        }

    fun canView(t: Ticket, u: User): Boolean = u.role == Role.TECH || t.createdBy == u.id

    /** Owner or any technician may comment, until the ticket is CLOSED or CANCELLED. */
    fun canComment(t: Ticket, u: User): Boolean =
        canView(t, u) && t.status != Status.CLOSED && t.status != Status.CANCELLED

    /** BUSINESS RULE: rating only on a CLOSED ticket, only by the owner, only once. */
    fun canRate(t: Ticket, u: User): Boolean =
        t.status == Status.CLOSED && t.createdBy == u.id && t.rating == null
}
