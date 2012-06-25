package com.HomeAutomationApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

/**
 * @author stealthflyer
 * 
 *         Class to listen to the network incase it drops out. Waits for a
 *         connection to be made to shutdown or start the server (which has a
 *         heartbeat)
 */
public class NetworkListener extends BroadcastReceiver {
  private HomeAutomationApp home;

  public NetworkListener(HomeAutomationApp h) {
    home = h;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    ConnectivityManager cMan = (ConnectivityManager) context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cMan.getActiveNetworkInfo() == null) {
      home.networkChanged(false);
    } else {
      home.networkChanged(cMan.getActiveNetworkInfo().isConnected());
    }
  }

}
