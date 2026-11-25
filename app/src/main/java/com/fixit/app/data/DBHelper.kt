package com.fixit.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fixit.app.R
import com.fixit.app.logic.AccessPolicy
import com.fixit.app.logic.PasswordHasher
import com.fixit.app.logic.SlaPolicy
import com.fixit.app.logic.TicketRules
import com.fixit.app.model.*

// Small cursor helpers so the mapping code stays readable.
private fun Cursor.str(c: String): String = getString(getColumnIndexOrThrow(c))
private fun Cursor.strN(c: String): String? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getString(i) }
private fun Cursor.lng(c: String): Long = getLong(getColumnIndexOrThrow(c))
private fun Cursor.lngN(c: String): Long? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getLong(i) }
private fun Cursor.intN(c: String): Int? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getInt(i) }

/**
 * The only class that talks to SQLite. One function per query.
 * Every status change goes through changeStatus(): rules check + ticket update +
 * history row, all inside ONE transaction.
 */
class DBHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "fixit.db", null, DB_VERSION) {

    private val appContext: Context = context.applicationContext

    companion object {
        private const val DB_VERSION = 3
        @Volatile private var instance: DBHelper? = null
        fun get(ctx: Context): DBHelper =
            instance ?: synchronized(this) { instance ?: DBHelper(ctx).also { instance = it } }

        private const val TICKET_SQL =
            "SELECT t.*, c.name AS category_name, d.code AS device_code, d.location AS device_location, " +
                    "d.model AS device_model, u.name AS owner_name, a.name AS tech_name " +
                    "FROM tickets t " +
                    "JOIN categories c ON c.id = t.category_id " +
                    "LEFT JOIN devices d ON d.id = t.device_id " +
                    "JOIN users u ON u.id = t.created_by " +
                    "LEFT JOIN users a ON a.id = t.assigned_to"
    }

