package com.example.codexwp.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.codexwp.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
    }
}
