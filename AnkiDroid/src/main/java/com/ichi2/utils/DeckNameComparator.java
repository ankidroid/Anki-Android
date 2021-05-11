/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

import com.ichi2.libanki.Decks;

import java.util.Comparator;

public class DeckNameComparator implements Comparator<String> {
    public static final DeckNameComparator INSTANCE = new DeckNameComparator();

    @Override
    public int compare(String lhs, String rhs) {
        String[] o1 = Decks.path(lhs);
        String[] o2 = Decks.path(rhs);
        for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
            int result = o1[i].compareToIgnoreCase(o2[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(o1.length, o2.length);
    }
}
