package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.model.Device
import com.fixit.app.model.Role

/**
 * Device screen (technicians only): shows the device details and lists its past tickets
 * to spot problematic equipment. Opened after a scan or from the Devices list.
 */
class DeviceQrActivity : AppCompatActivity() {

    companion object { const val EXTRA_DEVICE_ID = "device_id" }

    private lateinit var db: DBHelper
    private var device: Device? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        val me = requireLogin() ?: return
        if (me.role != Role.TECH) { finish(); return }
        setContentView(R.layout.activity_device_qr)
        setupScreen(R.string.title_device_details)
        db = DBHelper.get(this)

        findViewById<Button>(R.id.btnEditDevice).setOnClickListener {
            device?.let { startActivity(Intent(this, DeviceFormActivity::class.java).putExtra(DeviceFormActivity.EXTRA_DEVICE_ID, it.id)) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) load()      // reload: the device may have just been edited
    }

    private fun load() {
        val d = db.getDeviceById(intent.getLongExtra(EXTRA_DEVICE_ID, 0L))
        if (d == null) { toast(R.string.err_device_unknown); finish(); return }
        device = d
        findViewById<TextView>(R.id.tvCode).text = d.code
        findViewById<TextView>(R.id.tvRoom).text = d.location
        findViewById<TextView>(R.id.tvDeviceInfo).text =
            getString(R.string.device_info_fmt, getString(d.type.labelRes()), d.model)

        val tickets = db.getTicketsForDevice(d.id)
        findViewById<TextView>(R.id.tvHistoryTitle).text = getString(R.string.device_history_fmt, tickets.size)
        val container = findViewById<LinearLayout>(R.id.llDeviceTickets)
        container.removeAllViews()
        val now = System.currentTimeMillis()
        for (t in tickets) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_ticket, container, false)
            bindTicketRow(row, t, now)
            row.setOnClickListener {
                startActivity(Intent(this, TicketDetailActivity::class.java).putExtra(TicketDetailActivity.EXTRA_TICKET_ID, t.id))
            }
            container.addView(row)
        }
        findViewById<View>(R.id.tvHistoryEmpty).visibility = if (tickets.isEmpty()) View.VISIBLE else View.GONE
    }
}