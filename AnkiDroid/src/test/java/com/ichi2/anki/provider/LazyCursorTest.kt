// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.CursorWindow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class LazyCursorTest {
    @Test
    fun `large results only load visited rows`() {
        val loaded = mutableListOf<Int>()
        LazyCursor(arrayOf("_id"), 1_000_000) { position, row ->
            loaded.add(position)
            row.addRow(arrayOf(position.toLong()))
        }.use { cursor ->
            assertThat(cursor.count, equalTo(1_000_000))
            assertThat(loaded, empty())
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
            assertThat(cursor.moveToLast(), equalTo(true))
            assertThat(cursor.getLong(0), equalTo(999_999L))
            assertThat(loaded, contains(999_999))
            assertThat(cursor.moveToFirst(), equalTo(true))
            assertThat(cursor.getLong(0), equalTo(0L))
            assertThat(cursor.moveToFirst(), equalTo(true))
            assertThat(loaded, contains(999_999, 0))
            assertThat(cursor.moveToPrevious(), equalTo(false))
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
            assertThat(cursor.moveToLast(), equalTo(true))
            assertThat(loaded, contains(999_999, 0, 999_999))
            assertThat(cursor.moveToNext(), equalTo(false))
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
        }
    }

    @Test
    fun `empty results never load a row`() {
        LazyCursor(arrayOf("_id"), 0) { _, _ -> error("No rows to load") }.use { cursor ->
            assertThat(cursor.count, equalTo(0))
            assertThat(cursor.moveToFirst(), equalTo(false))
        }
    }

    @Test
    fun `moving releases the previous row`() {
        val rows = mutableListOf<Cursor>()
        LazyCursor(arrayOf("_id"), 2) { position, row ->
            rows.add(row)
            row.addRow(arrayOf(position))
        }.use { cursor ->
            cursor.moveToFirst()
            cursor.moveToNext()
            assertThat(rows[0].isClosed, equalTo(true))
            assertThat(rows[1].isClosed, equalTo(false))
            cursor.moveToPrevious()
            assertThat(rows[1].isClosed, equalTo(true))
            assertThat(cursor.getInt(0), equalTo(0))
        }
        assertThat(rows.all { it.isClosed }, equalTo(true))
    }

    @Test
    fun `rows can be copied into a cursor window`() {
        LazyCursor(arrayOf("_id", "text", "nullable", "real"), 3) { position, row ->
            row.addRow(arrayOf<Any?>(position.toLong(), "row $position", null, 1.5))
        }.use { cursor ->
            CursorWindow("test").use { window ->
                cursor.fillWindow(1, window)
                assertThat(window.numRows, equalTo(2))
                assertThat(window.getLong(1, 0), equalTo(1L))
                assertThat(window.getString(2, 1), equalTo("row 2"))
                assertThat(window.getType(1, 2), equalTo(Cursor.FIELD_TYPE_NULL))
                assertThat(window.getDouble(1, 3), equalTo(1.5))
            }
        }
    }

    @Test
    fun `closing releases the loaded row`() {
        var loadedRow: Cursor? = null
        val cursor =
            LazyCursor(arrayOf("_id"), 1) { _, row ->
                loadedRow = row
                row.addRow(arrayOf(1L))
            }
        cursor.moveToFirst()
        cursor.close()
        assertThat(cursor.isClosed, equalTo(true))
        assertThat(loadedRow!!.isClosed, equalTo(true))
        assertThrows(IllegalStateException::class.java) { cursor.getLong(0) }
    }
}
