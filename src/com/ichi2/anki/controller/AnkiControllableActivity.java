/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
package com.ichi2.anki.controller;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;

public abstract class AnkiControllableActivity extends AnkiActivity implements IAnkiControllable {
    @Override
    protected void onResume() {
    	super.onResume();
    	AnkiDroidApp.getControllerManager().setReceiverActivity(AnkiControllableActivity.this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    	AnkiDroidApp.getControllerManager().unsetReceiverActivity();
    }

	@Override
	protected void onDestroy() {
		if (canStopController()) {
			AnkiDroidApp.getControllerManager().disableController();
		}
        super.onDestroy();
	}

	public boolean canStartController() {
		return false;
	}

	public boolean canStopController() {
		return false;
	}
}
