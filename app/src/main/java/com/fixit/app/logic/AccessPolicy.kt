package com.fixit.app.logic

/**
 * Who may create a TECH (technician) account.
 * Anyone can register as a USER. To register as a TECH the person must also type the
 * technician access code, otherwise anybody could give themselves access to every ticket.
 * Change the code here only.
 */
object AccessPolicy {
    private const val TECH_SIGNUP_CODE = "FIXIT-TECH-2026"

    fun isValidTechCode(code: String?): Boolean = code != null && code.trim() == TECH_SIGNUP_CODE
}
