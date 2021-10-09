/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils;

import android.database.Cursor;
import android.database.CursorWrapper;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class MockCursor {

    public static Cursor getEmpty() {
        //Couldn't just mock cursor
        Cursor mockCursor = Mockito.mock(CursorWrapper.class);
        when(mockCursor.moveToFirst()).thenReturn(false);
        when(mockCursor.getString(anyInt())).thenThrow(new IllegalStateException());
        return mockCursor;
    }
}
