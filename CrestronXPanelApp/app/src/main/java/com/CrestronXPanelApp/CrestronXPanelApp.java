/*  CrestronXPanelApp - A Crestron XPanel Android Application
 *  Copyright (C) 2013 Aleks Rozman aleks.rozman@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.CrestronXPanelApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.CrestronXPanelApp.R;
import com.CrestronXPanelApp.EditPreferences;
import com.InputTypes.DigitalButton;
import com.InputTypes.InputHandlerIf;

/**
 * @author stealthflyer
 * 
 *         Main activity for the app
 * 
 */
public class CrestronXPanelApp extends FragmentActivity {
	/** Called when the activity is first created. */

	private ArrayList<Map<Integer, List<InputHandlerIf>>> inputList = new ArrayList<Map<Integer, List<InputHandlerIf>>>();

	class FragEntry {
		private AppFragment frag;
		private String title;

		FragEntry(AppFragment f, String t) {
			frag = f;
			title = t;
		}

		public AppFragment getFrag() {
			return frag;
		}

		@Override
		public String toString() {
			return title;
		}
	}

	public ArrayList<FragEntry> mFragments = new ArrayList<FragEntry>();
	private Hashtable<String, List<InputHandlerIf>> specialList = new Hashtable<String, List<InputHandlerIf>>();
	private Server mServer;
	private Thread mServerThread;
	private PhoneListener mPhone;
	private NetworkListener mNet;
	private ViewPager mViewPager;
	private SectionsPagerAdapter mSectionsPagerAdapter;
	private Vibrator myVib;

	/**
	 * @param ringing
	 *            Phone is ringing Toggles the special join associated with a
	 *            change in the phone ringing state
	 */
	public void phoneActivated(boolean ringing) {
		simulateSpecialButtons("mute", !ringing);
		simulateSpecialButtons("pause", false);
		simulateSpecialButtons("play", false);
	}

	private void simulateSpecialButtons(String name, boolean state) {
		if (specialList.containsKey(name)) {
			for (InputHandlerIf ii : specialList.get(name)) {
				if (ii.getState() == state) {
					simulateDigitalButton(ii);
				}
			}
		}
	}

	private void simulateDigitalButton(InputHandlerIf button) {
		if (button != null) {
			sendMessage(button.getJoin(), Utilities.DIGITAL_INPUT, "1");
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sendMessage(button.getJoin(), Utilities.DIGITAL_INPUT, "0");
		}
	}

	/**
	 * @param connected
	 *            Network connected
	 * 
	 *            Restarts the server thread when the connection is re-connected
	 */
	public void networkChanged(boolean connected) {
		Utilities.logDebug("Network has been "
				+ ((connected) ? "connected" : "disconnected"));
		if (connected) {
			if (mServer == null) {
				startThread();
			} else if (mServer.status() == false) {
				terminateThread();
				startThread();
			}
		} else {
			terminateThread();
		}
	}

	/**
	 * When something comes back from the server, delegate
	 * 
	 * @param join
	 *            received
	 * @param type
	 *            received
	 * @param val
	 *            received
	 */
	public void serverCallback(int join, int type, String val) {
		if (type >= Utilities.DIGITAL_INPUT && type <= Utilities.SERIAL_INPUT) {
			if (inputList.get(type).containsKey(join)) {
				processJoins(join, type, val);
			}
		} else {
			Utilities.logWarning("ID " + Integer.toString(join)
					+ " has no registered callback for type "
					+ Integer.toString(type));
		}
	}

	/**
	 * Iterate through all handlers and update
	 * 
	 * @param join
	 *            received
	 * @param type
	 *            received
	 * @param val
	 *            received
	 */
	private void processJoins(int join, int type, String val) {
		for (InputHandlerIf ii : inputList.get(type).get(join)) {
			if (ii.getClass().equals(DigitalButton.class)
					&& (type == Utilities.SERIAL_INPUT)) {
				((DigitalButton) ii).setCaption(val);
			} else {
				ii.setValue(val);
			}
		}
	}

