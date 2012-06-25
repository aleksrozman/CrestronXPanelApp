package com.InputTypes;

import com.HomeAutomationApp.HomeAutomationApp;
import com.HomeAutomationApp.R;
import com.HomeAutomationApp.Utilities;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;

/**
 * @author stealthflyer
 * 
 *         Add features to seekbar to allow it to send/receive values to the
 *         server (passes through the application context)
 */
public class AnalogBar extends SeekBar implements InputHandlerIf {

  public int join;
  private int value;
  private int expectingFeedback;
  private static final int MAXIMUM_VALUE = 65535;

  public AnalogBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs);
  }

  public AnalogBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public AnalogBar(Context context) {
    super(context);

    throw new RuntimeException(
        "Valid parameters must be passed to this class via the XML parameters: app:join.");
  }

  private void init(AttributeSet attrs) {
    setMax(MAXIMUM_VALUE);
    setOnSeekBarChangeListener(listen);
    expectingFeedback = 0;
    TypedArray a = getContext().obtainStyledAttributes(attrs,
        R.styleable.AnalogBar);
    join = a.getInteger(R.styleable.AnalogBar_ajoin, 0);
    if (join < 1 || join > 1000) { // Sanity check (see digital button)
      throw new RuntimeException("The join number specified is invalid");
    } else {
      ((HomeAutomationApp) getContext()).registerInput(this, join,
          Utilities.ANALOG_INPUT);
    }
  }

  public void setValue(String v) {
    setValue(Integer.parseInt(v));
  }

  public boolean getState() {
    return (value == 0);
  }

  public void setValue(int v) {
    if (expectingFeedback <= 0) {
      setProgress(v);
      expectingFeedback = 0;
    } else {
      expectingFeedback--;
    }
  }

  public void restoreState() {
    setValue(value);
  }

  OnSeekBarChangeListener listen = new OnSeekBarChangeListener() {

    public void onStopTrackingTouch(SeekBar seekBar) {
      // TODO Auto-generated method stub

    }

    public void onStartTrackingTouch(SeekBar seekBar) {
      // TODO Auto-generated method stub

    }

    public void onProgressChanged(SeekBar seekBar, int progress,
        boolean fromUser) {
      value = progress;
      ((HomeAutomationApp) getContext()).sendMessage(join,
          Utilities.ANALOG_INPUT, Integer.toString(progress));
      expectingFeedback++;
    }
  };

  public void setCaption(String c) {
    // Does nothing
  }

}
