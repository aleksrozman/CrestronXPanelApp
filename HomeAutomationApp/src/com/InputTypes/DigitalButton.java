package com.InputTypes;

import com.HomeAutomationApp.HomeAutomationApp;
import com.HomeAutomationApp.R;
import com.HomeAutomationApp.Utilities;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

public class DigitalButton extends Button implements InputHandlerIf {

  public int join;
  public int special;
  private boolean state;
  private boolean expectingFeedback;

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

    throw new RuntimeException("Valid parameters must be passed to this class via the XML parameters: app:join.");
  }

  private void init(AttributeSet attrs) {
    expectingFeedback = false;
    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.DigitalButton);
    join = a.getInteger(R.styleable.DigitalButton_join, 0);
    if (join < 1 || join > 1000) {
      throw new RuntimeException("The join number specified is invalid");
    } else {
      special = a.getInteger(R.styleable.DigitalButton_special, 0);
      ((HomeAutomationApp) getContext()).registerInput(this, join, Utilities.DIGITAL_INPUT);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()) {
    case MotionEvent.ACTION_DOWN: {
      ((HomeAutomationApp) getContext()).sendMessage(join, Utilities.DIGITAL_INPUT, "1");
      expectingFeedback = true;
      break;
    }
    case MotionEvent.ACTION_UP: {
      ((HomeAutomationApp) getContext()).sendMessage(join, Utilities.DIGITAL_INPUT, "0");
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