    // ------------------------------------------------------------------ schema

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)   // make FOREIGN KEY constraints real
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, salt TEXT NOT NULL, " +
                "role TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE devices(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, " +
                "type TEXT NOT NULL, model TEXT NOT NULL, location TEXT NOT NULL, note TEXT)")
        db.execSQL("CREATE TABLE tickets(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                "description TEXT NOT NULL, category_id INTEGER NOT NULL REFERENCES categories(id), " +
                "device_id INTEGER REFERENCES devices(id), priority TEXT NOT NULL, status TEXT NOT NULL, " +
                "created_by INTEGER NOT NULL REFERENCES users(id), assigned_to INTEGER REFERENCES users(id), " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, due_at INTEGER NOT NULL, " +
                "paused_at INTEGER, resolved_at INTEGER, closed_at INTEGER, resolution_note TEXT, " +
                "rating INTEGER CHECK(rating BETWEEN 1 AND 5))")
        db.execSQL("CREATE TABLE comments(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE, " +
                "author_id INTEGER NOT NULL REFERENCES users(id), message TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE ticket_history(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE, " +
                "changed_by INTEGER NOT NULL REFERENCES users(id), old_status TEXT, new_status TEXT NOT NULL, " +
                "note TEXT, changed_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_tickets_owner ON tickets(created_by)")
        db.execSQL("CREATE INDEX idx_tickets_tech ON tickets(assigned_to)")
        db.execSQL("CREATE INDEX idx_tickets_status ON tickets(status)")
        db.execSQL("CREATE INDEX idx_tickets_device ON tickets(device_id)")
        db.execSQL("CREATE INDEX idx_comments_ticket ON comments(ticket_id)")
        db.execSQL("CREATE INDEX idx_history_ticket ON ticket_history(ticket_id)")
        seed(db)
    }

    /** Simple strategy for a school project: drop everything and rebuild (demo data is re-seeded). */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (t in listOf("ticket_history", "comments", "tickets", "devices", "categories", "users")) {
            db.execSQL("DROP TABLE IF EXISTS $t")
        }
        onCreate(db)
    }

    // ------------------------------------------------------------------ users

    private fun insertUser(db: SQLiteDatabase, name: String, email: String, password: String, role: Role): Long {
        val salt = PasswordHasher.newSalt()
        val cv = ContentValues().apply {
            put("name", name); put("email", email.lowercase())
            put("password_hash", PasswordHasher.hash(password, salt)); put("salt", salt)
            put("role", role.name); put("created_at", System.currentTimeMillis())
        }
        return db.insertOrThrow("users", null, cv)
    }

    /**
     * BUSINESS RULE: anyone can create a USER account; a TECH account is created only when the
     * technician access code is correct (see AccessPolicy).
     */
    fun register(name: String, email: String, password: String, role: Role, techCode: String?): Violation? {
        if (role == Role.TECH && !AccessPolicy.isValidTechCode(techCode)) return Violation.BAD_TECH_CODE
        val db = writableDatabase
        db.rawQuery("SELECT id FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            if (it.moveToFirst()) return Violation.EMAIL_TAKEN
        }
        insertUser(db, name, email, password, role)
        return null
    }

    fun authenticate(email: String, password: String): User? {
        readableDatabase.rawQuery(
            "SELECT id, name, email, password_hash, salt, role, created_at FROM users WHERE email = ?",
            arrayOf(email.trim().lowercase())
        ).use { c ->
            if (!c.moveToFirst()) return null
            if (!PasswordHasher.verify(password, c.str("salt"), c.str("password_hash"))) return null
            return readUser(c)
        }
    }

    fun getUserById(id: Long): User? {
        readableDatabase.rawQuery(
            "SELECT id, name, email, role, created_at FROM users WHERE id = ?", arrayOf(id.toString())
        ).use { c -> return if (c.moveToFirst()) readUser(c) else null }
    }

    private fun readUser(c: Cursor) =
        User(c.lng("id"), c.str("name"), c.str("email"), Role.valueOf(c.str("role")), c.lng("created_at"))

    // ------------------------------------------------------------------ categories & devices

    fun getCategories(): List<Category> {
        val out = ArrayList<Category>()
        readableDatabase.rawQuery("SELECT id, name FROM categories ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(Category(c.lng("id"), c.str("name")))
        }
        return out
    }

    private fun readDevice(c: Cursor) = Device(
        c.lng("id"), c.str("code"), DeviceType.valueOf(c.str("type")),
        c.str("model"), c.str("location"), c.strN("note")
    )

    fun getDevices(query: String): List<Device> {
        val like = "%${query.trim()}%"
        val out = ArrayList<Device>()
        readableDatabase.rawQuery(
            "SELECT * FROM devices WHERE code LIKE ? OR model LIKE ? OR location LIKE ? OR type LIKE ? ORDER BY code",
            arrayOf(like, like, like, like)
        ).use { c -> while (c.moveToNext()) out.add(readDevice(c)) }
        return out
    }

    fun getDeviceByCode(code: String): Device? {
        readableDatabase.rawQuery("SELECT * FROM devices WHERE code = ?", arrayOf(code)).use { c ->
            return if (c.moveToFirst()) readDevice(c) else null
        }
    }

    fun getDeviceById(id: Long): Device? {
        readableDatabase.rawQuery("SELECT * FROM devices WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readDevice(c) else null
        }
    }

    /** Used for the live "duplicate code" warning. [excludeId] is the device being edited. */
    fun deviceCodeExists(code: String, excludeId: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT id FROM devices WHERE code = ? AND id <> ?", arrayOf(code, excludeId.toString())
        ).use { return it.moveToFirst() }
    }

    fun saveDevice(d: Device): Violation? {
        if (deviceCodeExists(d.code, d.id)) return Violation.CODE_TAKEN   // refuse duplicate codes
        val cv = ContentValues().apply {
            put("code", d.code); put("type", d.type.name); put("model", d.model)
            put("location", d.location); put("note", d.note)
        }
        if (d.id == 0L) writableDatabase.insertOrThrow("devices", null, cv)
        else writableDatabase.update("devices", cv, "id = ?", arrayOf(d.id.toString()))
        return null
    }

    // ------------------------------------------------------------------ tickets (read)

    private fun readTicket(c: Cursor) = Ticket(
        id = c.lng("id"), title = c.str("title"), description = c.str("description"),
        categoryId = c.lng("category_id"), categoryName = c.str("category_name"),
        deviceId = c.lngN("device_id"), deviceCode = c.strN("device_code"),
        deviceLocation = c.strN("device_location"), deviceModel = c.strN("device_model"),
        priority = Priority.valueOf(c.str("priority")), status = Status.valueOf(c.str("status")),
        createdBy = c.lng("created_by"), ownerName = c.str("owner_name"),
        assignedTo = c.lngN("assigned_to"), techName = c.strN("tech_name"),
        createdAt = c.lng("created_at"), updatedAt = c.lng("updated_at"), dueAt = c.lng("due_at"),
        pausedAt = c.lngN("paused_at"), resolvedAt = c.lngN("resolved_at"), closedAt = c.lngN("closed_at"),
        resolutionNote = c.strN("resolution_note"), rating = c.intN("rating")
    )

    private fun queryTicket(db: SQLiteDatabase, id: Long): Ticket? =
        db.rawQuery("$TICKET_SQL WHERE t.id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) readTicket(c) else null
        }

    /** BUSINESS RULE: a USER only ever gets their own tickets; a TECH gets all of them. */
    fun getTicketsFor(user: User): List<Ticket> {
        val out = ArrayList<Ticket>()
        val c = if (user.role == Role.TECH)
            readableDatabase.rawQuery("$TICKET_SQL ORDER BY t.created_at DESC", null)
        else
            readableDatabase.rawQuery("$TICKET_SQL WHERE t.created_by = ? ORDER BY t.created_at DESC",
                arrayOf(user.id.toString()))
        c.use { while (it.moveToNext()) out.add(readTicket(it)) }
        return out
    }

    /** Returns null if the ticket does not exist OR the user is not allowed to see it. */
    fun getTicketForUser(id: Long, user: User): Ticket? {
        val t = queryTicket(readableDatabase, id) ?: return null
        return if (TicketRules.canView(t, user)) t else null
    }

    /** Device history (technicians only: the Devices screens are TECH-only). */
    fun getTicketsForDevice(deviceId: Long): List<Ticket> {
        val out = ArrayList<Ticket>()
        readableDatabase.rawQuery("$TICKET_SQL WHERE t.device_id = ? ORDER BY t.created_at DESC",
            arrayOf(deviceId.toString())).use { while (it.moveToNext()) out.add(readTicket(it)) }
        return out
    }

    // ------------------------------------------------------------------ tickets (write)

    /** Creates a ticket + its first history row (null -> OPEN) in one transaction. Returns the id, or -1. */
    fun createTicket(user: User, title: String, description: String, categoryId: Long,
                     deviceId: Long?, priority: Priority): Long {
        if (user.role != Role.USER) return -1L     // only USER accounts report problems
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put("title", title.trim()); put("description", description.trim())
                put("category_id", categoryId)
                if (deviceId != null) put("device_id", deviceId) else putNull("device_id")
                put("priority", priority.name); put("status", Status.OPEN.name)
                put("created_by", user.id)
                put("created_at", now); put("updated_at", now)
                put("due_at", SlaPolicy.dueAt(now, priority))   // SLA deadline computed at creation
            }
            val id = db.insertOrThrow("tickets", null, cv)
            insertHistory(db, id, user.id, null, Status.OPEN, null, now)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    private fun insertHistory(db: SQLiteDatabase, ticketId: Long, by: Long, old: Status?,
                              new: Status, note: String?, at: Long) {
        val cv = ContentValues().apply {
            put("ticket_id", ticketId); put("changed_by", by)
            if (old != null) put("old_status", old.name) else putNull("old_status")
            put("new_status", new.name)
            if (note.isNullOrBlank()) putNull("note") else put("note", note.trim())
            put("changed_at", at)
        }
        db.insertOrThrow("ticket_history", null, cv)
    }

    /**
     * THE one place where a ticket changes status.
     *  1. load the ticket   2. TicketRules.check()   3. SLA side effects
     *  4. UPDATE ticket + INSERT history  -- all in the same transaction (all or nothing).
     * Returns null on success, or the rule that was broken.
     */
    fun changeStatus(ticketId: Long, actor: User, to: Status, note: String?): Violation? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val t = queryTicket(db, ticketId) ?: return Violation.NOT_FOUND
            TicketRules.check(t, actor, to, note)?.let { return it }

            val now = System.currentTimeMillis()
            val e = TicketRules.effects(t, to, actor.id, now)
            val cv = ContentValues().apply {
                put("status", to.name); put("updated_at", now); put("due_at", e.dueAt)
                if (e.assignedTo != null) put("assigned_to", e.assignedTo) else putNull("assigned_to")
                if (e.pausedAt != null) put("paused_at", e.pausedAt) else putNull("paused_at")
                if (e.resolvedAt != null) put("resolved_at", e.resolvedAt) else putNull("resolved_at")
                if (e.closedAt != null) put("closed_at", e.closedAt) else putNull("closed_at")
                if (to == Status.RESOLVED) put("resolution_note", note!!.trim())
            }
            db.update("tickets", cv, "id = ?", arrayOf(ticketId.toString()))
            insertHistory(db, ticketId, actor.id, t.status, to, note, now)
            db.setTransactionSuccessful()
            return null
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------ comments, history, rating

    /**
     * Adds a comment. BUSINESS RULE: if the OWNER replies on a WAITING_FOR_USER ticket, the ticket
     * automatically goes back to IN_PROGRESS (and the history records it) in the same transaction.
     */
    fun addComment(ticketId: Long, author: User, message: String): Violation? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val t = queryTicket(db, ticketId) ?: return Violation.NOT_FOUND
            if (!TicketRules.canComment(t, author)) return Violation.CANNOT_COMMENT
            if (message.isBlank()) return Violation.EMPTY_COMMENT
            val cv = ContentValues().apply {
                put("ticket_id", ticketId); put("author_id", author.id)
                put("message", message.trim()); put("created_at", System.currentTimeMillis())
            }
            db.insertOrThrow("comments", null, cv)
            if (t.status == Status.WAITING_FOR_USER && t.createdBy == author.id) {
                changeStatus(ticketId, author, Status.IN_PROGRESS,
                    appContext.getString(R.string.history_user_replied))?.let { return it }
            }
            db.setTransactionSuccessful()
            return null
        } finally {
            db.endTransaction()
        }
    }

    fun getComments(ticketId: Long): List<Comment> {
        val out = ArrayList<Comment>()
        readableDatabase.rawQuery(
            "SELECT m.id, m.ticket_id, m.author_id, m.message, m.created_at, u.name, u.role " +
                    "FROM comments m JOIN users u ON u.id = m.author_id WHERE m.ticket_id = ? ORDER BY m.id",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Comment(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(5),
                Role.valueOf(c.getString(6)), c.getString(3), c.getLong(4)))
        }
        return out
    }

    fun getHistory(ticketId: Long): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        readableDatabase.rawQuery(
            "SELECT h.id, h.ticket_id, u.name, h.old_status, h.new_status, h.note, h.changed_at " +
                    "FROM ticket_history h JOIN users u ON u.id = h.changed_by WHERE h.ticket_id = ? ORDER BY h.id",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(HistoryEntry(c.getLong(0), c.getLong(1), c.getString(2),
                c.getString(3)?.let { Status.valueOf(it) }, Status.valueOf(c.getString(4)),
                c.getString(5), c.getLong(6)))
        }
        return out
    }

    /** BUSINESS RULE: rating 1..5, only on a CLOSED ticket, only by its owner, only once. */
    fun rateTicket(ticketId: Long, user: User, stars: Int): Violation? {
        if (stars !in 1..5) return Violation.BAD_RATING
        val db = writableDatabase
        val t = queryTicket(db, ticketId) ?: return Violation.NOT_FOUND
        if (t.status != Status.CLOSED || t.createdBy != user.id) return Violation.CANNOT_RATE
        if (t.rating != null) return Violation.ALREADY_RATED
        val cv = ContentValues().apply { put("rating", stars); put("updated_at", System.currentTimeMillis()) }
        db.update("tickets", cv, "id = ? AND rating IS NULL", arrayOf(ticketId.toString()))
        return null
    }

    // ------------------------------------------------------------------ demo data

    private fun idOf(db: SQLiteDatabase, table: String, col: String, value: String): Long =
        db.rawQuery("SELECT id FROM $table WHERE $col = ?", arrayOf(value)).use { it.moveToFirst(); it.getLong(0) }

    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (n in listOf("Hardware", "Network", "Software", "Account/Access", "Printer", "Other")) {
            db.insertOrThrow("categories", null, ContentValues().apply { put("name", n) })
        }
        // Demo accounts: 1 technician + 1 user.
        val mohamed = insertUser(db, "Mohamed", "tech1@fixit.local", "Tech1234", Role.TECH)
        val hela = insertUser(db, "Hela", "hela@fixit.local", "User1234", Role.USER)

        // 14 devices in 5 rooms
        fun dev(code: String, type: DeviceType, model: String, room: String) {
            db.insertOrThrow("devices", null, ContentValues().apply {
                put("code", code); put("type", type.name); put("model", model); put("location", room)
            })
        }
        dev("PC-B2-001", DeviceType.PC, "Dell OptiPlex 3080", "Lab B2")
        dev("PC-B2-002", DeviceType.PC, "Dell OptiPlex 3080", "Lab B2")
        dev("PC-B2-003", DeviceType.PC, "Dell OptiPlex 3080", "Lab B2")
        dev("PRJ-B2-001", DeviceType.PROJECTOR, "Epson EB-X49", "Lab B2")
        dev("PC-B3-001", DeviceType.PC, "HP ProDesk 400", "Lab B3")
        dev("PC-B3-002", DeviceType.PC, "HP ProDesk 400", "Lab B3")
        dev("PRN-B3-001", DeviceType.PRINTER, "HP LaserJet Pro M404", "Lab B3")
        dev("PRJ-C1-001", DeviceType.PROJECTOR, "BenQ MX550", "Classroom C1")
        dev("PC-C1-001", DeviceType.PC, "Lenovo ThinkCentre M70", "Classroom C1")
        dev("RTR-C2-001", DeviceType.ROUTER, "TP-Link Archer C6", "Classroom C2")
        dev("PC-C2-001", DeviceType.PC, "Lenovo ThinkCentre M70", "Classroom C2")
        dev("PRN-OF-001", DeviceType.PRINTER, "Canon i-SENSYS MF445", "Admin Office")
        dev("PC-OF-001", DeviceType.PC, "HP EliteDesk 800", "Admin Office")
        dev("RTR-OF-001", DeviceType.ROUTER, "Cisco RV340", "Admin Office")

        // 3 demo tickets. Times are "minutes ago".
        seedTicket(
            db = db, now = now, title = "Computer will not boot",
            desc = "Black screen after pressing the power button.",
            category = "Hardware", deviceCode = "PC-B2-001",
            priority = Priority.CRITICAL, status = Status.IN_PROGRESS,
            owner = hela, tech = mohamed, createdAgoMin = 20
        )
        seedTicket(
            db = db, now = now, title = "Printer paper jam",
            desc = "Paper is stuck and the tray will not close.",
            category = "Printer", deviceCode = "PRN-B3-001",
            priority = Priority.HIGH, status = Status.ASSIGNED,
            owner = hela, tech = mohamed, createdAgoMin = 60
        )
        seedTicket(
            db = db, now = now, title = "Wi-Fi unavailable in room C2",
            desc = "Nobody can connect since this morning.",
            category = "Network", deviceCode = "RTR-C2-001",
            priority = Priority.MEDIUM, status = Status.OPEN,
            owner = hela, tech = null, createdAgoMin = 30
        )
    }

    private fun seedTicket(
        db: SQLiteDatabase, now: Long, title: String, desc: String, category: String, deviceCode: String?,
        priority: Priority, status: Status, owner: Long, tech: Long?, createdAgoMin: Long,
        pausedAgoMin: Long? = null, resolvedAgoMin: Long? = null, closedAgoMin: Long? = null,
        note: String? = null, rating: Int? = null
    ): Long {
        val min = 60_000L
        val created = now - createdAgoMin * min
        val paused = pausedAgoMin?.let { now - it * min }
        val resolved = resolvedAgoMin?.let { now - it * min }
        val closed = closedAgoMin?.let { now - it * min }
        val cv = ContentValues().apply {
            put("title", title); put("description", desc)
            put("category_id", idOf(db, "categories", "name", category))
            if (deviceCode != null) put("device_id", idOf(db, "devices", "code", deviceCode)) else putNull("device_id")
            put("priority", priority.name); put("status", status.name); put("created_by", owner)
            if (tech != null) put("assigned_to", tech) else putNull("assigned_to")
            put("created_at", created); put("updated_at", closed ?: resolved ?: paused ?: now)
            put("due_at", SlaPolicy.dueAt(created, priority))
            if (paused != null) put("paused_at", paused) else putNull("paused_at")
            if (resolved != null) put("resolved_at", resolved) else putNull("resolved_at")
            if (closed != null) put("closed_at", closed) else putNull("closed_at")
            if (note != null) put("resolution_note", note) else putNull("resolution_note")
            if (rating != null) put("rating", rating) else putNull("rating")
        }
        val id = db.insertOrThrow("tickets", null, cv)

        // Matching history rows: one per step of the path.
        insertHistory(db, id, owner, null, Status.OPEN, null, created)
        val path = when (status) {
            Status.OPEN -> emptyList()
            Status.ASSIGNED -> listOf(Status.ASSIGNED)
            Status.IN_PROGRESS -> listOf(Status.ASSIGNED, Status.IN_PROGRESS)
            Status.WAITING_FOR_USER -> listOf(Status.ASSIGNED, Status.IN_PROGRESS, Status.WAITING_FOR_USER)
            Status.RESOLVED -> listOf(Status.ASSIGNED, Status.IN_PROGRESS, Status.RESOLVED)
            Status.CLOSED -> listOf(Status.ASSIGNED, Status.IN_PROGRESS, Status.RESOLVED, Status.CLOSED)
            Status.CANCELLED -> listOf(Status.CANCELLED)
        }
        var previous = Status.OPEN
        path.forEachIndexed { i, s ->
            val at = when (s) {
                Status.RESOLVED -> resolved!!
                Status.CLOSED -> closed!!
                Status.WAITING_FOR_USER -> paused!!
                else -> created + (i + 1) * 5 * min
            }
            val by = if (s == Status.CLOSED || s == Status.CANCELLED) owner else tech!!
            insertHistory(db, id, by, previous, s, if (s == Status.RESOLVED) note else null, at)
            previous = s
        }
        return id
    }
}