/*  HomeAutomationApp - A Crestron XPanel Android Application
 *  Copyright (C) 2012 Aleks Rozman aleks.rozman@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.HomeAutomationApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.HomeAutomationApp.R;
import com.InputTypes.DigitalButton;
import com.InputTypes.InputHandlerIf;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.preference.PreferenceManager;

/**
 * @author stealthflyer
 * 
 *         Main activity for the app
 * 
 */
public class HomeAutomationApp extends Activity {
  /** Called when the activity is first created. */

  private ArrayList<Map<Integer, InputHandlerIf>> inputList = new ArrayList<Map<Integer, InputHandlerIf>>();

  private static final int EDIT_ID = Menu.FIRST + 2;
  private static final int CONNECT_ID = Menu.FIRST + 3;
  private static final int DISCONNECT_ID = Menu.FIRST + 4;

  private static final float MIN_SWIPE_DISTANCE = 75;
  private static final float EDGE_THRESHOLD = 75;
  private float oldTouchValue;

  private Server mServer;
  private Thread mServerThread;
  private PhoneListener mPhone;
  private NetworkListener mNet;
  private ViewFlipper mFlipper;
  private Vibrator myVib;
  private int dButtonPhone = 0;
  private int dButtonSideUp = 0;
  private int dButtonSideDown = 0;

  /**
   * @param ringing
   *          Phone is ringing Toggles the special join associated with a change
   *          in the phone ringing state
   */
  public void phoneActivated(boolean ringing) {
    if (dButtonPhone != 0
        && inputList.get(Utilities.DIGITAL_INPUT).containsKey(dButtonPhone)) {
      if (inputList.get(Utilities.DIGITAL_INPUT).get(dButtonPhone).getState() == !ringing) {
        sendMessage(dButtonPhone, Utilities.DIGITAL_INPUT, "1");
        sendMessage(dButtonPhone, Utilities.DIGITAL_INPUT, "0");
      }
    }
  }

  /**
   * @param connected
   *          Network connected
   * 
   *          Restarts the server thread when the connection is re-connected
   */
  public void networkChanged(boolean connected) {
    Utilities.logDebug("Network has been "
        + ((connected) ? "connected" : "disconnected"));
    if (connected) {
      if (mServer == null) {
        startThread();
      } else if (mServer.status() == false) {
        terminateThread();
        startThread();
      }
    } else {
      terminateThread();
    }
  }

  /**
   * @param join
   *          received
   * @param type
   *          received
   * @param val
   *          received
   */
  public void serverCallback(int join, int type, String val) {
    if (type >= Utilities.DIGITAL_INPUT && type <= Utilities.SERIAL_INPUT) {
      if (inputList.get(type).containsKey(join)) {
        inputList.get(type).get(join).setValue(val);
      }
    } else {
      Utilities.logWarning("ID " + Integer.toString(join)
          + " has no registered callback for type " + Integer.toString(type));
    }
  }

  /**
   * @param i
   *          Hander interface
   * @param join
   *          of input
   * @param type
   *          of input
   * 
   *          Append the input to the list for notification of state change,
   *          also registers special joins
   * 
   */
  public void registerInput(InputHandlerIf i, int join, int type) {
    if (type >= Utilities.DIGITAL_INPUT && type <= Utilities.SERIAL_INPUT) {
      inputList.get(type).put(new Integer(join), i);
      if (i instanceof DigitalButton) {
        switch (((DigitalButton) i).special) {
          case 1:
            dButtonSideUp = join;
            break;
          case 2:
            dButtonSideDown = join;
            break;
          case 3:
            dButtonPhone = join;
            break;
        }
      }
      Utilities.logInformational("Join " + Integer.toString(join)
          + " registered to type " + Integer.toString(type));
    } else {
      Utilities.logWarning("Join " + Integer.toString(join)
          + " failed due to bad type " + Integer.toString(type));
    }
  }

