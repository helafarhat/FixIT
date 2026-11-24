package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.SlaPolicy
import com.fixit.app.model.Role
import com.fixit.app.model.Status

/** Dashboard: Overdue / Due soon counters, quick actions and the most urgent tickets. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val me get() = (requireActivity() as MainActivity).me
    private val ticker = LiveTicker { refresh() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isTech = me.role == Role.TECH
        view.findViewById<TextView>(R.id.tvGreeting).text = getString(R.string.home_greeting, me.name)
        view.findViewById<TextView>(R.id.chipHomeRole).applyChip(
            getString(me.role.labelRes()), ChipStyle(R.color.primary_container, R.color.primary_dark))
        view.findViewById<TextView>(R.id.tvSubtitle).setText(
            if (isTech) R.string.home_subtitle_tech else R.string.home_subtitle_user)
        view.findViewById<TextView>(R.id.tvThirdLabel).setText(
            if (isTech) R.string.home_unassigned else R.string.home_active)

        view.findViewById<Button>(R.id.btnScan).setOnClickListener {
            startActivity(Intent(requireContext(), ScanActivity::class.java))
        }
        val second = view.findViewById<Button>(R.id.btnSecondary)
        second.setText(if (isTech) R.string.action_devices else R.string.action_new_ticket)
        second.setOnClickListener {
            startActivity(Intent(requireContext(),
                if (isTech) DevicesActivity::class.java else NewTicketActivity::class.java))
        }
        view.findViewById<View>(R.id.emptyState).setEmptyTexts(R.string.empty_urgent_title, R.string.empty_urgent_text)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ticker.start()      // live countdown: only while the screen is visible
    }

    override fun onPause() {
        ticker.stop()
        super.onPause()
    }

    private fun refresh() {
        val v = view ?: return
        val now = System.currentTimeMillis()
        val tickets = DBHelper.get(requireContext()).getTicketsFor(me)

        v.findViewById<TextView>(R.id.tvOverdueCount).text =
            SlaPolicy.countState(tickets, now, SlaPolicy.State.OVERDUE).toString()
        v.findViewById<TextView>(R.id.tvSoonCount).text =
            SlaPolicy.countState(tickets, now, SlaPolicy.State.DUE_SOON).toString()
        val third = if (me.role == Role.TECH) tickets.count { it.status == Status.OPEN }
        else tickets.count { SlaPolicy.isActive(it.status) }
        v.findViewById<TextView>(R.id.tvThirdCount).text = third.toString()

        // The 3 most urgent active tickets (soonest deadline first).
        val urgent = tickets.filter { SlaPolicy.isActive(it.status) }
            .sortedBy { SlaPolicy.urgencyKey(it, now) }.take(3)
        val container = v.findViewById<LinearLayout>(R.id.llUrgent)
        container.removeAllViews()
        for (t in urgent) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_ticket, container, false)
            bindTicketRow(row, t, now)
            row.setOnClickListener {
                startActivity(Intent(requireContext(), TicketDetailActivity::class.java)
                    .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, t.id))
            }
            container.addView(row)
        }
        v.findViewById<View>(R.id.emptyState).visibility = if (urgent.isEmpty()) View.VISIBLE else View.GONE
    }
}
