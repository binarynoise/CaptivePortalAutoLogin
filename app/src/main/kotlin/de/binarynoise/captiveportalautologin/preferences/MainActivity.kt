package de.binarynoise.captiveportalautologin.preferences

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.R

class MainActivity : FragmentActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainerView, MainFragment())
                fillInAnimation()
            }
        }
        
        val actionBar = actionBar
        if (actionBar != null) {
            fun updateActionBar() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    actionBar.setDisplayHomeAsUpEnabled(true)
                    actionBar.setHomeButtonEnabled(true)
                } else {
                    actionBar.setDisplayHomeAsUpEnabled(false)
                    actionBar.setHomeButtonEnabled(false)
                }
            }
            supportFragmentManager.addOnBackStackChangedListener(::updateActionBar)
            updateActionBar()
        }
        
        if (intent.getBooleanExtra("startService", true)) {
            ConnectivityChangeListenerService.start()
            intent.putExtra("startService", false)
        }
    }
    
    override fun onNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        
        return super.onNavigateUp()
    }
    
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        supportFragmentManager.commit {
            fillInAnimation()
            val preferenceFragmentName = pref.fragment ?: return false
            val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, preferenceFragmentName)
            replace(R.id.fragmentContainerView, fragment, pref.key)
            addToBackStack(null)
        }
        return true
    }
    
    fun FragmentTransaction.fillInAnimation() {
        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
    }
}
