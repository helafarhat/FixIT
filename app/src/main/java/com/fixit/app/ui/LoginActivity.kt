package com.fixit.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.data.Session
import com.fixit.app.model.Role
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

/**
 * The app opens here. The person says whether they sign in as a User or a Technician;
 * the choice is checked against the role stored in the account, then each role is sent to its own screens.
 */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Blue header under the status bar: light (white) status bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // "Remember me" was ticked last time: skip the form.
        if (Session.current(this) != null) { goMain(); return }

        setContentView(R.layout.activity_login)
        applyInsets()

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleRole)
        val roleHint = findViewById<TextView>(R.id.tvRoleHint)
        toggle.check(R.id.btnRoleUser)
        roleHint.setText(R.string.role_hint_user)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) roleHint.setText(
                if (checkedId == R.id.btnRoleTech) R.string.role_hint_tech else R.string.role_hint_user)
        }

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = tilEmail.text()
            val password = tilPassword.editText?.text?.toString().orEmpty()   // never trim a password
            val chosenRole = if (toggle.checkedButtonId == R.id.btnRoleTech) Role.TECH else Role.USER
            var ok = true
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.showFieldError(getString(R.string.err_email_invalid)); ok = false
            } else tilEmail.showFieldError(null)
            if (password.isEmpty()) {
                tilPassword.showFieldError(getString(R.string.err_required)); ok = false
            } else tilPassword.showFieldError(null)
            if (!ok) return@setOnClickListener

            val user = DBHelper.get(this).authenticate(email, password)
            when {
                user == null -> tilPassword.showFieldError(getString(R.string.err_bad_credentials))
                // The chosen role must match the account's real role (stored in the database).
                user.role != chosenRole -> tilEmail.showFieldError(getString(
                    if (user.role == Role.TECH) R.string.err_account_is_tech else R.string.err_account_is_user))
                else -> {
                    Session.login(this, user, findViewById<CheckBox>(R.id.cbRemember).isChecked)
                    goMain()
                }
            }
        }
        findViewById<Button>(R.id.btnGoRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /** Header takes the status-bar height; the form takes the navigation-bar / keyboard height. */
    private fun applyInsets() {
        val header = findViewById<View>(R.id.loginHeader)
        val form = findViewById<View>(R.id.loginForm)
        val headerTop = header.paddingTop
        val formBottom = form.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.screenRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            form.setPadding(form.paddingLeft, form.paddingTop, form.paddingRight, formBottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
