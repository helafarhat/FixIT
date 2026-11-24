package com.fixit.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fixit.app.R
import com.fixit.app.logic.SlaPolicy
import com.fixit.app.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- labels (enum -> string resource)

fun Status.labelRes(): Int = when (this) {
    Status.OPEN -> R.string.status_open
    Status.ASSIGNED -> R.string.status_assigned
    Status.IN_PROGRESS -> R.string.status_in_progress
    Status.WAITING_FOR_USER -> R.string.status_waiting
    Status.RESOLVED -> R.string.status_resolved
    Status.CLOSED -> R.string.status_closed
    Status.CANCELLED -> R.string.status_cancelled
}

fun Priority.labelRes(): Int = when (this) {
    Priority.CRITICAL -> R.string.priority_critical
    Priority.HIGH -> R.string.priority_high
    Priority.MEDIUM -> R.string.priority_medium
    Priority.LOW -> R.string.priority_low
}

fun DeviceType.labelRes(): Int = when (this) {
    DeviceType.PC -> R.string.type_pc
    DeviceType.PROJECTOR -> R.string.type_projector
    DeviceType.PRINTER -> R.string.type_printer
    DeviceType.ROUTER -> R.string.type_router
    DeviceType.OTHER -> R.string.type_other
}

fun Role.labelRes(): Int = when (this) {
    Role.USER -> R.string.role_user
    Role.TECH -> R.string.role_tech
}

/** Friendly message for every business-rule refusal. */
fun Violation.messageRes(): Int = when (this) {
    Violation.BAD_TRANSITION -> R.string.err_bad_transition
    Violation.TECH_ONLY -> R.string.err_tech_only
    Violation.NOT_OWNER -> R.string.err_not_owner
    Violation.NOT_ASSIGNED -> R.string.err_not_assigned
    Violation.ALREADY_TAKEN -> R.string.err_already_taken
    Violation.NOTE_REQUIRED -> R.string.err_note_required
    Violation.NOT_FOUND -> R.string.err_ticket_not_found
    Violation.CANNOT_COMMENT -> R.string.err_cannot_comment
    Violation.EMPTY_COMMENT -> R.string.err_empty_comment
    Violation.CANNOT_RATE -> R.string.err_cannot_rate
    Violation.ALREADY_RATED -> R.string.err_already_rated
    Violation.BAD_RATING -> R.string.err_pick_rating
    Violation.EMAIL_TAKEN -> R.string.err_email_taken
    Violation.CODE_TAKEN -> R.string.err_code_taken
    Violation.USER_ONLY -> R.string.err_user_only
    Violation.BAD_TECH_CODE -> R.string.err_bad_tech_code
}

// ---------------------------------------------------------------- chips

data class ChipStyle(@ColorRes val bg: Int, @ColorRes val fg: Int)

fun Status.chipStyle(): ChipStyle = when (this) {
    Status.OPEN -> ChipStyle(R.color.status_open_bg, R.color.status_open_fg)
    Status.ASSIGNED -> ChipStyle(R.color.status_assigned_bg, R.color.status_assigned_fg)
    Status.IN_PROGRESS -> ChipStyle(R.color.status_progress_bg, R.color.status_progress_fg)
    Status.WAITING_FOR_USER -> ChipStyle(R.color.status_waiting_bg, R.color.status_waiting_fg)
    Status.RESOLVED -> ChipStyle(R.color.status_resolved_bg, R.color.status_resolved_fg)
    Status.CLOSED -> ChipStyle(R.color.status_closed_bg, R.color.status_closed_fg)
    Status.CANCELLED -> ChipStyle(R.color.status_cancelled_bg, R.color.status_cancelled_fg)
}

fun Priority.chipStyle(): ChipStyle = when (this) {
    Priority.CRITICAL -> ChipStyle(R.color.priority_critical_bg, R.color.priority_critical_fg)
    Priority.HIGH -> ChipStyle(R.color.priority_high_bg, R.color.priority_high_fg)
    Priority.MEDIUM -> ChipStyle(R.color.priority_medium_bg, R.color.priority_medium_fg)
    Priority.LOW -> ChipStyle(R.color.priority_low_bg, R.color.priority_low_fg)
}

/** Shows [text] in a pill coloured from the given colour resources. */
fun TextView.applyChip(text: CharSequence, style: ChipStyle) {
    this.text = text
    setTextColor(ContextCompat.getColor(context, style.fg))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, style.bg))
}

/**
 * Deadline chip. State is ALWAYS written as text ("3h 20m left", "Overdue by 40m", ...),
 * the colour is only a bonus. Hidden for finished tickets.
 */
