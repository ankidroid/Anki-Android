/****************************************************************************************
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

package com.ichi2.veecheck;

import android.content.Intent;
import android.content.IntentFilter;

import com.ichi2.anki.R;
import com.tomgibara.android.veecheck.VeecheckNotifier;
import com.tomgibara.android.veecheck.VeecheckService;
import com.tomgibara.android.veecheck.VeecheckState;
import com.tomgibara.android.veecheck.util.DefaultNotifier;
import com.tomgibara.android.veecheck.util.PrefState;

public class CheckService extends VeecheckService {

	private static final String TAG = "Ankidroid";
	public static final int NOTIFICATION_ID = 1;

	@Override
	protected VeecheckNotifier createNotifier() {
		IntentFilter[] filters = new IntentFilter[1];

		IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
		filter.addDataScheme("http");
		filters[0] = filter;

		return new DefaultNotifier(this, NOTIFICATION_ID, filters,
					new Intent(this, Notification.class),
					R.drawable.anki,
					R.string.notify_ticker,
					R.string.notify_title,
					R.string.notify_message);
	}

	@Override
	protected VeecheckState createState() {
		return new PrefState(this);
	}

}
