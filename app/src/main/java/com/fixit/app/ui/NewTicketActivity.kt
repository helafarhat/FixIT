package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.model.Category
import com.fixit.app.model.Device
import com.fixit.app.model.Priority
import com.fixit.app.model.Role
import com.google.android.material.textfield.TextInputLayout

/**
 * New ticket. When opened from a QR scan, the device, its room and a suggested category
 * are already filled in: the user only writes the problem and picks a priority.
 */
class NewTicketActivity : AppCompatActivity() {

    private lateinit var db: DBHelper
    private lateinit var categories: List<Category>
    private var device: Device? = null

    // Scan again from this screen: ScanActivity just returns the device code.
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val code = r.data?.getStringExtra(ScanActivity.EXTRA_DEVICE_CODE)
        if (r.resultCode == RESULT_OK && code != null) selectDevice(db.getDeviceByCode(code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        val me = requireLogin() ?: return
        if (me.role != Role.USER) { toast(R.string.err_user_only); finish(); return }   // technicians do not create tickets
        setContentView(R.layout.activity_new_ticket)
        setupScreen(R.string.title_new_ticket)
        db = DBHelper.get(this)

        categories = db.getCategories()
        val spCategory = findViewById<Spinner>(R.id.spCategory)
        spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val spPriority = findViewById<Spinner>(R.id.spPriority)
        spPriority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            Priority.values().map { getString(it.labelRes()) })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spPriority.setSelection(Priority.MEDIUM.ordinal)      // sensible default

        // Device pre-filled by the scan flow?
        intent.getStringExtra(ScanActivity.EXTRA_DEVICE_CODE)?.let { selectDevice(db.getDeviceByCode(it)) }
        if (device == null) selectDevice(null)

        findViewById<Button>(R.id.btnScanDevice).setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_RETURN_CODE, true))
        }
        findViewById<Button>(R.id.btnClearDevice).setOnClickListener { selectDevice(null) }

        val tilTitle = findViewById<TextInputLayout>(R.id.tilTitle)
        val tilDescription = findViewById<TextInputLayout>(R.id.tilDescription)
        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            val title = tilTitle.text()
            val description = tilDescription.text()
            var ok = true
            if (title.length < 5) { tilTitle.showFieldError(getString(R.string.err_title_short)); ok = false }
            else tilTitle.showFieldError(null)
            if (description.length < 10) { tilDescription.showFieldError(getString(R.string.err_description_short)); ok = false }
            else tilDescription.showFieldError(null)
            if (!ok) return@setOnClickListener

            val id = db.createTicket(me, title, description,
                categories[spCategory.selectedItemPosition].id, device?.id,
                Priority.values()[spPriority.selectedItemPosition])
            if (id < 0) { toast(R.string.err_generic); return@setOnClickListener }
            toast(R.string.msg_ticket_created)
            startActivity(Intent(this, TicketDetailActivity::class.java)
                .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, id))
            finish()
        }
    }

    /** Shows the device card and pre-selects the suggested category. */
    private fun selectDevice(d: Device?) {
        device = d
        val info = findViewById<TextView>(R.id.tvDeviceInfo)
        val suggestion = findViewById<TextView>(R.id.tvSuggestion)
        findViewById<View>(R.id.btnClearDevice).visibility = if (d == null) View.GONE else View.VISIBLE
        if (d == null) {
            info.setText(R.string.no_device_selected)
            suggestion.visibility = View.GONE
            return
        }
        info.text = getString(R.string.device_selected_fmt, d.code, getString(d.type.labelRes()), d.model, d.location)
        val index = categories.indexOfFirst { it.name == d.type.suggestedCategory }
        if (index >= 0) {
            findViewById<Spinner>(R.id.spCategory).setSelection(index)
            suggestion.text = getString(R.string.category_suggested_fmt, d.type.suggestedCategory)
            suggestion.visibility = View.VISIBLE
        }
    }
}
