package com.CrestronXPanelApp;

import com.CrestronXPanelApp.R;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

/**
 * @author stealthflyer
 * 
 *         Help setup the preferences
 */
public class EditPreferences extends PreferenceActivity  {
	
  private static final int MSG_SHOW_TOAST = 1;
  static PreferenceActivity thisActivity = null;

@Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    thisActivity = this; 

    getFragmentManager().beginTransaction().replace(android.R.id.content,
        new PrefsFragment()).commit();
  }
  
  static public class PrefsFragment extends PreferenceFragment  {

    @Override
    public void onCreate(Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);
     addPreferencesFromResource(R.xml.pref);

     Preference pref = findPreference("id"); 
     pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
         public boolean onPreferenceChange(Preference preference, Object newValue) {
     		int ipID = Integer.parseInt((String) newValue,16);
    		if (ipID >= 0x03 && ipID <= 0xFE ) {
    			return true;
    		}
    		Toast.makeText(thisActivity, "ID should be between 03 and FE" , Toast.LENGTH_LONG).show();
    		return false;
         }
     });
    }
   }
}
