package com.fixit.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.QrPayload
import com.fixit.app.model.Device
import com.fixit.app.model.DeviceType
import com.fixit.app.model.Role
import com.google.android.material.textfield.TextInputLayout

/** Add or edit a device (technicians only). Warns live about duplicate codes. */
class DeviceFormActivity : AppCompatActivity() {

    companion object { const val EXTRA_DEVICE_ID = "device_id" }

    private lateinit var db: DBHelper
    private var editing: Device? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        val me = requireLogin() ?: return
        if (me.role != Role.TECH) { finish(); return }
        setContentView(R.layout.activity_device_form)
        db = DBHelper.get(this)

        val deviceId = intent.getLongExtra(EXTRA_DEVICE_ID, 0L)
        editing = if (deviceId > 0) db.getDeviceById(deviceId) else null
        setupScreen(if (editing == null) R.string.title_add_device else R.string.title_edit_device)

        val tilCode = findViewById<TextInputLayout>(R.id.tilCode)
        val tilModel = findViewById<TextInputLayout>(R.id.tilModel)
        val tilLocation = findViewById<TextInputLayout>(R.id.tilLocation)
        val tilNote = findViewById<TextInputLayout>(R.id.tilNote)
        val spType = findViewById<Spinner>(R.id.spType)
        spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            DeviceType.values().map { getString(it.labelRes()) })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        editing?.let { d ->
            tilCode.editText?.setText(d.code)
            tilModel.editText?.setText(d.model)
            tilLocation.editText?.setText(d.location)
            tilNote.editText?.setText(d.note.orEmpty())
            spType.setSelection(d.type.ordinal)
            findViewById<View>(R.id.tvCodeWarning).visibility = View.VISIBLE   // changing a code breaks printed stickers
        }

        // Live duplicate-code warning while typing.
        tilCode.editText?.doAfterTextChanged {
            val code = it?.toString().orEmpty().trim().uppercase()
            if (code.isNotEmpty() && db.deviceCodeExists(code, editing?.id ?: 0L)) {
                tilCode.showFieldError(getString(R.string.err_code_taken))
            } else tilCode.showFieldError(null)
        }

        findViewById<Button>(R.id.btnSaveDevice).setOnClickListener {
            val code = tilCode.text().uppercase()
            val model = tilModel.text()
            val location = tilLocation.text()
            var ok = true
            if (!QrPayload.isValidCode(code)) { tilCode.showFieldError(getString(R.string.err_code_invalid)); ok = false }
            if (model.isEmpty()) { tilModel.showFieldError(getString(R.string.err_required)); ok = false }
            else tilModel.showFieldError(null)
            if (location.isEmpty()) { tilLocation.showFieldError(getString(R.string.err_required)); ok = false }
            else tilLocation.showFieldError(null)
            if (!ok) return@setOnClickListener

            val device = Device(editing?.id ?: 0L, code, DeviceType.values()[spType.selectedItemPosition],
                model, location, tilNote.text().ifEmpty { null })
            val violation = db.saveDevice(device)
            if (violation != null) { tilCode.showFieldError(getString(violation.messageRes())); return@setOnClickListener }
            toast(R.string.msg_device_saved)
            finish()
        }
    }
}