  /**
   * Restores the state of the buttons after the activity comes back
   */
  private void restoreButtonStates() {
    Iterator<Map<Integer, InputHandlerIf>> ix = inputList.iterator();
    while (ix.hasNext()) {
      Iterator<java.util.Map.Entry<Integer, InputHandlerIf>> it;
      it = ix.next().entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<Integer, InputHandlerIf> pairs = it.next();
        pairs.getValue().restoreState();
      }
    }
  }

  /**
   * @param join
   *          of input
   * @param type
   *          of input
   * @param s
   *          value to send
   * 
   *          Server call, string chosen as the third parameter to be compatible
   *          with serial
   */
  public void sendMessage(int join, int type, String s) {
    // Join is 1 based, we send 0 based
    if (mServer != null && join != 0) {
      switch (type) {
        case Utilities.DIGITAL_INPUT:
          mServer.sendDigital(join - 1, Integer.parseInt(s));
          break;
        case Utilities.ANALOG_INPUT:
          mServer.sendAnalog(join - 1, Integer.parseInt(s));
          break;
        default:
          mServer.sendSerial(join - 1, s);
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onCreate(android.os.Bundle)
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Digital
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Analog
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Serial
    super.onCreate(savedInstanceState);
    try {
      setContentView(R.layout.main);
      mPhone = new PhoneListener(this);
      mNet = new NetworkListener(this);
      registerReceiver(mPhone, new IntentFilter(
          TelephonyManager.ACTION_PHONE_STATE_CHANGED));
      registerReceiver(mNet, new IntentFilter(
          ConnectivityManager.CONNECTIVITY_ACTION));
      /* startThread(); gets called by the network intent */
      mFlipper = (ViewFlipper) findViewById(R.id.viewflip);
      myVib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    } catch (Exception x) {
      Utilities.logDebug(x.getMessage());
    }
  }

  /**
   * Activate haptic feedback if necessary
   */
  public void VibrateButton() {
    myVib.vibrate(40);
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case EDIT_ID:
        terminateThread();
        startActivity(new Intent(this, EditPreferences.class));
        return true;
      case CONNECT_ID:
        terminateThread();
        startThread();
        return true;
      case DISCONNECT_ID:
        terminateThread();
        return true;
    }
    return (super.onOptionsItemSelected(item));
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
   */
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    try {
      if (mServer != null) {
        menu.findItem(CONNECT_ID).setVisible(!mServer.status());
        menu.findItem(DISCONNECT_ID).setVisible(mServer.status());
      } else {
        menu.findItem(CONNECT_ID).setVisible(true);
        menu.findItem(DISCONNECT_ID).setVisible(false);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return super.onPrepareOptionsMenu(menu);
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, EDIT_ID, Menu.NONE, "Settings")
        .setIcon(R.drawable.settings).setAlphabeticShortcut('e');
    menu.add(Menu.NONE, DISCONNECT_ID, Menu.NONE, "Disconnect")
        .setIcon(R.drawable.agt_stop).setAlphabeticShortcut('d');
    menu.add(Menu.NONE, CONNECT_ID, Menu.NONE, "Connect")
        .setIcon(R.drawable.connect_creating).setAlphabeticShortcut('c');
    return super.onCreateOptionsMenu(menu);
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onOptionsMenuClosed(android.view.Menu)
   */
  @Override
  public void onOptionsMenuClosed(Menu menu) {
    restoreButtonStates();
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onTouchEvent(android.view.MotionEvent)
   * 
   * Fling detection algorithm to allow switching between views. Better than
   * gestures to ensure full control, however high CPU usage
   * 
   * NOTE: As a result, cannot fling on inputs like buttons or the analog bar
   * primarily because the inputs send state right away rather than wait to see
   * if the user is performing an action
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    try {
      final View currentView = mFlipper.getCurrentView();
      int lindex = mFlipper.getDisplayedChild() - 1;
      int rindex = mFlipper.getDisplayedChild() + 1;
      View leftView = null;
      View rightView = null;
      boolean switchRight = false;
      boolean switchLeft = false;
      if (lindex >= 0) {
        leftView = mFlipper.getChildAt(lindex);
      }
      if (rindex < mFlipper.getChildCount()) {
        rightView = mFlipper.getChildAt(rindex);
      }
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          // User has activated the screen, store current position
          oldTouchValue = event.getX();
          for (int i = 0; i < mFlipper.getChildCount(); i++) {
            if (i != mFlipper.getDisplayedChild())
              mFlipper.getChildAt(i).setVisibility(View.INVISIBLE);
          }
          return false;
        }
        case MotionEvent.ACTION_UP: {
          // User has let go of the screen, need to detect if thresholds have
          // been met and move to new view or restore
          float currentX = event.getX();
          if (oldTouchValue == currentX) {
            if (currentX < EDGE_THRESHOLD)
              switchLeft = true;
            if (currentView.getWidth() - currentX < EDGE_THRESHOLD)
              switchRight = true;
            if (!switchLeft && !switchRight)
              return false;
          }
          if (oldTouchValue < currentX) {
            if (leftView == null) {
              leftView = mFlipper.getChildAt(mFlipper.getChildCount() - 1);
            }
            if ((currentX - oldTouchValue) < MIN_SWIPE_DISTANCE
                || currentX < EDGE_THRESHOLD) {
              currentView.layout(0, currentView.getTop(),
                  (int) (currentView.getWidth()), currentView.getBottom());
              leftView.setVisibility(View.INVISIBLE);
              break;
            }
            switchLeft = true;
          }
          if (oldTouchValue > currentX) {
            if (rightView == null) {
              rightView = mFlipper.getChildAt(0);
            }
            if ((oldTouchValue - currentX) < MIN_SWIPE_DISTANCE
                || (currentView.getWidth() - currentX) < EDGE_THRESHOLD) {
              currentView.layout(0, currentView.getTop(),
                  (int) (currentView.getWidth()), currentView.getBottom());
              rightView.setVisibility(View.INVISIBLE);
              break;
            }
            switchRight = true;
          }
          if (switchRight) {
            float temp = (currentView.getWidth() - (oldTouchValue - currentX))
                / currentView.getWidth();
            mFlipper.setInAnimation(Utilities.inFromRightAnimation(temp));
            mFlipper.setOutAnimation(Utilities.outToLeftAnimation(temp));
            mFlipper.showNext();
          }
          if (switchLeft) {
            float temp = (currentView.getWidth() - (currentX - oldTouchValue))
                / currentView.getWidth();
            mFlipper.setInAnimation(Utilities.inFromLeftAnimation(temp));
            mFlipper.setOutAnimation(Utilities.outToRightAnimation(temp));
            mFlipper.showPrevious();
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          // User is moving, append the views and make it look seemless
          float currentX = event.getX();
          int temp = (int) (currentX - oldTouchValue);
          currentView.layout(temp, currentView.getTop(),
              temp + currentView.getWidth(), currentView.getBottom());
          if (temp < 0) {
            if (rightView == null)
              rightView = mFlipper.getChildAt(0);
            rightView.layout(currentView.getRight(), rightView.getTop(),
                currentView.getRight() + rightView.getWidth(),
                rightView.getBottom());
            rightView.setVisibility(View.VISIBLE);
          } else if (temp > 0) {
            if (leftView == null)
              leftView = mFlipper.getChildAt(mFlipper.getChildCount() - 1);
            leftView.layout(currentView.getLeft() - leftView.getWidth(),
                leftView.getTop(), currentView.getLeft(), leftView.getBottom());
            leftView.setVisibility(View.VISIBLE);
          }
          return true;
        }
      }
    } catch (Exception x) {
    }
    return false;
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onResume()
   */
  @Override
  public void onResume() {
    restoreButtonStates();
    super.onResume();
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
   * 
   * Activate special buttons (press)
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      if (event.getRepeatCount() == 0) {
        sendMessage(dButtonSideUp, Utilities.DIGITAL_INPUT, "1");
      }
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      if (event.getRepeatCount() == 0) {
        sendMessage(dButtonSideDown, Utilities.DIGITAL_INPUT, "1");
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onKeyUp(int, android.view.KeyEvent)
   * 
   * Activate special buttons (release)
   */
  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      if (event.getRepeatCount() == 0) {
        sendMessage(dButtonSideUp, Utilities.DIGITAL_INPUT, "0");
      }
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      if (event.getRepeatCount() == 0) {
        sendMessage(dButtonSideDown, Utilities.DIGITAL_INPUT, "0");
      }
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      this.finish();
    }
    return super.onKeyDown(keyCode, event);
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onDestroy()
   */
  @Override
  public void onDestroy() {
    terminateThread();
    unregisterReceiver(mPhone);
    unregisterReceiver(mNet);
    super.onDestroy();
  }

  /**
   * Activate the server thread to start listening and communicating
   */
  private void startThread() {
    try {
      SharedPreferences prefs = PreferenceManager
          .getDefaultSharedPreferences(this);
      try {
        if (mServer == null) {
          mServer = new Server(this, prefs.getString("ip", ""),
              Integer.parseInt(prefs.getString("port", "0")),
              Integer.parseInt(prefs.getString("id", "0")));
          mServerThread = new Thread(mServer);
          mServerThread.start();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      Toast.makeText(getApplicationContext(), "Failure to parse preferences",
          Toast.LENGTH_LONG).show();
      e.printStackTrace();
    }
  }

  /**
   * Shutdown server thread to stop communication
   */
  private void terminateThread() {
    if (mServer != null) {
      mServer.shutdown();
      mServer = null;
    }
  }

}