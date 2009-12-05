/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomgibara.android.veecheck;

import static com.tomgibara.android.veecheck.Veecheck.LOG_TAG;

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.util.Log;

/**
 * An abstract base class for creating a {@link BroadcastReceiver} that will
 * respond to requests to consider or reschedule checks for new application
 * versions. This class should be extended with an implementation of the
 * {@link #createSettings(Context)} method.
 * 
 * @author Tom Gibara
 *
 */

public abstract class VeecheckReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(LOG_TAG, "Receiver called");
		String action = intent.getAction();
		if (action == null) return;
		
		Log.v(LOG_TAG, "Receiver called with action: " + action);
		if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
			syncChecking(context);
		} else if (action.equals(Veecheck.getRescheduleAction(context))) {
			syncChecking(context);
		} else if (action.equals(Veecheck.getConsiderAction(context))) {
			considerChecking(context);
		}
	}

	/**
	 * Supplies the settings that govern the operation of this
	 * {@link BroadcastReceiver} within the given context.
	 * 
	 * @param context the context within which this {@link BroadcastReceiver} is
	 * operating
	 * 
	 * @return the settings for this {@link BroadcastReceiver}, never null
	 */
	
	protected abstract VeecheckSettings createSettings(Context context);

	/**
	 * Supplies an object that can persist the state information needed by the
	 * veecheck within the given context.
	 * 
	 * @param context the context within which this {@link BroadcastReceiver} is
	 * operating

	 * @return the state of the veecheck system
	 */
	
	protected abstract VeecheckState createState(Context context);
	
	/**
	 * Constructs a {@link PendingIntent} that will be broadcast by the
	 * {@link AlarmManager} to prompt this {@link BroadcastReceiver} to consider
	 * checking for updates.
	 * 
	 * @param context the context in which the {@link BroadcastReceiver} is operating
	 * @return an intent, pending to {@link Veecheck#getConsiderAction(Context)}
	 */
	
	private PendingIntent createCheckingIntent(Context context) {
		return PendingIntent.getBroadcast(context, 0, new Intent(Veecheck.getConsiderAction(context)), 0);
	}
	
	/**
	 * Makes the periodic scheduling of {@link #ACTION_CONSIDER_CHECK}
	 * {@link Intent}s constent with the settings supplied by
	 * {@link #createSettings(Context)}.
	 * 
	 * @param context the context in which the {@link BroadcastReceiver} is operating
	 */
	
	private void syncChecking(Context context) {
		VeecheckSettings settings = createSettings(context);
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		PendingIntent pending = createCheckingIntent(context);
		if (settings.isEnabled()) {
			Log.d(LOG_TAG, "Registering checks with alarm service");
			long now = System.currentTimeMillis();
			long period = settings.getPeriod();
			//could create a Random object here to avoid cost of double, but probably not worthwhile
			long offset = (long) (Math.random() * period);
			Log.v(LOG_TAG, "Check period is " + period +"ms starting from " + new Date(now+offset));
			manager.setRepeating(AlarmManager.RTC, now+offset, period, pending);
		} else {
			Log.d(LOG_TAG, "de-registering checks with alarm service");
			manager.cancel(pending);
		}
	}

	/**
	 * Consults the settings to determine whether a {@link Service} should be
	 * started to perform a check for application updates.
	 * 
	 * @param context the context in which the {@link BroadcastReceiver} is operating
	 */
	
	private void considerChecking(Context context) {
		Log.d(LOG_TAG, "Considering performing check.");
		VeecheckSettings settings = createSettings(context);
		if (!settings.isEnabled()) {
			syncChecking(context);
			return;
		}
		Log.d(LOG_TAG, "Checking is enabled.");
		
		String uri = settings.getCheckUri();
		if (uri == null) return; //no point continuing - not configured
		Log.d(LOG_TAG, "URI is available.");
		
		VeecheckState state = createState(context);
		long now = System.currentTimeMillis();
		long lastCheck = state.getLastCheck();
		if (lastCheck >= 0L && lastCheck + settings.getCheckInterval() > now) {
			return; //it has run too recently
		}
		Log.d(LOG_TAG, "Last check was not too recent.");
		
		//TODO get battery status: not possible yet, see:
		//http://code.google.com/p/android/issues/detail?id=926
		int status = BatteryManager.BATTERY_STATUS_CHARGING;
		if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
			return; //run some other, better, time
		}
		//Log.d(LOG_TAG, "Battery is in a suitable state.");

		//attach our package as an extra on the intent
		Intent intent = new Intent(Veecheck.getCheckAction(context), Uri.parse(uri));
		context.startService(intent);
		//Note: this is an awkward part of the design...
		//It might seem ideal to have this called by the Service only when a
		//check has been successfully made, but this is not obviously the right
		//design.
		state.setLastCheckNow(now);
		Log.d(LOG_TAG, "Last check date was updated.");
	}
	
}
