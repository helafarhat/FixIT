package com.fixit.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.logic.QrPayload
import com.fixit.app.model.Device
import com.fixit.app.model.Role
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * Camera screen. Flow: permission -> scan -> strict validation -> look up the device -> continue.
 *  - started with EXTRA_RETURN_CODE: just returns the device code to the caller (New Ticket screen);
 *  - USER: opens New Ticket pre-filled;   TECH: opens the device's QR/history screen.
 * Manual entry of the code is always available as a fallback.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETURN_CODE = "return_code"
        const val EXTRA_DEVICE_CODE = "device_code"
    }

    private lateinit var db: DBHelper
    private lateinit var scanner: DecoratedBarcodeView
    private var torchOn = false
    private var lastText: String? = null
    private var lastTime = 0L

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updateUi()
        else {
            // No rationale to show any more = the user ticked "don't ask again": only Settings can help.
            val permanent = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            showPermissionPanel(permanent)
        }
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val text = result.text ?: return
            val now = System.currentTimeMillis()
            if (text == lastText && now - lastTime < 2500) return   // ignore repeated reads of the same code
            lastText = text; lastTime = now
            handlePayload(text)
        }
        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        requireLogin() ?: return
        setContentView(R.layout.activity_scan)
        setupScreen(R.string.title_scan)
        db = DBHelper.get(this)

        scanner = findViewById(R.id.barcodeView)
        scanner.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        scanner.setStatusText(getString(R.string.scan_instruction))
        scanner.decodeContinuous(callback)

        val flash = findViewById<Button>(R.id.btnFlash)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) flash.visibility = View.GONE
        flash.setOnClickListener {
            torchOn = !torchOn
            if (torchOn) scanner.setTorchOn() else scanner.setTorchOff()
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            askInput(R.string.manual_title, R.string.manual_hint, true, capitals = true) { text ->
                val code = QrPayload.parseManual(text)
                if (code == null) toast(R.string.err_qr_invalid) else openDevice(code)
            }
        }
        findViewById<Button>(R.id.btnGrant).setOnClickListener {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        if (::scanner.isInitialized) {
            updateUi()      // the person may have just allowed the camera in Settings; also resumes the camera
        }
    }

    override fun onPause() {
        if (::scanner.isInitialized) scanner.pause()
        super.onPause()
    }

    private fun updateUi() {
        val granted = hasCamera()
        scanner.visibility = if (granted) View.VISIBLE else View.GONE
        findViewById<View>(R.id.llPermission).visibility = if (granted) View.GONE else View.VISIBLE
        if (granted) scanner.resume() else showPermissionPanel(false)
    }

    private fun showPermissionPanel(permanentlyDenied: Boolean) {
        findViewById<View>(R.id.llPermission).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvPermissionMessage).setText(
            if (permanentlyDenied) R.string.perm_denied_forever else R.string.perm_rationale)
        findViewById<View>(R.id.btnGrant).visibility = if (permanentlyDenied) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnOpenSettings).visibility = if (permanentlyDenied) View.VISIBLE else View.GONE
    }

    private fun handlePayload(text: String) {
        val code = QrPayload.parse(text)
        if (code == null) { toast(R.string.err_qr_invalid); return }   // strict validation
        openDevice(code)
    }

    private fun openDevice(code: String) {
        val device = db.getDeviceByCode(code)
        if (device == null) { toast(R.string.err_device_unknown); return }
        proceed(device)
    }

    private fun proceed(device: Device) {
        if (intent.getBooleanExtra(EXTRA_RETURN_CODE, false)) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_DEVICE_CODE, device.code))
        } else if (requireLogin()?.role == Role.USER) {
            startActivity(Intent(this, NewTicketActivity::class.java).putExtra(EXTRA_DEVICE_CODE, device.code))
        } else {
            startActivity(Intent(this, DeviceQrActivity::class.java)
                .putExtra(DeviceQrActivity.EXTRA_DEVICE_ID, device.id))
        }
        finish()
    }
}