fun TextView.applySla(t: Ticket, now: Long) {
    val info = SlaPolicy.evaluate(t, now)
    val d = SlaPolicy.formatDuration(info.remainingMs)
    when (info.state) {
        SlaPolicy.State.NONE -> { visibility = View.GONE; return }
        SlaPolicy.State.ON_TRACK ->
            applyChip(context.getString(R.string.sla_on_track, d), ChipStyle(R.color.sla_ok_bg, R.color.sla_ok_fg))
        SlaPolicy.State.DUE_SOON ->
            applyChip(context.getString(R.string.sla_due_soon, d), ChipStyle(R.color.sla_soon_bg, R.color.sla_soon_fg))
        SlaPolicy.State.OVERDUE ->
            applyChip(context.getString(R.string.sla_overdue, d), ChipStyle(R.color.sla_over_bg, R.color.sla_over_fg))
        SlaPolicy.State.PAUSED ->
            applyChip(context.getString(R.string.sla_paused), ChipStyle(R.color.sla_paused_bg, R.color.sla_paused_fg))
    }
    visibility = View.VISIBLE
}

fun formatDateTime(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

// ---------------------------------------------------------------- ticket row (list + home + device history)

fun bindTicketRow(v: View, t: Ticket, now: Long) {
    v.findViewById<TextView>(R.id.tvTitle).text = t.title
    val where = t.deviceCode ?: v.context.getString(R.string.no_device_short)
    v.findViewById<TextView>(R.id.tvMeta).text =
        v.context.getString(R.string.ticket_row_meta, t.id, t.categoryName, where)
    v.findViewById<TextView>(R.id.chipStatus).applyChip(v.context.getString(t.status.labelRes()), t.status.chipStyle())
    v.findViewById<TextView>(R.id.chipPriority).applyChip(v.context.getString(t.priority.labelRes()), t.priority.chipStyle())
    v.findViewById<TextView>(R.id.chipSla).applySla(t, now)
    // Coloured stripe on the left edge = priority at a glance (the text chip still says it too).
    v.findViewById<View>(R.id.priorityStripe).setBackgroundColor(
        ContextCompat.getColor(v.context, t.priority.chipStyle().fg))
}

class TicketAdapter(private val ctx: Context, private var items: List<Ticket>) : BaseAdapter() {
    fun update(newItems: List<Ticket>) { items = newItems; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int): Ticket = items[position]
    override fun getItemId(position: Int): Long = items[position].id
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.item_ticket, parent, false)
        bindTicketRow(v, items[position], System.currentTimeMillis())   // SLA text recomputed on every redraw
        return v
    }
}

class DeviceAdapter(private val ctx: Context, private var items: List<Device>) : BaseAdapter() {
    fun update(newItems: List<Device>) { items = newItems; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int): Device = items[position]
    override fun getItemId(position: Int): Long = items[position].id
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.item_device, parent, false)
        val d = items[position]
        v.findViewById<TextView>(R.id.tvDeviceCode).text = d.code
        v.findViewById<TextView>(R.id.tvDeviceInfo).text =
            ctx.getString(R.string.device_info_fmt, ctx.getString(d.type.labelRes()), d.model)
        v.findViewById<TextView>(R.id.tvDeviceLocation).text = d.location
        return v
    }
}

// ---------------------------------------------------------------- live countdown

/**
 * Calls [onTick] every ~30 s. Screens call start() in onResume and stop() in onPause,
 * so nothing runs in the background (no leaks, no battery drain).
 */
class LiveTicker(private val everyMs: Long = 30_000L, private val onTick: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() { onTick(); handler.postDelayed(this, everyMs) }
    }
    fun start() { handler.removeCallbacks(runnable); handler.postDelayed(runnable, everyMs) }
    fun stop() { handler.removeCallbacks(runnable) }
}

// ---------------------------------------------------------------- empty state, dialogs

/** Fills the included layout_empty_state with a title and a text. */
fun View.setEmptyTexts(titleRes: Int, textRes: Int) {
    findViewById<TextView>(R.id.tvEmptyTitle).setText(titleRes)
    findViewById<TextView>(R.id.tvEmptyText).setText(textRes)
}

/** Dialog with one text field. If [required] and empty, shows an inline error and stays open. */
fun AppCompatActivity.askInput(titleRes: Int, hintRes: Int, required: Boolean,
                               capitals: Boolean = false, onOk: (String) -> Unit) {
    val view = layoutInflater.inflate(R.layout.dialog_input, null)
    val til = view.findViewById<TextInputLayout>(R.id.tilInput)
    til.hint = getString(hintRes)
    if (capitals) til.editText?.inputType = android.text.InputType.TYPE_CLASS_TEXT or
        android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(titleRes).setView(view)
        .setPositiveButton(R.string.action_ok, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = til.text()
            if (required && text.isBlank()) {
                til.showFieldError(getString(R.string.err_required))
            } else {
                dialog.dismiss()
                onOk(text)
            }
        }
    }
    dialog.show()
}

/** Confirmation before a destructive action. */
fun AppCompatActivity.confirm(messageRes: Int, onYes: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setMessage(messageRes)
        .setPositiveButton(R.string.action_yes) { _, _ -> onYes() }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
