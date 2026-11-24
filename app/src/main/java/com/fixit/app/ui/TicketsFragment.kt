package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.SlaPolicy
import com.fixit.app.model.Priority
import com.fixit.app.model.Role
import com.fixit.app.model.Status
import com.fixit.app.model.Ticket
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

/** Ticket list with status chips, search, priority filter and sort. */
class TicketsFragment : Fragment(R.layout.fragment_tickets) {

    private val me get() = (requireActivity() as MainActivity).me
    private lateinit var adapter: TicketAdapter
    private var all: List<Ticket> = emptyList()

    private var query = ""
    private var statusFilter: Status? = null
    private var priorityFilter: Priority? = null
    private var urgentFirst = false
    private val ticker = LiveTicker { render() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = TicketAdapter(requireContext(), emptyList())
        val list = view.findViewById<ListView>(R.id.listTickets)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(requireContext(), TicketDetailActivity::class.java)
                .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, adapter.getItemId(position)))
        }

        view.findViewById<TextInputEditText>(R.id.etSearch).doAfterTextChanged {
            query = it?.toString().orEmpty().trim(); render()
        }

        view.findViewById<ChipGroup>(R.id.chipGroupStatus).setOnCheckedStateChangeListener { _, ids ->
            statusFilter = when (ids.firstOrNull()) {
                R.id.chipOpen -> Status.OPEN
                R.id.chipAssigned -> Status.ASSIGNED
                R.id.chipProgress -> Status.IN_PROGRESS
                R.id.chipWaiting -> Status.WAITING_FOR_USER
                R.id.chipResolved -> Status.RESOLVED
                R.id.chipClosed -> Status.CLOSED
                R.id.chipCancelled -> Status.CANCELLED
                else -> null            // "All"
            }
            render()
        }

        val spPriority = view.findViewById<Spinner>(R.id.spPriorityFilter)
        spPriority.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.priority_filter_options,
            android.R.layout.simple_spinner_item).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                priorityFilter = if (pos == 0) null else Priority.values()[pos - 1]   // 0 = "All priorities"
                render()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val spSort = view.findViewById<Spinner>(R.id.spSort)
        spSort.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.sort_options,
            android.R.layout.simple_spinner_item).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                urgentFirst = pos == 1; render()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val fab = view.findViewById<FloatingActionButton>(R.id.fabNewTicket)
        fab.visibility = if (me.role == Role.USER) View.VISIBLE else View.GONE
        fab.setOnClickListener { startActivity(Intent(requireContext(), NewTicketActivity::class.java)) }
        view.findViewById<View>(R.id.emptyState).setEmptyTexts(R.string.empty_tickets_title, R.string.empty_tickets_text)
    }

    override fun onResume() {
        super.onResume()
        all = DBHelper.get(requireContext()).getTicketsFor(me)
        render()
        ticker.start()
    }

    override fun onPause() {
        ticker.stop()
        super.onPause()
    }

    private fun matchesQuery(t: Ticket): Boolean {
        if (query.isEmpty()) return true
        return t.title.contains(query, true) || t.categoryName.contains(query, true) ||
            (t.deviceCode?.contains(query, true) == true) || t.id.toString() == query
    }

    private fun render() {
        val v = view ?: return
        val now = System.currentTimeMillis()
        var shown = all.filter {
            (statusFilter == null || it.status == statusFilter) &&
                (priorityFilter == null || it.priority == priorityFilter) && matchesQuery(it)
        }
        shown = if (urgentFirst) shown.sortedBy { SlaPolicy.urgencyKey(it, now) }
        else shown.sortedByDescending { it.createdAt }
        adapter.update(shown)
        v.findViewById<View>(R.id.emptyState).visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        v.findViewById<ListView>(R.id.listTickets).visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
    }
}
