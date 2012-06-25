package com.HomeAutomationApp;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * @author stealthflyer
 * 
 *         Help setup the preferences
 */
public class EditPreferences extends PreferenceActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.pref);
  }
}
