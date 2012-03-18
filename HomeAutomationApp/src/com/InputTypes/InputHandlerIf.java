package com.InputTypes;

public interface InputHandlerIf {
  public int join = 0;
  
  public boolean getState();

  public int getValue();

  public void setValue(String v);

  public void restoreState();
}