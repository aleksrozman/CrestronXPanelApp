/**
 * 
 */
package com.CrestronXPanelApp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author stealthflyer
 * 
 */
public class AppFragment extends Fragment {
  private int layoutId = 0;

  static AppFragment newInstance(int id) {
    AppFragment f = new AppFragment();
    Bundle args = new Bundle();
    args.putInt("id", id);
    f.setArguments(args);
    return f;
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      layoutId = getArguments().getInt("id");
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    if (layoutId == 0) {
      Utilities.logWarning("Layout id has not been initialized");
      return null;
    } else {
      View rootView = inflater.inflate(layoutId, container, false);
      return rootView;
    }
  }
}
