/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Sound.SoundSide.QUESTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class ReadTextTest extends RobolectricTest{

    @Before
    public void init() {
        // Investigate: This is fine here, but previously caused test failures.
        // E/SQLiteConnectionPool: Failed to close connection, its fate is now in the hands of the merciful GC: SQLiteConnection
        // java.lang.IllegalStateException: Illegal connection pointer 1163. Current pointers for thread Thread[SDK 29 Main Thread,5,SDK 29] []
        //	at org.robolectric.shadows.ShadowSQLiteConnection$Connections.getConnection(ShadowSQLiteConnection.java:369)
        //	at org.robolectric.shadows.ShadowSQLiteConnection$Connections.getStatement(ShadowSQLiteConnection.java:378)
        //	at org.robolectric.shadows.ShadowSQLiteConnection$Connections.finalizeStmt(ShadowSQLiteConnection.java:491)
        //	at org.robolectric.shadows.ShadowSQLiteConnection.nativeFinalizeStatement(ShadowSQLiteConnection.java:124)
        //	at android.database.sqlite.SQLiteConnection.nativeFinalizeStatement(SQLiteConnection.java)
        //	at android.database.sqlite.SQLiteConnection.finalizePreparedStatement(SQLiteConnection.java:1032)
        //	at android.database.sqlite.SQLiteConnection.access$100(SQLiteConnection.java:91)
        //	at android.database.sqlite.SQLiteConnection$PreparedStatementCache.entryRemoved(SQLiteConnection.java:1361)
        //	at android.database.sqlite.SQLiteConnection$PreparedStatementCache.entryRemoved(SQLiteConnection.java:1350)
        //	at android.util.LruCache.trimToSize(LruCache.java:222)
        //	at android.util.LruCache.evictAll(LruCache.java:310)
        //	at android.database.sqlite.SQLiteConnection.dispose(SQLiteConnection.java:248)
        //	at android.database.sqlite.SQLiteConnection.close(SQLiteConnection.java:209)
        //	at android.database.sqlite.SQLiteConnectionPool.closeConnectionAndLogExceptionsLocked(SQLiteConnectionPool.java:610)
        //	at android.database.sqlite.SQLiteConnectionPool.closeAvailableConnectionsAndLogExceptionsLocked(SQLiteConnectionPool.java:548)
        //	at android.database.sqlite.SQLiteConnectionPool.dispose(SQLiteConnectionPool.java:253)
        //	at android.database.sqlite.SQLiteConnectionPool.close(SQLiteConnectionPool.java:232)
        //	at android.database.sqlite.SQLiteDatabase.dispose(SQLiteDatabase.java:356)
        //	at android.database.sqlite.SQLiteDatabase.onAllReferencesReleased(SQLiteDatabase.java:333)
        //	at android.database.sqlite.SQLiteClosable.releaseReference(SQLiteClosable.java:76)
        //	at android.database.sqlite.SQLiteClosable.close(SQLiteClosable.java:108)
        ReadText.closeForTests();
        ReadText.initializeTts(getTargetContext(), mock(AbstractFlashcardViewer.ReadTextListener.class));
    }


    @Test
    public void SaveValue() {
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is(""));
        storeLanguage(1, "French");
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is("French"));
        storeLanguage(1, "German");
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is("German"));
        storeLanguage(2, "English");
        assertThat(MetaDB.getLanguage(getTargetContext(), 2, 1, QUESTION), is("English"));
    }

    @Test
    public void testIsTextToSpeechReleased_sameContext() {
        Context context = getTargetContext();
        initializeTextToSpeech(context);
        ReadText.releaseTts(context);
        assertThat(isTextToSpeechShutdown(), is(true));
    }

    @Test
    public void testIsTextToSpeechReleased_differentContext() {
        initializeTextToSpeech(getTargetContext());
        ReadText.releaseTts(mock(Context.class));
        assertThat(isTextToSpeechShutdown(), is(false));
    }

    private boolean isTextToSpeechShutdown() {
        return shadowOf(ReadText.getTextToSpeech()).isShutdown();
    }

    private void initializeTextToSpeech(Context context) {
        ReadText.initializeTts(context, mock(AbstractFlashcardViewer.ReadTextListener.class));
    }

    protected void storeLanguage(int i, String french) {
        MetaDB.storeLanguage(getTargetContext(), i, 1, QUESTION, french);
        advanceRobolectricLooperWithSleep();
        advanceRobolectricLooperWithSleep();
    }
}
