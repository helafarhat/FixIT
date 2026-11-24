package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fixit.app.R
import com.fixit.app.data.DBHelper
import com.fixit.app.data.Session
import com.fixit.app.model.Role
import com.fixit.app.model.Violation
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

/**
 * Registration: the person chooses User or Technician.
 * A Technician account also needs the technician access code (checked in DBHelper.register / AccessPolicy).
 */
class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        setupScreen(R.string.title_register)

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleRole)
        val roleHint = findViewById<TextView>(R.id.tvRoleHint)
        val tilTechCode = findViewById<TextInputLayout>(R.id.tilTechCode)
        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)

        toggle.check(R.id.btnRoleUser)
        roleHint.setText(R.string.role_hint_user)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tech = checkedId == R.id.btnRoleTech
            roleHint.setText(if (tech) R.string.role_hint_tech else R.string.role_hint_user)
            tilTechCode.visibility = if (tech) View.VISIBLE else View.GONE   // code only asked for technicians
            if (!tech) tilTechCode.showFieldError(null)
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val role = if (toggle.checkedButtonId == R.id.btnRoleTech) Role.TECH else Role.USER
            val techCode = tilTechCode.text()
            val name = tilName.text()
            val email = tilEmail.text().lowercase()
            val password = tilPassword.editText?.text?.toString().orEmpty()
            val confirm = tilConfirm.editText?.text?.toString().orEmpty()

            var ok = true
            if (role == Role.TECH && techCode.isEmpty()) {
                tilTechCode.showFieldError(getString(R.string.err_required)); ok = false
            } else tilTechCode.showFieldError(null)
            if (name.length < 2) { tilName.showFieldError(getString(R.string.err_name_short)); ok = false }
            else tilName.showFieldError(null)
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.showFieldError(getString(R.string.err_email_invalid)); ok = false
            } else tilEmail.showFieldError(null)
            if (password.length < 6) { tilPassword.showFieldError(getString(R.string.err_password_short)); ok = false }
            else tilPassword.showFieldError(null)
            if (confirm != password) { tilConfirm.showFieldError(getString(R.string.err_password_mismatch)); ok = false }
            else tilConfirm.showFieldError(null)
            if (!ok) return@setOnClickListener

            val db = DBHelper.get(this)
            val violation = db.register(name, email, password, role, techCode)
            if (violation != null) {
                val message = getString(violation.messageRes())
                if (violation == Violation.BAD_TECH_CODE) tilTechCode.showFieldError(message)
                else tilEmail.showFieldError(message)
                return@setOnClickListener
            }
            // Log the new person straight in: MainActivity shows the screens of their role.
            val user = db.authenticate(email, password)!!
            Session.login(this, user, false)
            toast(R.string.msg_account_created)
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }
}
