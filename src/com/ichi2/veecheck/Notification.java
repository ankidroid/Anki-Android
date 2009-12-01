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

package com.ichi2.veecheck;

import android.os.Bundle;
import android.view.View;
import android.widget.Checkable;

import com.ichi2.anki.R;
import com.tomgibara.android.veecheck.VeecheckActivity;
import com.tomgibara.android.veecheck.VeecheckState;
import com.tomgibara.android.veecheck.util.PrefState;

public class Notification extends VeecheckActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notification);
	}
	
	@Override
	protected VeecheckState createState() {
		return new PrefState(this);
	}

	@Override
	protected View getNoButton() {
		return findViewById(R.id.no);
	}

	@Override
	protected View getYesButton() {
		return findViewById(R.id.yes);
	}
	
	@Override
	protected Checkable getStopCheckBox() {
		return (Checkable) findViewById(R.id.stop);
	}
}
