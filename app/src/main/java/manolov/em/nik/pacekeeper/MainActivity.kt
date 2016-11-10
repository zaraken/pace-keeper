package manolov.em.nik.pacekeeper

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.*
import android.util.Log

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.pace_keeper_preferences, false)

        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
        .commit()
    }

    companion object {
        val TAG = "MainActivity"
    }

    class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreate(savedInstanceState: Bundle?){
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pace_keeper_preferences)

            traversePreferences(preferenceScreen, {p: Preference->updateEditTextPreferenceSummary(p)})
            Log.d(TAG, "starting service")
            activity.startService(Intent(activity, PaceKeeperService::class.java))
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                      key: String) {
            Log.d(TAG, key)
            updateEditTextPreferenceSummary(findPreference(key))
        }

        private fun traversePreferences(p: Preference, func: (p:Preference)->Unit) {
            when (p){
                is PreferenceGroup -> {
                    for (i in 0 until p.preferenceCount) {
                        traversePreferences(p.getPreference(i), func)
                }}
                else -> { func(p)}
            }
        }

        private fun updateEditTextPreferenceSummary(p: Preference) {
            Log.d(TAG, "updateEditTextPreferenceSummary")
            if (p is EditTextPreference) {
                Log.d(TAG, "EditTextPreference")
                p.setSummary(p.text)
            }
        }
    }

}
