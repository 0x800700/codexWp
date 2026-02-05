package com.example.codexwp.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.codexwp.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        findPreference<Preference>("resetDefaults")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            sp.edit().clear().apply()
            PreferenceManager.setDefaultValues(ctx, R.xml.prefs, true)
            preferenceScreen.removeAll()
            setPreferencesFromResource(R.xml.prefs, rootKey)
            true
        }
    }
}
