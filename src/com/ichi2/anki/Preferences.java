/****************************************************************************************
* Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.tomgibara.android.veecheck.Veecheck;
import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Preferences dialog.
 */
public class Preferences extends PreferenceActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		getPreferenceManager().setSharedPreferencesName(PrefSettings.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.layout.preferences);
	}

    @Override
    protected void onPause() {
    	super.onPause();
    	// Reschedule the checking in case the user has changed anything
		sendBroadcast(new Intent(Veecheck.getRescheduleAction(this)));
    }
}
