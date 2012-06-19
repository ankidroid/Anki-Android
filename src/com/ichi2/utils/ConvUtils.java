/****************************************************************************************
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

package com.ichi2.utils;

import org.json.JSONArray;
import org.json.JSONException;

public class ConvUtils {

    public static Object[] jsonArray2Objects(JSONArray array) {
        Object[] o = new Object[array.length()];
        for (int i = 0; i < array.length(); i++) {
            try {
                o[i] = array.get(i);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return o;
    }
}
