package de.binarynoise.captiveportalautologin.preferences

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.R

class NewMainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        supportFragmentManager.commit {
            add(R.id.fragmentContainerView, NewMainActivityFragment())
        }
        
        val actionBar = actionBar
        if (actionBar != null) supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            } else {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
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
}
