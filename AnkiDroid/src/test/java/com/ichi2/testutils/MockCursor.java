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
