package com.InputTypes;

/**
 * @author stealthflyer
 * 
 *         Base class for each input to allow all of them to be passed around
 *         generically
 * 
 */
public interface InputHandlerIf {
  public boolean getState();

  public void setValue(String v);

  public void restoreState();
  
  public int getJoin();
  
  public String getSpecial();
}
