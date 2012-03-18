package com.HomeAutomationApp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class Server implements Runnable {
  
  private static volatile boolean serverThreadAlive = false;
  private static final int MY_NOTIFICATION = 1;
  private static final int MAX_RETRIES = 3;
  private static final int TIME_TO_RECONNECT = 2000;
  private Socket mSocket;
  private Toast mConnectToast;
  private PrintWriter mPrinter;
  private NotificationManager mNotificationManager;
  private Notification myNotification;
  private BufferedReader mBufferIn;
  private HomeAutomationApp home;
  private String mIP;
  private int mPort;

  public Server(HomeAutomationApp h, String ip, int port) {
    home = h;
    mIP = ip;
    mPort = port;
    mConnectToast = Toast.makeText(home.getApplicationContext(), "Connection attempt failed", Toast.LENGTH_LONG);
    mNotificationManager = (NotificationManager) home.getSystemService(Context.NOTIFICATION_SERVICE);
    myNotification = new Notification(R.drawable.icon, "App connected to home", System.currentTimeMillis());
    Intent i = new Intent(home, HomeAutomationApp.class);
    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent contentIntent = PendingIntent.getActivity(home, 0, i, 0);
    myNotification.setLatestEventInfo(home.getApplicationContext(), "HomeAutomationApp", "Running in background",
        contentIntent);
    myNotification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
  }

  public boolean status() {
    return serverThreadAlive;
  }

  public void sendMessage(String s) {
    try {
      if (mSocket != null && mSocket.isConnected()) {
        mPrinter.print(s);
        mPrinter.flush();
      }
      else {
        Utilities.logDebug("Cannot send string " + s + " because mSocket is " + ((mSocket == null) ? "null" : "not connected"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void shutdown() {
    try {
      mNotificationManager.cancel(MY_NOTIFICATION);
      serverThreadAlive = false;
      mConnectToast.cancel();
      if (mSocket != null) {
        try {
          mSocket.shutdownInput();
          mSocket.shutdownOutput();
          mSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      mSocket = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void run() {
    int retry = 0;
    serverThreadAlive = true;
    while (serverThreadAlive) {
      try {
        mNotificationManager.cancel(MY_NOTIFICATION);
        mSocket = new Socket(InetAddress.getByName(mIP), mPort);
        mSocket.setReuseAddress(true);
        mSocket.setKeepAlive(true);
        mSocket.setSoLinger(false, 0);
        mPrinter = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream()), true);
        mBufferIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()), 1024);
        String line = null;
        mNotificationManager.notify(MY_NOTIFICATION, myNotification);
        Utilities.logDebug("Connected");
        retry = 0;
        while ((line = mBufferIn.readLine()) != null) {
          try {
            if (line.length() > 0) {
              String[] vals = line.split(":");
              if (vals.length >= 3) {
                if (vals.length > 3) { // When sending text, sometimes there
                                       // might be colons
                  for (int i = 3; i < vals.length; i++)
                    vals[2] += ":" + vals[i];
                }
                home.serverCallback(vals[0], vals[1], vals[2]);
              } else {
                Utilities.logWarning(line + " not understood");
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      } catch (IOException e) {
        mConnectToast.show();
        try {
          if (mSocket != null)
            mSocket.close();
          Thread.sleep(TIME_TO_RECONNECT);
          if (++retry >= MAX_RETRIES) {
            home.finish();
            break;
          } else {
            mConnectToast.cancel();
            Thread.sleep(500);
          }
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        } catch (IOException e2) {
          e2.printStackTrace();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    Utilities.logWarning("Server thread over");
  }
}
