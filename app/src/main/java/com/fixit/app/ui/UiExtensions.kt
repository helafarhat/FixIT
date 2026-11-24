package com.fixit.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fixit.app.R
import com.fixit.app.data.Session
import com.fixit.app.model.User
import com.google.android.material.textfield.TextInputLayout

/** Call BEFORE super.onCreate(). Draws behind the (transparent) system bars with dark icons. */
fun ComponentActivity.enableLightEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
}

/** Adds the system bar (and keyboard) sizes to this view's padding so content never hides behind them. */
fun View.applySystemBarInsets() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + maxOf(bars.bottom, ime.bottom))
        insets
    }
}

/**
 * Every screen: root view id "screenRoot", top bar with "btnTopBack" + "tvTopTitle".
 * Handles insets and wires the back arrow.
 */
fun AppCompatActivity.setupScreen(@StringRes titleRes: Int? = null, showBack: Boolean = true) {
    findViewById<View>(R.id.screenRoot).applySystemBarInsets()
    val back: ImageButton? = findViewById(R.id.btnTopBack)
    back?.visibility = if (showBack) View.VISIBLE else View.GONE
    back?.setOnClickListener { finish() }
    val title: TextView? = findViewById(R.id.tvTopTitle)
    if (title != null) {
        if (titleRes != null) title.setText(titleRes)
        if (!showBack) {
            title.setPaddingRelative(resources.getDimensionPixelSize(R.dimen.screen_padding),
                title.paddingTop, title.paddingEnd, title.paddingBottom)
        }
    }
}

fun AppCompatActivity.setScreenTitle(@StringRes titleRes: Int) {
    findViewById<TextView>(R.id.tvTopTitle).setText(titleRes)
}

/** Inline validation error under a field. Pass null to clear. */
fun TextInputLayout.showFieldError(message: String?) {
    isErrorEnabled = message != null
    error = message
}

/** Trimmed text of the field inside a TextInputLayout. */
fun TextInputLayout.text(): String = editText?.text?.toString()?.trim().orEmpty()

fun Context.toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

/** Returns the logged-in user, or sends the person to Login and returns null. */
fun AppCompatActivity.requireLogin(): User? {
    val u = Session.current(this)
    if (u == null) {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    return u
}
