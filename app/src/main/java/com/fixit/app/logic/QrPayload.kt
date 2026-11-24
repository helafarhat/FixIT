package com.fixit.app.logic

/** QR payload format: FIXIT:DEVICE:<code>. Validated strictly: anything else is refused. */
object QrPayload {
    private const val PREFIX = "FIXIT:DEVICE:"

    /** Device code: 3 to 30 characters, capital letters, digits and dashes, e.g. PC-B2-014. */
    private val CODE = Regex("^[A-Z0-9][A-Z0-9-]{2,29}$")

    fun isValidCode(code: String): Boolean = CODE.matches(code)

    fun build(code: String): String = PREFIX + code

    /** Returns the device code, or null when the text is not a valid FixIT payload. */
    fun parse(text: String?): String? {
        val s = text?.trim() ?: return null
        if (!s.startsWith(PREFIX)) return null
        val code = s.removePrefix(PREFIX)
        return if (isValidCode(code)) code else null
    }

    /** Manual entry: accepts the bare code (pc-b2-014) or the full payload. */
    fun parseManual(text: String): String? {
        val t = text.trim()
        return parse(t) ?: t.uppercase().takeIf { isValidCode(it) }
    }
}
