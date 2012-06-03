/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki.sync;

import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.libanki.Collection;

public class LocalServer extends Syncer {

	public LocalServer(Collection col) {
		super(null, null);
	}

	public JSONObject applyChanges(JSONObject kw) {
		try {
			return new JSONObject(kw.toString());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

}
