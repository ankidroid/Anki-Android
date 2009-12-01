/****************************************************************************************
* Copyright (c) 2009 																   *
* Edu Zamora <email@email.com>                                            			   *
*                                                                                      *
* This program is free software; you can redistribute it and/or modify it under        *
* the terms of the GNU General Public License as published by the Free Software        *
* Foundation; either version 3 of the License, or (at your option) any later           *
* version.                                                                             *
*                                                                                      *
* This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
* PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
*                                                                                      *
* You should have received a copy of the GNU General Public License along with         *
* this program.  If not, see <http://www.gnu.org/licenses/>.                           *
****************************************************************************************/

package com.ichi2.anki;

import com.tomgibara.android.veecheck.Veecheck;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class AnkidroidApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		
		SharedPreferences prefs = PrefSettings.getSharedPrefs(this);
		//assign some default settings if necessary
		if (prefs.getString(PrefSettings.KEY_CHECK_URI, null) == null) {
			Editor editor = prefs.edit();
			//Test Update Notifications
			//some ridiculously fast polling, just to demonstrate it working...
			/*editor.putBoolean(PrefSettings.KEY_ENABLED, true);
			editor.putLong(PrefSettings.KEY_PERIOD, 30 * 1000L);
			editor.putLong(PrefSettings.KEY_CHECK_INTERVAL, 60 * 1000L);
			editor.putString(PrefSettings.KEY_CHECK_URI, "http://ankidroid.googlecode.com/files/test_notifications.xml");*/
			editor.putString(PrefSettings.KEY_CHECK_URI, "http://ankidroid.googlecode.com/files/last_release.xml");
			editor.commit();
		}

		//reschedule the checks - we need to do this if the settings have changed (as above)
		//it may also necessary in the case where an application has been updated
		//here for simplicity, we do it every time the application is launched
		Intent intent = new Intent(Veecheck.getRescheduleAction(this));
		sendBroadcast(intent);
	}
}
