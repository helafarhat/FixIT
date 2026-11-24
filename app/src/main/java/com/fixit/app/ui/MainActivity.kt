package com.fixit.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.fixit.app.R
import com.fixit.app.model.Role
import com.fixit.app.model.User
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Hosts the four/five tabs (fragments) and the bottom navigation, which differs by role:
 *  USER: Home, My tickets, Scan, Profile      TECH: Home, Tickets, Scan, Stats, Profile
 * "Scan" is not a tab: it opens the camera screen and the selected tab stays unchanged.
 */
class MainActivity : AppCompatActivity() {

    lateinit var me: User
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLightEdgeToEdge()
        super.onCreate(savedInstanceState)
        me = requireLogin() ?: return
        setContentView(R.layout.activity_main)
        setupScreen(showBack = false)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.inflateMenu(if (me.role == Role.TECH) R.menu.menu_bottom_tech else R.menu.menu_bottom_user)
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_scan) {
                startActivity(Intent(this, ScanActivity::class.java))
                false                       // do not select the Scan item
            } else {
                showTab(item.itemId)
                true
            }
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_home
        else setScreenTitle(titleFor(nav.selectedItemId))
    }

    private fun titleFor(itemId: Int): Int = when (itemId) {
        R.id.nav_tickets -> if (me.role == Role.TECH) R.string.nav_tickets else R.string.nav_my_tickets
        R.id.nav_stats -> R.string.nav_stats
        R.id.nav_profile -> R.string.nav_profile
        else -> R.string.nav_home
    }

    private fun showTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_tickets -> TicketsFragment()
            R.id.nav_stats -> StatsFragment()
            R.id.nav_profile -> ProfileFragment()
            else -> HomeFragment()
        }
        setScreenTitle(titleFor(itemId))
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }
}
