package com.fixit.app.data

import android.content.Context
import com.fixit.app.model.User

/**
 * The logged-in user. Kept in memory; when "Remember me" is ticked only the user id
 * is saved in SharedPreferences (never the password) so the next launch skips Login.
 */
object Session {
    private const val PREFS = "fixit_session"
    private const val KEY_USER_ID = "remembered_user_id"
    private var user: User? = null

    fun current(ctx: Context): User? {
        user?.let { return it }
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_USER_ID, -1L)
        if (id > 0) user = DBHelper.get(ctx).getUserById(id)
        return user
    }

    fun login(ctx: Context, u: User, remember: Boolean) {
        user = u
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (remember) e.putLong(KEY_USER_ID, u.id) else e.remove(KEY_USER_ID)
        e.apply()
    }

    fun logout(ctx: Context) {
        user = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_USER_ID).apply()
    }
}
