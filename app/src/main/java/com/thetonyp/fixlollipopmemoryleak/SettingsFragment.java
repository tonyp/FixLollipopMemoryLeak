package com.thetonyp.fixlollipopmemoryleak;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import java.io.File;

public class SettingsFragment extends PreferenceFragment {

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.preferences);

        findPreference("pref_about").setTitle(getString(R.string.pref_about_title, BuildConfig.VERSION_NAME));
        findPreference("pref_launcher").setOnPreferenceChangeListener(changeListenerLauncher);
    }

    // Option to hide the launcher icon
    private final Preference.OnPreferenceChangeListener changeListenerLauncher = new Preference.OnPreferenceChangeListener() {
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int componentState = ((Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            Activity activity = getActivity();
            ComponentName alias = new ComponentName(activity, "com.thetonyp.fixlollipopmemoryleak.SettingsActivity-Insert");
            activity.getPackageManager().setComponentEnabledSetting(alias, componentState, PackageManager.DONT_KILL_APP);
            return true;
        }
    };

    @Override
    public void onPause() {
        super.onPause();

        // world readable
        File sharedPrefsDir = new File(getActivity().getApplicationInfo().dataDir, "shared_prefs");
        File sharedPrefsFile = new File(sharedPrefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");
        if (sharedPrefsFile.exists()) {
            sharedPrefsFile.setReadable(true, false);
        }
    }
}