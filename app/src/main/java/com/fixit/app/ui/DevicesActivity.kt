package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.model.Role
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

/** Technician-only list of devices with search. A tap opens the device's QR sticker + history. */
class DevicesActivity : AppCompatActivity() {

    private lateinit var db: DBHelper
    private lateinit var adapter: DeviceAdapter
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        val me = requireLogin() ?: return
        if (me.role != Role.TECH) { finish(); return }      // permission: TECH only
        setContentView(R.layout.activity_devices)
        setupScreen(R.string.title_devices)
        db = DBHelper.get(this)

        adapter = DeviceAdapter(this, emptyList())
        val list = findViewById<ListView>(R.id.listDevices)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, DeviceQrActivity::class.java)
                .putExtra(DeviceQrActivity.EXTRA_DEVICE_ID, adapter.getItemId(position)))
        }
        findViewById<TextInputEditText>(R.id.etSearch).doAfterTextChanged {
            query = it?.toString().orEmpty(); reload()
        }
        findViewById<FloatingActionButton>(R.id.fabAddDevice).setOnClickListener {
            startActivity(Intent(this, DeviceFormActivity::class.java))
        }
        findViewById<View>(R.id.emptyState).setEmptyTexts(R.string.empty_devices_title, R.string.empty_devices_text)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) reload()
    }

    private fun reload() {
        val devices = db.getDevices(query)
        adapter.update(devices)
        findViewById<View>(R.id.emptyState).visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }
}
