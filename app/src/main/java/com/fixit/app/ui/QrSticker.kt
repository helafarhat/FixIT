package com.fixit.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.fixit.app.logic.QrPayload
import com.fixit.app.model.Device
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/** Draws the printable sticker: the QR code plus the device code and room underneath. */
object QrSticker {
    fun create(device: Device): Bitmap {
        val qrSize = 640
        val qr = BarcodeEncoder().encodeBitmap(QrPayload.build(device.code), BarcodeFormat.QR_CODE, qrSize, qrSize)
        val w = 760
        val h = 900
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(qr, (w - qrSize) / 2f, 40f, null)

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 64f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val roomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 42f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(device.code, w / 2f, 790f, codePaint)
        canvas.drawText(device.location, w / 2f, 855f, roomPaint)
        return bmp
    }
}
