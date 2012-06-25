package com.InputTypes;

/**
 * @author stealthflyer
 * 
 *         Base class for each input to allow all of them to be passed around
 *         generically
 * 
 */
public interface InputHandlerIf {
  public int join = 0;

  public boolean getState();

  public void setValue(String v);

  public void restoreState();
}