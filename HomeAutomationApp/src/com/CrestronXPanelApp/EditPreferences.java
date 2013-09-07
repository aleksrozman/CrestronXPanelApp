package com.CrestronXPanelApp;

import com.CrestronXPanelApp.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

/**
 * @author stealthflyer
 * 
 *         Help setup the preferences
 */
public class EditPreferences extends PreferenceActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getFragmentManager().beginTransaction().replace(android.R.id.content,
        new PrefsFragment()).commit();
  }
  
  static public class PrefsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);
     addPreferencesFromResource(R.xml.pref);
    }

   }
  
}
