package com.fixit.app.model

// ---------------------------------------------------------------------------
// Plain data classes and enums. No Android code in this file.
// ---------------------------------------------------------------------------

enum class Role { USER, TECH }

enum class Priority { CRITICAL, HIGH, MEDIUM, LOW }

enum class Status {
    OPEN, ASSIGNED, IN_PROGRESS, WAITING_FOR_USER, RESOLVED, CLOSED, CANCELLED
}

/** Each device type suggests a ticket category (used by the QR scan flow). */
enum class DeviceType(val suggestedCategory: String) {
    PC("Hardware"),
    PROJECTOR("Hardware"),
    PRINTER("Printer"),
    ROUTER("Network"),
    OTHER("Other")
}

/**
 * Every refusal the business rules can produce. The UI turns each one into a
 * friendly message (see Violation.messageRes() in UiHelpers.kt).
 */
enum class Violation {
    BAD_TRANSITION, TECH_ONLY, NOT_OWNER, NOT_ASSIGNED, ALREADY_TAKEN, NOTE_REQUIRED,
    NOT_FOUND, CANNOT_COMMENT, EMPTY_COMMENT, CANNOT_RATE, ALREADY_RATED, BAD_RATING,
    EMAIL_TAKEN, CODE_TAKEN, USER_ONLY, BAD_TECH_CODE
}

data class User(val id: Long, val name: String, val email: String, val role: Role, val createdAt: Long)

data class Category(val id: Long, val name: String)

data class Device(
    val id: Long,
    val code: String,
    val type: DeviceType,
    val model: String,
    val location: String,
    val note: String?
)

data class Ticket(
    val id: Long,
    val title: String,
    val description: String,
    val categoryId: Long,
    val categoryName: String,
    val deviceId: Long?,
    val deviceCode: String?,
    val deviceLocation: String?,
    val deviceModel: String?,
    val priority: Priority,
    val status: Status,
    val createdBy: Long,
    val ownerName: String,
    val assignedTo: Long?,
    val techName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val dueAt: Long,
    val pausedAt: Long?,
    val resolvedAt: Long?,
    val closedAt: Long?,
    val resolutionNote: String?,
    val rating: Int?
)

data class Comment(
    val id: Long,
    val ticketId: Long,
    val authorId: Long,
    val authorName: String,
    val authorRole: Role,
    val message: String,
    val createdAt: Long
)

data class HistoryEntry(
    val id: Long,
    val ticketId: Long,
    val changedByName: String,
    val oldStatus: Status?,
    val newStatus: Status,
    val note: String?,
    val changedAt: Long
)
