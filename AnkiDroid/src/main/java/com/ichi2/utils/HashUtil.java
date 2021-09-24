/***************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
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

import java.util.HashMap;
import java.util.HashSet;

public class HashUtil {
    /**
     * @param size Number of elements expected in the hash structure
     * @return Initial capacity for the hash structure. Copied from HashMap code
     */
    private static int capacity(int size) {
        return Math.max((int) (size/.75f) + 1, 16);
    }

    public static <T> HashSet<T> HashSetInit(int size) {
        return new HashSet<T>(capacity(size));
    }
    public static <T, U> HashMap<T, U> HashMapInit(int size) {
        return new HashMap<T, U>(capacity(size));
    }
}
