package com.CrestronXPanelApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * @author stealthflyer
 * 
 *         Class to listen to the phone state. Used for special button 3
 * 
 */
public class PhoneListener extends BroadcastReceiver {
  private CrestronXPanelApp home;

  public PhoneListener(CrestronXPanelApp h) {
    home = h;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    TelephonyManager tm = (TelephonyManager) context
        .getSystemService(Context.TELEPHONY_SERVICE);
    int events = PhoneStateListener.LISTEN_CALL_STATE;
    tm.listen(phoneStateListener, events);
  }

  private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
      String callState = Integer.toString(state);
      switch (state) {
        case TelephonyManager.CALL_STATE_IDLE:
          callState = "IDLE";
          home.phoneActivated(false);
          break;
        case TelephonyManager.CALL_STATE_RINGING:
          // -- check international call or not.
          callState = "RINGING " + incomingNumber;
          home.phoneActivated(true);
          break;
      }
      Utilities.logInformational("onCallStateChanged " + callState);
      super.onCallStateChanged(state, incomingNumber);
    }
  };

};