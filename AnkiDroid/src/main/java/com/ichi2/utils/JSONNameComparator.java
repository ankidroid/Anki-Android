/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;

public class JSONNameComparator implements Comparator<JSONObject> {
    @Override
    public int compare(JSONObject lhs, JSONObject rhs) {
        String[] o1;
        String[] o2;
        try {
            o1 = lhs.getString("name").split("::");
            o2 = rhs.getString("name").split("::");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
            int result = o1[i].compareToIgnoreCase(o2[i]);
            if (result != 0) {
                return result;
            }
        }
        if (o1.length < o2.length) {
            return -1;
        } else if (o1.length > o2.length) {
            return 1;
        } else {
            return 0;
        }
    }
}
