package com.HomeAutomationApp;

import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

public class Utilities {

  private static final String MY_ACTIVITY = "ALEKS";
  public static final int DIGITAL_INPUT = 0;
  public static final int ANALOG_INPUT = 1;
  public static final int SERIAL_INPUT = 2;


  public static Animation inFromRightAnimation(float param) {

    Animation inFromRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, +param, Animation.RELATIVE_TO_PARENT,
        0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
    inFromRight.setDuration((long) (500 * param));
    inFromRight.setInterpolator(new AccelerateInterpolator(.5f));
    return inFromRight;
  }

  public static Animation inFromLeftAnimation(float param) {
    Animation inFromLeft = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, -param, Animation.RELATIVE_TO_PARENT,
        0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
    inFromLeft.setDuration((long) (500 * param));
    inFromLeft.setInterpolator(new AccelerateInterpolator(.5f));
    return inFromLeft;
  }

  public static Animation outToLeftAnimation(float param) {
    Animation outtoLeft = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
        -param, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
    outtoLeft.setDuration((long) (500 * param));
    outtoLeft.setInterpolator(new AccelerateInterpolator(.5f));
    return outtoLeft;
  }

  public static Animation outToRightAnimation(float param) {
    Animation outtoRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
        +param, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
    outtoRight.setDuration((long) (500 * param));
    outtoRight.setInterpolator(new AccelerateInterpolator(.5f));
    return outtoRight;
  }
  


  public static void logDebug(String s) {
    Log.d(MY_ACTIVITY, s);
  }

  public static void logInformational(String s) {
    Log.i(MY_ACTIVITY, s);
  }

  public static void logWarning(String s) {
    Log.w(MY_ACTIVITY, s);
  }

}
