package com.ichi2.testutils;

import android.content.ContentResolver;
import android.database.Cursor;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MockContentResolver {

    public static ContentResolver returningCursor(Cursor cursor) {
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        when(resolver.query(any(), any(), any(), any(), any())).thenReturn(cursor);
        return resolver;
    }

    public static ContentResolver returningEmptyCursor() {
        return returningCursor(MockCursor.getEmpty());
    }


}
