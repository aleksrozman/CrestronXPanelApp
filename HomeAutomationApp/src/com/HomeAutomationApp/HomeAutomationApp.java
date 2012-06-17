package com.HomeAutomationApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.HomeAutomationApp.R;
import com.InputTypes.DigitalButton;
import com.InputTypes.InputHandlerIf;

import android.R.integer;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
  private ViewFlipper mFlipper;
  private Vibrator myVib;
  private int dButtonPhone = 0;
  private int dButtonSideUp = 0;
  private int dButtonSideDown = 0;

  public void phoneActivated(boolean ringing) {
    if (dButtonPhone != 0 && inputList.get(Utilities.DIGITAL_INPUT).containsKey(dButtonPhone)) {
      if (inputList.get(Utilities.DIGITAL_INPUT).get(dButtonPhone).getState() == !ringing) {
        sendMessage(dButtonPhone, Utilities.DIGITAL_INPUT, "1");
        sendMessage(dButtonPhone, Utilities.DIGITAL_INPUT, "0");
      }
    }
  }

  public void serverCallback(int idInt, int typeInt, String val) {
    if (typeInt >= Utilities.DIGITAL_INPUT && typeInt <= Utilities.SERIAL_INPUT) {
      if (inputList.get(typeInt).containsKey(idInt)) {
        inputList.get(typeInt).get(idInt).setValue(val);
      }
    } else {
      Utilities.logWarning("ID " + Integer.toString(idInt) + " has no registered callback for type " + Integer.toString(typeInt));
    }
  }

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
      Utilities.logInformational("Join " + Integer.toString(join) + " registered to type " + Integer.toString(type));
    } else {
      Utilities.logWarning("Join " + Integer.toString(join) + " failed due to bad type " + Integer.toString(type));
    }
  }

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

  public void sendMessage(int join, int type, String s) {
    // Join is 1 based, we send 0 based
    if (mServer != null) {
      switch(type) {
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

  @Override
  public void onCreate(Bundle savedInstanceState) {
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Digital
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Analog
    inputList.add(new HashMap<Integer, InputHandlerIf>()); // Serial
    super.onCreate(savedInstanceState);
    try {
      setContentView(R.layout.main);
      mPhone = new PhoneListener(this);
      registerReceiver(mPhone, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
      startThread();
      mFlipper = (ViewFlipper) findViewById(R.id.viewflip);
      myVib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    } catch (Exception x) {
      Utilities.logDebug(x.getMessage());
    }
  }

  public void VibrateButton() {
    myVib.vibrate(50);
  }

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

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, EDIT_ID, Menu.NONE, "Settings").setIcon(R.drawable.settings).setAlphabeticShortcut('e');
    menu.add(Menu.NONE, DISCONNECT_ID, Menu.NONE, "Disconnect").setIcon(R.drawable.agt_stop).setAlphabeticShortcut('d');
    menu.add(Menu.NONE, CONNECT_ID, Menu.NONE, "Connect").setIcon(R.drawable.connect_creating)
        .setAlphabeticShortcut('c');
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public void onOptionsMenuClosed(Menu menu) {
    restoreButtonStates();
  }

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
        oldTouchValue = event.getX();
        for (int i = 0; i < mFlipper.getChildCount(); i++) {
          if (i != mFlipper.getDisplayedChild())
            mFlipper.getChildAt(i).setVisibility(View.INVISIBLE);
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
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
          if ((currentX - oldTouchValue) < MIN_SWIPE_DISTANCE || currentX < EDGE_THRESHOLD) {
            currentView.layout(0, currentView.getTop(), (int) (currentView.getWidth()), currentView.getBottom());
            leftView.setVisibility(View.INVISIBLE);
            break;
          }
          switchLeft = true;
        }
        if (oldTouchValue > currentX) {
          if (rightView == null) {
            rightView = mFlipper.getChildAt(0);
          }
          if ((oldTouchValue - currentX) < MIN_SWIPE_DISTANCE || (currentView.getWidth() - currentX) < EDGE_THRESHOLD) {
            currentView.layout(0, currentView.getTop(), (int) (currentView.getWidth()), currentView.getBottom());
            rightView.setVisibility(View.INVISIBLE);
            break;
          }
          switchRight = true;
        }
        if (switchRight) {
          float temp = (currentView.getWidth() - (oldTouchValue - currentX)) / currentView.getWidth();
          mFlipper.setInAnimation(Utilities.inFromRightAnimation(temp));
          mFlipper.setOutAnimation(Utilities.outToLeftAnimation(temp));
          mFlipper.showNext();
        }
        if (switchLeft) {
          float temp = (currentView.getWidth() - (currentX - oldTouchValue)) / currentView.getWidth();
          mFlipper.setInAnimation(Utilities.inFromLeftAnimation(temp));
          mFlipper.setOutAnimation(Utilities.outToRightAnimation(temp));
          mFlipper.showPrevious();
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        float currentX = event.getX();
        int temp = (int) (currentX - oldTouchValue);
        currentView.layout(temp, currentView.getTop(), temp + currentView.getWidth(), currentView.getBottom());
        if (temp < 0) {
          if (rightView == null)
            rightView = mFlipper.getChildAt(0);
          rightView.layout(currentView.getRight(), rightView.getTop(), currentView.getRight() + rightView.getWidth(),
              rightView.getBottom());
          rightView.setVisibility(View.VISIBLE);
        } else if (temp > 0) {
          if (leftView == null)
            leftView = mFlipper.getChildAt(mFlipper.getChildCount() - 1);
          leftView.layout(currentView.getLeft() - leftView.getWidth(), leftView.getTop(), currentView.getLeft(),
              leftView.getBottom());
          leftView.setVisibility(View.VISIBLE);
        }
        return true;
      }
      }
    } catch (Exception x) {
    }
    return false;
  }

  @Override
  public void onResume() {
    restoreButtonStates();
    super.onResume();
  }

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

  @Override
  public void onDestroy() {
    terminateThread();
    unregisterReceiver(mPhone);
    super.onDestroy();
  }

  private void startThread() {
    try {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      try {
        if (mServer == null) {
          mServer = new Server(this, prefs.getString("ip", ""), Integer.parseInt(prefs.getString("port", "0")), Integer.parseInt(prefs.getString("id", "0")));
          mServerThread = new Thread(mServer);
          mServerThread.start();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      Toast.makeText(getApplicationContext(), "Failure to parse preferences", Toast.LENGTH_LONG).show();
      e.printStackTrace();
    }
  }

  private void terminateThread() {
    if (mServer != null) {
      mServer.shutdown();
      mServer = null;
    }
  }

}