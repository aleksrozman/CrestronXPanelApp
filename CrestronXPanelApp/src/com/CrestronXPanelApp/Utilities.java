package com.CrestronXPanelApp;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.util.Log;
import android.view.WindowManager;

/**
 * @author stealthflyer
 * 
 *         Useful function to aid some common tasks and store global constants
 */
public class Utilities {

  private static final String MY_ACTIVITY = "C_XP_A";
  public static final String XMLNS = "http://schemas.android.com/apk/res/com.CrestronXPanelApp";
  public static final int DIGITAL_INPUT = 0;
  public static final int ANALOG_INPUT = 1;
  public static final int SERIAL_INPUT = 2;

  // TODO remove these however screen lock doesnt work the right way
  // without using the deprecated class
  private static KeyguardManager keyguardManager = null;
  private static KeyguardLock lock = null;

  public static void logDebug(String s) {
    Log.d(MY_ACTIVITY, s);
  }

  public static void logInformational(String s) {
    Log.i(MY_ACTIVITY, s);
  }

  public static void logWarning(String s) {
    Log.w(MY_ACTIVITY, s);
  }

  /**
   * Prevents screen from locking in the future
   * 
   * @param act
   *          Activity to perform on
   */
  public static void stopLocking(Activity act) {
    if(keyguardManager == null)
    {
      keyguardManager = (KeyguardManager)act.getSystemService(Activity.KEYGUARD_SERVICE);  
      lock = keyguardManager.newKeyguardLock(act.KEYGUARD_SERVICE);
    }
    lock.disableKeyguard();
//TODO appropriate way:    act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
  }

  /**
   * Allows screen to lock
   * 
   * @param act
   *          Activity to perform on
   */
  public static void startLocking(Activity act) {
    if(keyguardManager == null)
    {
      keyguardManager = (KeyguardManager)act.getSystemService(Activity.KEYGUARD_SERVICE);  
      lock = keyguardManager.newKeyguardLock(act.KEYGUARD_SERVICE);
    }
    lock.reenableKeyguard();
//TODO appropriate way:        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
  }
}
