package com.HomeAutomationApp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * @author stealthflyer
 * 
 *         Creates a server thread used for decoding and sending messages to the
 *         Crestron system
 */
public class Server implements Runnable {

  private static volatile boolean serverThreadAlive = false;
  private static final int MY_NOTIFICATION = 1;
  private static final int MAX_RETRIES = 3;
  private static final int TIME_TO_RECONNECT = 2000;
  private static final int HEARTBEAT_FREQ = 5000; /* 5 seconds */
  private static final int HOLD_FREQ = 500; /* .5 seconds */

  private HomeAutomationApp mHome; /* Parent pointer */
  private NotificationManager mNotificationManager;
  private Notification myNotification;
  private Toast mConnectFailedToast;
  private Toast mIDFailedToast;
  private Socket mSocket;
  private DataOutputStream mOutStream;
  private DataInputStream mInStream;
  private String mIP;
  private int mPort;
  private int mId;
  private Timer mHeartBeatTimer = null;
  private Timer mHoldTimer = null;
  /*
   * OPTIONAL, poll the update request private static final int UPDATE_FREQ =
   * 1000; // 1 second private Timer updateRequest;
   */
  // Better to be safe, cannot trust boolean
  private static final Semaphore lock = new Semaphore(1);

  class CIPMessage {
    public byte type;
    public short len;
    public byte[] payload;

    /**
     * Default constructor
     */
    public CIPMessage() {
      type = 0;
      len = 0;
    }

    /**
     * @param msgType
     *          Type of message to make
     * @param body
     *          Contents of message
     */
    public CIPMessage(byte msgType, byte[] body) {
      type = msgType;
      len = (short) body.length;
      payload = body;
    }

    /**
     * @param input
     *          byte stream to pull CIP message from
     * @param offset
     *          into the byte stream to start
     */
    public CIPMessage(byte[] input, int offset) {
      type = input[offset];
      len = (short) ((input[offset + 1] << 8) + input[offset + 2]);
      payload = Arrays.copyOfRange(input, offset + 3, offset + 3 + len);
    }

    /**
     * @return byte stream of CIP message
     */
    public byte[] getMessage() {
      byte[] message = new byte[len + 3];
      message[0] = type;
      message[1] = (byte) (len >> 8);
      message[2] = (byte) (len & 0xFF);
      System.arraycopy(payload, 0, message, 3, len);
      return message;
    }
  }

  private CIPMessage mConnectionMsg;
  private CIPMessage mHeartbeatMsg;
  private CIPMessage mUpdateRequestMsg;
  private CIPMessage mHoldMsg;

  /**
   * @param h
   *          Pointer to parent class
   * @param ip
   *          IP Addresss
   * @param port
   *          Port
   * @param id
   *          Crestron ID desired
   */
  public Server(HomeAutomationApp h, String ip, int port, int id) {
    mHome = h;
    mIP = ip;
    mPort = port;
    mId = id;

    byte[] connectionBody = { 0x7F, 0x00, 0x00, 0x01, 0x00, (byte) mId, 0x40 };
    mConnectionMsg = new CIPMessage((byte) 0x01, connectionBody);

    byte[] heartbeatBody = { 0x00, 0x00 };
    mHeartbeatMsg = new CIPMessage((byte) 0x0D, heartbeatBody);

    byte[] updateRequestBody = { 0x00, 0x00, 0x02, 0x03, 0x00 };
    mUpdateRequestMsg = new CIPMessage((byte) 0x05, updateRequestBody);

    mConnectFailedToast = Toast.makeText(mHome.getApplicationContext(),
        "Connection attempt failed", Toast.LENGTH_LONG);
    mIDFailedToast = Toast.makeText(mHome.getApplicationContext(),
        "Crestron ID " + Integer.toString(mId) + " does not exist",
        Toast.LENGTH_LONG);
    mNotificationManager = (NotificationManager) mHome
        .getSystemService(Context.NOTIFICATION_SERVICE);
    myNotification = new Notification(R.drawable.icon, "App connected to home",
        System.currentTimeMillis());
    Intent i = new Intent(mHome, HomeAutomationApp.class);
    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent contentIntent = PendingIntent.getActivity(mHome, 0, i, 0);
    myNotification.setLatestEventInfo(mHome.getApplicationContext(),
        "HomeAutomationApp", "Running in background", contentIntent);
    myNotification.flags = Notification.FLAG_NO_CLEAR
        | Notification.FLAG_ONGOING_EVENT;
  }

