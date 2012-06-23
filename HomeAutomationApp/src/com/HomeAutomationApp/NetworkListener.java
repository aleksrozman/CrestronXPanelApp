package com.HomeAutomationApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

public class NetworkListener extends BroadcastReceiver {
  private HomeAutomationApp home;
  
  public NetworkListener(HomeAutomationApp h) {
    home = h;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    ConnectivityManager cMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    home.networkChanged(cMan.getActiveNetworkInfo().isConnected());
  }

}
