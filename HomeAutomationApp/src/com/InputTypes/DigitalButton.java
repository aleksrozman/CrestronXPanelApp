package com.InputTypes;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

import com.HomeAutomationApp.HomeAutomationApp;
import com.HomeAutomationApp.Utilities;

/**
 * @author stealthflyer
 * 
 *         Add features to button to to allow it to send/receive values to the
 *         server (passes through the application context)
 */
public class DigitalButton extends Button implements InputHandlerIf {

  public int join;
  public int special;
  private boolean state;
  private boolean expectingFeedback; /*
                                      * Offer the user a more interactive
                                      * experience. The assumption is that
                                      * pressing a button will return feedback
                                      * (press) which can trigger vibration
                                      */

  public DigitalButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs);
  }

  public DigitalButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public DigitalButton(Context context) {
    super(context);

    throw new RuntimeException(
        "Valid parameters must be passed to this class via the XML parameters: app:join.");
  }

  private void init(AttributeSet attrs) {
    expectingFeedback = false;
    if (!isInEditMode()) {
      join = attrs
          .getAttributeIntValue(
              "http://schemas.android.com/apk/res/com.HomeAutomationApp",
              "join", 0);
      if (join < 1 || join > 1000) { // Just a sanity check (though a large
                                     // number
                                     // is OK we want to make sure its not
                                     // extreme)
        throw new RuntimeException("The join number specified is invalid");
      } else {
        special = attrs.getAttributeIntValue(
            "http://schemas.android.com/apk/res/com.HomeAutomationApp",
            "special", 0);
        ((HomeAutomationApp) getContext()).registerInput(this, join,
            Utilities.DIGITAL_INPUT);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        ((HomeAutomationApp) getContext()).sendMessage(join,
            Utilities.DIGITAL_INPUT, "1");
        expectingFeedback = true;
        break;
      }
      case MotionEvent.ACTION_UP: {
        ((HomeAutomationApp) getContext()).sendMessage(join,
            Utilities.DIGITAL_INPUT, "0");
        break;
      }
    }
    return true;
  }

  private Handler buttonHandler = new Handler() {
    public void handleMessage(Message msg) {
      state = (msg.what == 1);
      if (expectingFeedback) {
        expectingFeedback = false;
        ((HomeAutomationApp) getContext()).VibrateButton();
      }
      setPressed(state);
    }
  };

  public void setValue(String v) {
    if (v.equals("0"))
      off();
    else
      on();
  }

  public void on() {
    buttonHandler.sendEmptyMessage(1);
  }

  public void off() {
    buttonHandler.sendEmptyMessage(0);
  }

  public boolean getState() {
    return state;
  }

  public void restoreState() {
    if (state == true) {
      on();
    } else {
      off();
    }
  }

  public void setCaption(String c) {
    // Does nothing
  }

}
