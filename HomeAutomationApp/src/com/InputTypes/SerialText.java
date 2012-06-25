package com.InputTypes;

import com.HomeAutomationApp.HomeAutomationApp;
import com.HomeAutomationApp.R;
import com.HomeAutomationApp.Utilities;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * @author stealthflyer
 * 
 *         Add features to textview to to allow it to send/receive values to the
 *         server (passes through the application context)
 */
public class SerialText extends TextView implements InputHandlerIf {

  public int join;
  private String caption;
  private Handler h = new Handler();

  public SerialText(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs);
  }

  public SerialText(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public SerialText(Context context) {
    super(context);

    throw new RuntimeException(
        "Valid parameters must be passed to this class via the XML parameters: app:join.");
  }

  private void init(AttributeSet attrs) {
    TypedArray a = getContext().obtainStyledAttributes(attrs,
        R.styleable.SerialText);
    join = a.getInteger(R.styleable.SerialText_sjoin, 0);
    if (join < 1 || join > 1000) { // Sanity check (see digital button)
      throw new RuntimeException("The join number specified is invalid");
    } else {
      ((HomeAutomationApp) getContext()).registerInput(this, join,
          Utilities.SERIAL_INPUT);
    }
  }

  public boolean getState() {
    // Nothing
    return false;
  }

  public void setValue(String v) {
    caption = v;
    h.post(updateCaption);
  }

  public void restoreState() {
    setValue(caption);
  }

  Runnable updateCaption = new Runnable() {
    public void run() {
      setText(caption);
    }

  };

}
