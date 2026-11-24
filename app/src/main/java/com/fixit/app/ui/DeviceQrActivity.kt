package com.fixit.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.model.Device
import com.fixit.app.model.Role
import java.io.File

/**
 * "Show QR": draws the sticker, lets the technician save it as an image or share it (to print),
 * and lists the device's past tickets to spot problematic equipment.
 */
class DeviceQrActivity : AppCompatActivity() {

    companion object { const val EXTRA_DEVICE_ID = "device_id" }

    private lateinit var db: DBHelper
    private var device: Device? = null
    private var sticker: Bitmap? = null

    // Save as image: the system file picker, so no storage permission is needed on any Android version.
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bmp = sticker
        if (uri != null && bmp != null) {
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } catch (e: Exception) { false }
            toast(if (ok) R.string.msg_qr_saved else R.string.err_generic)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        val me = requireLogin() ?: return
        if (me.role != Role.TECH) { finish(); return }
        setContentView(R.layout.activity_device_qr)
        setupScreen(R.string.title_device_qr)
        db = DBHelper.get(this)

        findViewById<Button>(R.id.btnSaveImage).setOnClickListener {
            device?.let { saveLauncher.launch("FixIT-${it.code}.png") }
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { share() }
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
        sticker = QrSticker.create(d)
        findViewById<ImageView>(R.id.ivQr).setImageBitmap(sticker)
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

    private fun share() {
        val d = device ?: return
        val bmp = sticker ?: return
        val dir = File(cacheDir, "qr").apply { mkdirs() }
        val file = File(dir, "FixIT-${d.code}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }
}