	/**
	 * @param i
	 *            Hander interface
	 * @param join
	 *            of input
	 * @param type
	 *            of input
	 * 
	 *            Append the input to the list for notification of state change,
	 *            also registers special joins
	 * 
	 */
	public void registerInput(InputHandlerIf i, int join, int type) {
		if (type >= Utilities.DIGITAL_INPUT && type <= Utilities.SERIAL_INPUT) {

			List<InputHandlerIf> list = inputList.get(type).get(join);

			if (list == null)
				inputList.get(type).put(join,
						list = new ArrayList<InputHandlerIf>());

			list.add(i);
			if (i.getSpecial() != null && !i.getSpecial().isEmpty()) {
				List<InputHandlerIf> specials = specialList.get(i.getSpecial());
				if (specials == null)
					specialList.put(i.getSpecial(),
							specials = new ArrayList<InputHandlerIf>());
				specials.add(i);
			} else {
				Utilities.logWarning("Join " + Integer.toString(join)
						+ " failed due to bad type " + Integer.toString(type));
			}
		}
	}

	/**
	 * Restores the state of the buttons after the activity comes back
	 */
	private void restoreButtonStates() {
		Iterator<Map<Integer, List<InputHandlerIf>>> ix = inputList.iterator();
		while (ix.hasNext()) {
			Iterator<java.util.Map.Entry<Integer, List<InputHandlerIf>>> it;
			it = ix.next().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Integer, List<InputHandlerIf>> pairs = it.next();

				for (InputHandlerIf list : pairs.getValue()) {
					list.restoreState();
				}
			}
		}
	}

	/**
	 * @param join
	 *            of input
	 * @param type
	 *            of input
	 * @param s
	 *            value to send
	 * 
	 *            Server call, string chosen as the third parameter to be
	 *            compatible with serial
	 */
	public void sendMessage(int join, int type, String s) {
		// Join is 1 based, we send 0 based
		if (mServer != null && join != 0) {
			switch (type) {
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

	/**
	 * Shortcut to send a press on the digital members of an input list
	 * 
	 * @param list
	 *            every input to send to
	 * @param val
	 *            1 or 0 to press
	 */
	public void sendDigitalListMessage(List<InputHandlerIf> list, int val) {
		// Join is 1 based, we send 0 based
		if (mServer != null && list != null) {
			for (InputHandlerIf item : list) {
				if (item.getClass().equals(DigitalButton.class)) {
					mServer.sendDigital(item.getJoin() - 1, val);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		inputList.add(new HashMap<Integer, List<InputHandlerIf>>()); // Digital
		inputList.add(new HashMap<Integer, List<InputHandlerIf>>()); // Analog
		inputList.add(new HashMap<Integer, List<InputHandlerIf>>()); // Serial

		initializeFragments();
		super.onCreate(savedInstanceState);
//		startActivity(new Intent(this, EditPreferences.class));

		try {
			setContentView(R.layout.main);
			mPhone = new PhoneListener(this);
			mNet = new NetworkListener(this);
			registerReceiver(mPhone, new IntentFilter(
					TelephonyManager.ACTION_PHONE_STATE_CHANGED));
			registerReceiver(mNet, new IntentFilter(
					ConnectivityManager.CONNECTIVITY_ACTION));
			/* startThread(); gets called by the network intent */
			mViewPager = (ViewPager) findViewById(R.id.pager);
			mSectionsPagerAdapter = new SectionsPagerAdapter(
					getSupportFragmentManager());
			mViewPager.setOffscreenPageLimit(mFragments.size());
			mViewPager.setAdapter(mSectionsPagerAdapter);
			myVib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
			disableScreenLock();
		} catch (Exception x) {
			Utilities.logDebug(x.getMessage());
		}
	}

	/**
	 * Activate haptic feedback if necessary
	 */
	public void VibrateButton() {
		myVib.vibrate(40);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			terminateThread();
			startActivity(new Intent(this, EditPreferences.class));
			return true;
		case R.id.menu_connect:
			terminateThread();
			startThread();
			return true;
		case R.id.menu_disconnect:
			terminateThread();
			return true;
		}
		return (super.onOptionsItemSelected(item));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		try {
			if (mServer != null) {
				menu.findItem(R.id.menu_connect).setVisible(!mServer.status());
				menu.findItem(R.id.menu_disconnect)
						.setVisible(mServer.status());
			} else {
				menu.findItem(R.id.menu_connect).setVisible(true);
				menu.findItem(R.id.menu_disconnect).setVisible(false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return super.onPrepareOptionsMenu(menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings, menu);
		return true;
		// return super.onCreateOptionsMenu(menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsMenuClosed(android.view.Menu)
	 */
	@Override
	public void onOptionsMenuClosed(Menu menu) {
		restoreButtonStates();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		disableScreenLock();
		restoreButtonStates();
		super.onResume();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		enableScreenLock();
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
	 * 
	 * Activate special buttons (press)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			if (event.getRepeatCount() == 0
					&& specialList.containsKey("sideup")) {
				sendDigitalListMessage(specialList.get("sideup"), 1);
			}
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			if (event.getRepeatCount() == 0
					&& specialList.containsKey("sidedown")) {
				sendDigitalListMessage(specialList.get("sidedown"), 1);
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onKeyUp(int, android.view.KeyEvent)
	 * 
	 * Activate special buttons (release)
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			if (event.getRepeatCount() == 0
					&& specialList.containsKey("sideup")) {
				sendDigitalListMessage(specialList.get("sideup"), 0);
			}
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			if (event.getRepeatCount() == 0
					&& specialList.containsKey("sidedown")) {
				sendDigitalListMessage(specialList.get("sidedown"), 0);
			}
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			this.finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	public void onDestroy() {
		terminateThread();
		unregisterReceiver(mPhone);
		unregisterReceiver(mNet);
		super.onDestroy();
	}

	/**
	 * Activate the server thread to start listening and communicating
	 */
	private void startThread() {
		try {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			try {
				if (mServer == null) {
					mServer = new Server(this, prefs.getString("ip", ""),
							Integer.parseInt(prefs.getString("port", "41794")),
							Integer.parseInt(prefs.getString("id", "3"),16));
					mServerThread = new Thread(mServer);
					mServerThread.start();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(),
					"Failure to parse preferences", Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}

	/**
	 * Shutdown server thread to stop communication
	 */
	private void terminateThread() {
		if (mServer != null) {
			mServer.shutdown();
			mServer = null;
		}
	}

	/**
	 * Disable the screen lock
	 */
	private void disableScreenLock() {
		try {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			if (prefs.getBoolean("screenLock", false)) {
				Utilities.stopLocking(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Enable the screen lock
	 */
	private void enableScreenLock() {
		try {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			if (prefs.getBoolean("screenLock", false)) {
				Utilities.startLocking(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates the fragments by parsing the layouts xml document
	 */
	public void initializeFragments() {
		XmlResourceParser xr = getResources().getXml(R.xml.layouts);
		int eventType = XmlPullParser.END_DOCUMENT;
		try {
			eventType = xr.getEventType();
		} catch (XmlPullParserException e) {
			throw new RuntimeException(
					"Cannot parse the layouts file, no way to show");
		}
		while (eventType != XmlPullParser.END_DOCUMENT) {
			if (eventType == XmlPullParser.START_TAG
					&& xr.getName().equals("item")) {
				int layout = xr.getAttributeResourceValue(null, "layout", 0);
				String name = xr.getAttributeValue(null, "title");
				mFragments.add(new FragEntry(AppFragment.newInstance(layout),
						name));
			}
			try {
				eventType = xr.next();
			} catch (Exception e) {
				break; // TODO better handling here
			}
		}
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the primary sections of the app.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		/**
		 * @param fm
		 *            Fragment manager
		 */
		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.support.v4.app.FragmentPagerAdapter#getItem(int)
		 */
		@Override
		public Fragment getItem(int position) {
			if (position < mFragments.size())
				return mFragments.get(position).getFrag();
			else
				return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.support.v4.view.PagerAdapter#getCount()
		 */
		@Override
		public int getCount() {
			return mFragments.size();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.support.v4.view.PagerAdapter#getPageTitle(int)
		 */
		@Override
		public CharSequence getPageTitle(int position) {
			if (position < mFragments.size())
				return mFragments.get(position).toString();
			else
				return null;
		}

	}

}
