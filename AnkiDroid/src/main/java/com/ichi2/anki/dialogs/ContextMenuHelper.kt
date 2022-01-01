/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.dialogs;

import java.util.ArrayList;
import java.util.HashMap;

public class ContextMenuHelper {
    public static String[] getValuesFromKeys(HashMap<Integer, String> map, int[] keys) {
        String[] values = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = map.get(keys[i]);
        }
        return values;
    }

    public static int[] integerListToArray(ArrayList<Integer> itemIds) {
        int[] intItemIds = new int[itemIds.size()];
        for (int i = 0; i < itemIds.size(); i++) {
            intItemIds[i] = itemIds.get(i);
        }
        return intItemIds;
    }
}