  /**
   * @return Whether the thread should exist (could cause race condition)
   */
  public boolean status() {
    return serverThreadAlive;
  }

  /**
   * @param restart
   *          Create a new heartbeat thread
   */
  private void setupHeartbeat(boolean restart) {
    if (mHeartBeatTimer != null)
      mHeartBeatTimer.cancel();
    if (restart) {
      mHeartBeatTimer = new Timer();
      mHeartBeatTimer.schedule(new TimerTask() {

        @Override
        public void run() {
          sendMessage(mHeartbeatMsg);
        }
      }, 0, HEARTBEAT_FREQ);
    }
  }

  /**
   * @param restart
   *          Create new hold thread
   */
  private void setupHold(boolean restart) {
    if (mHoldTimer != null)
      mHoldTimer.cancel();
    if (restart) {
      mHoldTimer = new Timer();
      mHoldTimer.schedule(new TimerTask() {

        @Override
        public void run() {
          sendMessage(mHoldMsg);
        }
      }, 0, HOLD_FREQ);
    }
  }

  /**
   * @param input
   *          CIP message to send
   */
  private void sendMessage(CIPMessage input) {
    try {
      if (mSocket != null && mSocket.isConnected()) {
        mOutStream.write(input.getMessage());
        mOutStream.flush();
      } else {
        Utilities.logDebug("Cannot send string " + input.payload.toString()
            + " because mSocket is "
            + ((mSocket == null) ? "null" : "not connected"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * @param join
   *          of the button
   * @param value
   *          1 for release, otherwise push
   */
  public void sendDigital(int join, int value) {
    if (value == 1) {
      byte[] body = { 0x00, 0x00, 0x03, 0x27, (byte) (join & 0xFF),
          (byte) (join >> 8) };
      mHoldMsg = new CIPMessage((byte) 0x05, body);
      /* Only supporting 1 hold */
      setupHold(true);
    } else {
      byte[] body = { 0x00, 0x00, 0x03, 0x27, (byte) (join & 0xFF),
          (byte) ((join >> 8) | 0x0080) };
      setupHold(false);
      sendMessage(new CIPMessage((byte) 0x05, body));
    }

  }

  /**
   * @param join
   *          of the analog bar
   * @param value
   *          from 0 to 65535
   */
  public void sendAnalog(int join, int value) {
    byte[] body = { 0x00, 0x00, 0x05, 0x14, (byte) (join >> 8),
        (byte) (join & 0xFF), (byte) (value >> 8), (byte) (value & 0xFF) };
    sendMessage(new CIPMessage((byte) 0x05, body));
  }

  /**
   * @param join
   *          of the serial output
   * @param value
   *          string to send
   */
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

  /**
   * Terminate sockets and threads
   */
  public void shutdown() {
    try {
      serverThreadAlive = false;
      mNotificationManager.cancel(MY_NOTIFICATION);
      mConnectFailedToast.cancel();
      setupHeartbeat(false);
      setupHold(false);
      /* setupUpdateRequest(false); */
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

  /**
   * @param input
   *          stream of data (payload)
   */
  private void HandleCIPData(byte[] input) {
    int join = 0;
    int value = 0;
    String sValue = "";
    switch (input[3]) {
      case 0x00: // Digital
        /*
         * NOTE:
         * 
         * I flipped 1s and 0s in the digital button
         * 
         * Have to be careful with sign
         * 
         * Joins are 0 based when received
         */
        value = ((input[5] & 0x0080) == 0) ? 1 : 0;
        join = (input[5] & 0x007F);
        join <<= 8;
        join += (input[4] & 0xFF) + 1;
        mHome.serverCallback(join, Utilities.DIGITAL_INPUT,
            Integer.toString(value));
        break;
      case 0x01: // Analog
        if (input[2] == 0x04) { // Join < 256
          join = (input[4] & 0xFF) + 1;
          value = input[5] & 0x00FF;
          value <<= 8;
          value += input[6] & 0xFF;
        } else if (input[2] == 0x05) {
          join = input[4] & 0x00FF;
          join <<= 8;
          join += (input[5] & 0xFF) + 1;
          value = input[6] & 0x00FF;
          value <<= 8;
          value += input[7] & 0xFF;
        } else {
          Utilities.logWarning(input.toString());
        }
        mHome.serverCallback(join, Utilities.ANALOG_INPUT,
            Integer.toString(value));
        break;
      case 0x02: // Serial
        int stringLen = input[1] & 0x00FF;
        stringLen <<= 8;
        stringLen += input[2] & 0xFF;
        String[] msgs = (new String(input, 4, stringLen - 2)).split("\r");
        int joinOffset = msgs[0].indexOf(",");
        join = Integer.parseInt(msgs[0].substring(1, joinOffset));
        for (String s : msgs) {
          if (s.length() > joinOffset + 1) {
            sValue += s.substring(joinOffset + 1);
          }
        }
        mHome.serverCallback(join, Utilities.SERIAL_INPUT, sValue);
        break;
      case 0x03: // Update request confirmation
        break;
      case 0x08: // Date and time
        break;
      default:
        break;
    }

  }

  private void HandleCIPMessage(CIPMessage input) throws Exception {
    switch (input.type) {
      case 0x02: // Crestron ID registration feedback
        if ((input.payload[0] == (byte) 0xFF)
            && (input.payload[1] == (byte) 0xFF)
            && (input.payload[2] == (byte) 0x02)) {
          mIDFailedToast.show();
          shutdown();
          throw new Exception("Crestron ID bad");
        } else if (input.len == 4) {
          mNotificationManager.notify(MY_NOTIFICATION, myNotification);
          // IP Registration Successful
          setupHeartbeat(true);
          /*
           * OPTIONAL, poll the update request updateRequest.schedule(new
           * TimerTask() {
           * 
           * @Override public void run() { sendMessage(updateRequestMsg); } },
           * 0, 1000);
           */
          sendMessage(mUpdateRequestMsg);
        } else {
          Utilities.logWarning(input.payload.toString());
        }
      case 0x05: // Data
        try {
          HandleCIPData(input.payload);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;
      case 0x03:
        // Program stopping or disconnect
        break;
      case 0x0D: // Heartbeat timeout
      case 0x0E: // Heartbeat response
        break;
      case 0x0F: // Connection message
        if (input.len == 1) {
          if (input.payload[0] == 0x02) {
            sendMessage(mConnectionMsg);
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

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Runnable#run()
   */
  public void run() {
    if (lock.tryAcquire() == false)
      return; /* One instance allowed only */
    int retry = 0; /* Attempt to connect several times */
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
          for (int i = 0; i < charRead; i += (temp.len + 3)) {
            temp = new CIPMessage(data, i);
            try {
              HandleCIPMessage(temp);
            } catch (Exception e) {
              Utilities.logWarning("Malformed byte stream");
              e.printStackTrace();
            }
          }
        }
      } catch (IOException e) {
        // Failed to connect
        mConnectFailedToast.show();
        try {
          if (mSocket != null)
            mSocket.close();
          Thread.sleep(TIME_TO_RECONNECT);
          if (++retry >= MAX_RETRIES) {
            mHome.finish();
            break;
          } else {
            mConnectFailedToast.cancel();
            Thread.sleep(500);
          }
        } catch (InterruptedException e1) {
          // Attempted to abort (normal)
          e1.printStackTrace();
        } catch (IOException e2) {
          // Still failed to connect
          e2.printStackTrace();
        }
      } catch (Exception e) {
        // Catch all
        e.printStackTrace();
      }
    }
    Utilities.logWarning("Server thread over");
    lock.release();
  }
}
