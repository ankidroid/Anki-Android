/****************************************************************************************
 * Copyright (c) 2009 Bitonator                                                         *
 * https://stackoverflow.com/questions/6176441/how-to-use-assert-in-android/47267127#47267127 * 
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

public class Assert {
    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
