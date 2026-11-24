package com.fixit.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.SlaPolicy

/** Technician statistics: % resolved within SLA, average resolution time, overdue count. */
class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val me get() = (requireActivity() as MainActivity).me
    private val ticker = LiveTicker { refresh() }

    override fun onResume() {
        super.onResume()
        refresh()
        ticker.start()
    }

    override fun onPause() {
        ticker.stop()
        super.onPause()
    }

    private fun refresh() {
        val v = view ?: return
        val s = SlaPolicy.stats(DBHelper.get(requireContext()).getTicketsFor(me), System.currentTimeMillis())
        v.findViewById<TextView>(R.id.tvStatPercent).text =
            s.withinSlaPercent?.let { getString(R.string.stat_percent_fmt, it) } ?: getString(R.string.stat_none)
        v.findViewById<TextView>(R.id.tvStatPercentDetail).text =
            getString(R.string.stat_percent_detail, s.withinSla, s.resolvedCount)
        v.findViewById<TextView>(R.id.tvStatAvg).text =
            s.avgResolutionMs?.let { SlaPolicy.formatDuration(it) } ?: getString(R.string.stat_none)
        v.findViewById<TextView>(R.id.tvStatOverdue).text = s.overdueCount.toString()
        v.findViewById<TextView>(R.id.tvStatSoon).text = s.dueSoonCount.toString()
    }
}
