package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fixit.app.R
import com.fixit.app.data.Session
import com.fixit.app.model.Role

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val me get() = (requireActivity() as MainActivity).me

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvAvatar).text = me.name.trim().take(1).uppercase()
        view.findViewById<TextView>(R.id.tvProfileName).text = me.name
        view.findViewById<TextView>(R.id.tvProfileEmail).text = me.email
        view.findViewById<TextView>(R.id.chipRole).applyChip(
            getString(me.role.labelRes()), ChipStyle(R.color.primary_container, R.color.primary_dark))

        val devices = view.findViewById<Button>(R.id.btnDevices)
        devices.visibility = if (me.role == Role.TECH) View.VISIBLE else View.GONE   // Devices: reachable from Profile
        devices.setOnClickListener { startActivity(Intent(requireContext(), DevicesActivity::class.java)) }

        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            (requireActivity() as MainActivity).confirm(R.string.confirm_logout) {
                Session.logout(requireContext())
                startActivity(Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                requireActivity().finish()
            }
        }
    }
}
