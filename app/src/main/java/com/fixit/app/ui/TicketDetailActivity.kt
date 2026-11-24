package com.fixit.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.TicketRules
import com.fixit.app.logic.TicketRules.Action
import com.fixit.app.model.Role
import com.fixit.app.model.Status
import com.fixit.app.model.Ticket
import com.fixit.app.model.User
import com.google.android.material.textfield.TextInputLayout

/**
 * Ticket details: info card, status timeline, actions (depend on role + status), rating,
 * comments and history. The SLA chip ticks live while the screen is visible.
 */
class TicketDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_TICKET_ID = "ticket_id" }

    private lateinit var db: DBHelper
    private lateinit var me: User
    private var ticketId = 0L
    private var ticket: Ticket? = null

    private val ticker = LiveTicker {
        ticket?.let { findViewById<TextView>(R.id.chipSla).applySla(it, System.currentTimeMillis()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        me = requireLogin() ?: return
        setContentView(R.layout.activity_ticket_detail)
        setupScreen(R.string.title_ticket)
        db = DBHelper.get(this)
        ticketId = intent.getLongExtra(EXTRA_TICKET_ID, 0L)

        findViewById<Button>(R.id.btnRate).setOnClickListener { submitRating() }
        findViewById<Button>(R.id.btnSendComment).setOnClickListener { sendComment() }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) { load(); ticker.start() }
    }

    override fun onPause() {
        ticker.stop()
        super.onPause()
    }

    private fun load() {
        // Returns null if the ticket does not exist OR belongs to another user.
        val t = db.getTicketForUser(ticketId, me)
        if (t == null) { toast(R.string.err_ticket_not_found); finish(); return }
        ticket = t
        render(t)
    }

    private fun render(t: Ticket) {
        val now = System.currentTimeMillis()
        findViewById<TextView>(R.id.tvTicketTitle).text = t.title
        findViewById<TextView>(R.id.chipStatus).applyChip(getString(t.status.labelRes()), t.status.chipStyle())
        findViewById<TextView>(R.id.chipPriority).applyChip(getString(t.priority.labelRes()), t.priority.chipStyle())
        findViewById<TextView>(R.id.chipSla).applySla(t, now)

        findViewById<TextView>(R.id.tvCategory).text = getString(R.string.detail_category, t.categoryName)
        findViewById<TextView>(R.id.tvDevice).text =
            if (t.deviceCode != null) getString(R.string.detail_device, t.deviceCode, t.deviceModel, t.deviceLocation)
            else getString(R.string.detail_no_device)
        findViewById<TextView>(R.id.tvOwner).text = getString(R.string.detail_owner, t.ownerName)
        findViewById<TextView>(R.id.tvTech).text =
            getString(R.string.detail_tech, t.techName ?: getString(R.string.detail_unassigned))
        findViewById<TextView>(R.id.tvCreated).text = getString(R.string.detail_created, formatDateTime(t.createdAt))
        findViewById<TextView>(R.id.tvDue).text = getString(R.string.detail_due, formatDateTime(t.dueAt))
        findViewById<TextView>(R.id.tvDescription).text = t.description

        val note = findViewById<TextView>(R.id.tvResolution)
        note.visibility = if (t.resolutionNote.isNullOrBlank()) View.GONE else View.VISIBLE
        note.text = getString(R.string.detail_resolution, t.resolutionNote)

        renderTimeline(t)
        renderActions(t)
        renderRating(t)
        renderComments(t)
        renderHistory()
    }

    // ------------------------------------------------------------- timeline

    private fun renderTimeline(t: Ticket) {
        val ll = findViewById<LinearLayout>(R.id.llTimeline)
        ll.removeAllViews()
        if (t.status == Status.CANCELLED) {
            ll.addView(stepView(ll, getString(R.string.status_cancelled), t.status.chipStyle()))
            return
        }
        val steps = listOf(Status.OPEN, Status.ASSIGNED, Status.IN_PROGRESS, Status.RESOLVED, Status.CLOSED)
        val current = when (t.status) {
            Status.OPEN -> 0
            Status.ASSIGNED -> 1
            Status.IN_PROGRESS, Status.WAITING_FOR_USER -> 2
            Status.RESOLVED -> 3
            else -> 4
        }
        steps.forEachIndexed { i, s ->
            val label = if (i == current && t.status == Status.WAITING_FOR_USER) getString(R.string.status_waiting)
            else getString(s.labelRes())
            val style = when {
                i < current -> ChipStyle(R.color.step_done_bg, R.color.step_done_fg)
                i == current -> ChipStyle(R.color.primary, R.color.on_primary)
                else -> ChipStyle(R.color.border, R.color.ink_secondary)
            }
            ll.addView(stepView(ll, label, style))
        }
    }

    private fun stepView(parent: LinearLayout, text: String, style: ChipStyle): TextView {
        val tv = LayoutInflater.from(this).inflate(R.layout.item_step, parent, false) as TextView
        tv.applyChip(text, style)
        return tv
    }

    // ------------------------------------------------------------- actions

    private fun renderActions(t: Ticket) {
        val ll = findViewById<LinearLayout>(R.id.llActions)
        ll.removeAllViews()
        // Only the actions this person may do right now (TicketRules decides; DBHelper re-checks on click).
        for (action in TicketRules.availableActions(t, me)) {
            val primary = action == Action.TAKE || action == Action.START || action == Action.RESOLVE ||
                action == Action.CLOSE || action == Action.RESUME
            val button = LayoutInflater.from(this)
                .inflate(if (primary) R.layout.item_action_primary else R.layout.item_action_secondary, ll, false) as Button
            button.setText(actionLabel(action))
            button.setOnClickListener { onAction(action) }
            ll.addView(button)
        }
        findViewById<View>(R.id.tvActionsTitle).visibility = if (ll.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun actionLabel(a: Action): Int = when (a) {
        Action.TAKE -> R.string.action_take
        Action.START -> R.string.action_start
        Action.ASK_USER -> R.string.action_ask_user
        Action.RESUME -> R.string.action_resume
        Action.RESOLVE -> R.string.action_resolve
        Action.CLOSE -> R.string.action_close
        Action.REOPEN -> R.string.action_reopen
        Action.CANCEL -> R.string.action_cancel_ticket
    }

    private fun onAction(a: Action) {
        when (a) {
            Action.TAKE, Action.START, Action.RESUME -> perform(a, null)
            Action.ASK_USER -> askInput(R.string.dlg_ask_title, R.string.dlg_note_hint, false) { perform(a, it) }
            Action.REOPEN -> askInput(R.string.dlg_reopen_title, R.string.dlg_note_hint, false) { perform(a, it) }
            // Resolution note is required (also enforced in TicketRules).
            Action.RESOLVE -> askInput(R.string.dlg_resolve_title, R.string.dlg_resolution_hint, true) { perform(a, it) }
            Action.CLOSE -> confirm(R.string.confirm_close) { perform(a, null) }
            Action.CANCEL -> confirm(R.string.confirm_cancel_ticket) { perform(a, null) }
        }
    }

    private fun perform(a: Action, note: String?) {
        val violation = db.changeStatus(ticketId, me, a.target, note)
        if (violation != null) toast(violation.messageRes()) else toast(R.string.msg_status_updated)
        load()
    }

    // ------------------------------------------------------------- rating

    private fun renderRating(t: Ticket) {
        val card = findViewById<View>(R.id.cardRating)
        val bar = findViewById<RatingBar>(R.id.ratingBar)
        val btn = findViewById<Button>(R.id.btnRate)
        val shown = findViewById<TextView>(R.id.tvRated)
        when {
            TicketRules.canRate(t, me) -> {
                card.visibility = View.VISIBLE
                bar.visibility = View.VISIBLE; btn.visibility = View.VISIBLE; shown.visibility = View.GONE
            }
            t.status == Status.CLOSED && t.rating != null -> {
                card.visibility = View.VISIBLE
                bar.visibility = View.GONE; btn.visibility = View.GONE
                shown.visibility = View.VISIBLE
                shown.text = getString(R.string.rating_given, t.rating)
            }
            else -> card.visibility = View.GONE
        }
    }

    private fun submitRating() {
        val stars = findViewById<RatingBar>(R.id.ratingBar).rating.toInt()
        val violation = db.rateTicket(ticketId, me, stars)
        if (violation != null) toast(violation.messageRes()) else toast(R.string.msg_rating_saved)
        load()
    }

    // ------------------------------------------------------------- comments

    private fun renderComments(t: Ticket) {
        val ll = findViewById<LinearLayout>(R.id.llComments)
        ll.removeAllViews()
        val comments = db.getComments(ticketId)
        for (c in comments) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_comment, ll, false)
            row.findViewById<TextView>(R.id.tvCommentAuthor).text =
                if (c.authorRole == Role.TECH) getString(R.string.comment_author_tech, c.authorName) else c.authorName
            row.findViewById<TextView>(R.id.tvCommentText).text = c.message
            row.findViewById<TextView>(R.id.tvCommentDate).text = formatDateTime(c.createdAt)
            ll.addView(row)
        }
        findViewById<View>(R.id.tvNoComments).visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.llAddComment).visibility = if (TicketRules.canComment(t, me)) View.VISIBLE else View.GONE
    }

    private fun sendComment() {
        val til = findViewById<TextInputLayout>(R.id.tilComment)
        val text = til.text()
        if (text.isEmpty()) { til.showFieldError(getString(R.string.err_empty_comment)); return }
        til.showFieldError(null)
        val violation = db.addComment(ticketId, me, text)
        if (violation != null) { toast(violation.messageRes()); return }
        til.editText?.setText("")
        load()      // a reply on a WAITING_FOR_USER ticket moves it back to IN_PROGRESS: refresh everything
    }

    // ------------------------------------------------------------- history

    private fun renderHistory() {
        val ll = findViewById<LinearLayout>(R.id.llHistory)
        ll.removeAllViews()
        for (h in db.getHistory(ticketId)) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_history, ll, false)
            val newLabel = getString(h.newStatus.labelRes())
            row.findViewById<TextView>(R.id.tvHistoryTitle).text =
                if (h.oldStatus == null) getString(R.string.history_created, newLabel)
                else getString(R.string.history_change, getString(h.oldStatus.labelRes()), newLabel)
            row.findViewById<TextView>(R.id.tvHistoryMeta).text =
                getString(R.string.history_meta, h.changedByName, formatDateTime(h.changedAt))
            val note = row.findViewById<TextView>(R.id.tvHistoryNote)
            note.visibility = if (h.note.isNullOrBlank()) View.GONE else View.VISIBLE
            note.text = h.note
            ll.addView(row)
        }
    }
}
