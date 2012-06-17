package com.HomeAutomationApp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class Server implements Runnable {

  private Timer heartBeat;
  private Timer updateRequest;
  private static volatile boolean serverThreadAlive = false;
  private static final int MY_NOTIFICATION = 1;
  private static final int MAX_RETRIES = 3;
  private static final int TIME_TO_RECONNECT = 2000;
  private Socket mSocket;
  private Toast mConnectFailedToast;
  private Toast mIDFailedToast;
  private DataOutputStream mOutStream;
  private NotificationManager mNotificationManager;
  private Notification myNotification;
  private DataInputStream mInStream;
  private HomeAutomationApp home;
  private String mIP;
  private int mPort;
  private int mId;

  class CIPMessage {
    public byte type;
    public short len;
    public byte[] payload;

    public CIPMessage() {
      type = 0;
      len = 0;
    }
    
    public CIPMessage(byte msgType, byte[] body) {
      type = msgType;
      len = (short) body.length;
      payload = body;
    }

    public CIPMessage(byte[] input, int offset) {
      type = input[offset];
      len = (short) ((input[offset + 1] << 8) + input[offset + 2]);
      payload = Arrays.copyOfRange(input, offset + 3, offset + 3 + len);
    }
    
    public byte[] getMessage() {
      byte[] message = new byte[len + 3];
      message[0] = type;
      message[1] = (byte) (len >> 8);
      message[2] = (byte) (len & 0xFF);
      System.arraycopy(payload, 0, message, 3, len);
      return message;
    }
  }
  
  private CIPMessage connectionMsg;
  private CIPMessage heartbeatMsg;
  private CIPMessage updateRequestMsg;

  public Server(HomeAutomationApp h, String ip, int port, int id) {
    home = h;
    mIP = ip;
    mPort = port;
    mId = id;
    heartBeat = new Timer();
    updateRequest = new Timer();
    
    byte[] connectionBody = { 0x7F, 0x00, 0x00, 0x01, 0x00, (byte) mId, 0x40};
    connectionMsg = new CIPMessage((byte) 0x01, connectionBody);

    byte[] heartbeatBody = { 0x00, 0x00 };
    heartbeatMsg = new CIPMessage((byte) 0x0D, heartbeatBody);

    byte[] updateRequestBody = { 0x00, 0x00, 0x02, 0x03, 0x00 };
    updateRequestMsg = new CIPMessage((byte) 0x05, updateRequestBody);
    
    
    mConnectFailedToast = Toast.makeText(home.getApplicationContext(),
        "Connection attempt failed", Toast.LENGTH_LONG);
    mIDFailedToast = Toast.makeText(home.getApplicationContext(),
        "Crestron ID " + Integer.toString(mId) + " does not exist", Toast.LENGTH_LONG);
    mNotificationManager = (NotificationManager) home
        .getSystemService(Context.NOTIFICATION_SERVICE);
    myNotification = new Notification(R.drawable.icon, "App connected to home",
        System.currentTimeMillis());
    Intent i = new Intent(home, HomeAutomationApp.class);
    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent contentIntent = PendingIntent.getActivity(home, 0, i, 0);
    myNotification.setLatestEventInfo(home.getApplicationContext(),
        "HomeAutomationApp", "Running in background", contentIntent);
    myNotification.flags = Notification.FLAG_NO_CLEAR
        | Notification.FLAG_ONGOING_EVENT;
  }

  public boolean status() {
    return serverThreadAlive;
  }
  
  private void sendMessage(CIPMessage input) {
    try {
      if (mSocket != null && mSocket.isConnected()) {
        mOutStream.write(input.getMessage());
        mOutStream.flush();
      } else {
        Utilities.logDebug("Cannot send string " + input.payload.toString() + " because mSocket is "
            + ((mSocket == null) ? "null" : "not connected"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }   
  }
  
  public void sendDigital(int join, int value) {
    byte[] body = {0x00, 0x00, 0x03, 0x27, (byte) (join & 0xFF), (byte) ((join >> 8) | ((value == 0) ? 0x0080 : 0x0000))};
    sendMessage(new CIPMessage((byte) 0x05, body));
  }
  
  public void sendAnalog(int join, int value) {
    byte[] body = {0x00, 0x00, 0x05, 0x14, (byte) (join >> 8), (byte) (join & 0xFF), (byte) (value >> 8), (byte) (value & 0xFF)};
    sendMessage(new CIPMessage((byte) 0x05, body));
  }
  
  public void sendSerial(int join, String value) {
    // TODO Test this
    byte[] body = new byte[value.length() + 3];
    body[0] = 0x00;
    body[1] = 0x00;
    body[2] = (byte) (value.length() + 2);
    body[3] = 0x12;
    body[4] = (byte) join;
    System.arraycopy(value.toCharArray(), 0, body, 5, value.length());
    sendMessage(new CIPMessage((byte) 0x05, body));
  }

  public void shutdown() {
    try {
      mNotificationManager.cancel(MY_NOTIFICATION);
      serverThreadAlive = false;
      mConnectFailedToast.cancel();
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
  
  public void HandleCIPData(byte[] input) {
    int join = 0;
    int value = 0;
    String sValue = "";
    switch(input[3]) {
      case 0x00: // Digital
        value = ((input[5] & 0x0080) == 0) ? 1 : 0; /* NOTE: I flipped 1s and 0s in the digital button */
        // NOTE: Have to be careful with sign
        // And joins are 0 based when received
        join = (input[5] & 0x007F); 
        join <<= 8; 
        join += (input[4] & 0xFF) + 1; 
        home.serverCallback(join, Utilities.DIGITAL_INPUT, Integer.toString(value));
        break;
      case 0x01: // Analog
        if(input[2] == 0x04) { // Join < 256
          join = (input[4] & 0xFF) + 1;
          value = input[5] & 0x00FF; value <<= 8; value += input[6] & 0xFF;
        } else if (input[2] == 0x05) {
          join = input[4] & 0x00FF; join <<= 8; join += (input[5] & 0xFF) + 1;
          value = input[6] & 0x00FF; value <<= 8; value += input[7] & 0xFF;
        } else {
          Utilities.logWarning(input.toString());
        }
        home.serverCallback(join, Utilities.ANALOG_INPUT, Integer.toString(value));
        break;
      case 0x02: // Serial
        int stringLen = input[1] & 0x00FF; stringLen <<= 8; stringLen += input[2] & 0xFF;
        String[] msgs = (new String(input, 4, stringLen - 2)).split("\r");
        int joinOffset = msgs[0].indexOf(",");
        join = Integer.parseInt(msgs[0].substring(1, joinOffset));
        for (String s : msgs) {
          if(s.length() > joinOffset + 1) {
            sValue += s.substring(joinOffset + 1);
          }
        }
        home.serverCallback(join, Utilities.SERIAL_INPUT, sValue);
        break;
      case 0x03: // Update request confirmation
        break;
      case 0x08: // Date and time
        break;
      default:
        break;
    }

  }

  public void HandleCIPMessage(CIPMessage input) throws Exception {
    switch (input.type) {
      case 0x02:
        if ((input.payload[0] == (byte)0xFF) && (input.payload[1] == (byte)0xFF)
            && (input.payload[2] == (byte)0x02)) {
          mIDFailedToast.show();
          shutdown();
          throw new Exception("Crestron ID bad");
        } else if (input.len == 4) {
          mNotificationManager.notify(MY_NOTIFICATION, myNotification);
          // IP Registration Successful
          heartBeat.schedule(new TimerTask() {
            
            @Override
            public void run() {
              sendMessage(heartbeatMsg);
            }
          }, 0, 5000);
/*          
 * OPTIONAL, poll the update request
 * updateRequest.schedule(new TimerTask() {
 *          @Override
 *           public void run() {
 *             sendMessage(updateRequestMsg);
 *           }
 *         }, 0, 1000);
 */
          sendMessage(updateRequestMsg);
        } else {
          Utilities.logWarning(input.payload.toString());
        }
      case 0x05: // Data
        try {
          HandleCIPData(input.payload);
        } catch(Exception e) {
          e.printStackTrace();
        }
        break;
      case 0x03:
        // Program stopping or disconnect
        break;
      case 0x0D: // Heartbeat timeout
      case 0x0E: // Heartbeat response
        break;
      case 0x0F:
        if(input.len == 1) {
          if(input.payload[0] == 0x02) {
            sendMessage(connectionMsg);
          } else {
            Utilities.logWarning(input.payload.toString());
          }
        } else {
          Utilities.logDebug(input.payload.toString());
        }
        break;
      default:
        Utilities.logDebug(input.payload.toString());  
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
        mOutStream = new DataOutputStream(mSocket.getOutputStream());
        mInStream = new DataInputStream(mSocket.getInputStream());
        byte[] data = new byte[1024];
        int charRead = 0;
        Utilities.logDebug("Connected");
        retry = 0;
        CIPMessage temp;
        while ((charRead = mInStream.read(data)) >= 0) {
          for (int i = 0; i < charRead; i+=(temp.len + 3)) {
            temp = new CIPMessage(data, i);
            HandleCIPMessage(temp);
          }
        }
      } catch (IOException e) {
        mConnectFailedToast.show();
        try {
          if (mSocket != null)
            mSocket.close();
          Thread.sleep(TIME_TO_RECONNECT);
          if (++retry >= MAX_RETRIES) {
            home.finish();
            break;
          } else {
            mConnectFailedToast.cancel();
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
