// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.CursorWindow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

/** Tests for [LazyRowCursor] */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class LazyRowCursorTest {
    private val columns = arrayOf("_id", "name", "weight")
    private val built = mutableListOf<Int>()

    private fun cursor(rowCount: Int = 3) =
        LazyRowCursor(columns, rowCount) { position ->
            built.add(position)
            arrayOf(position.toLong(), "row $position", if (position == 1) null else position * 0.5)
        }

    @Test
    fun `no row is built until one is read`() {
        val cursor = cursor()

        assertThat("count is known without building rows", cursor.count, equalTo(3))
        assertThat("column names are known without building rows", cursor.columnNames.size, equalTo(3))
        cursor.moveToFirst()

        assertThat("moving does not build a row", built, empty())
    }

    @Test
    fun `reading a row builds only that row`() {
        val cursor = cursor()

        cursor.moveToPosition(2)
        assertThat(cursor.getString(1), equalTo("row 2"))

        assertThat("rows 0 and 1 are never built", built, contains(2))
    }

    @Test
    fun `all columns of a row share a single build`() {
        val cursor = cursor()

        cursor.moveToFirst()
        cursor.getLong(0)
        cursor.getString(1)
        cursor.getDouble(2)
        cursor.getType(2)

        assertThat("the row is built once, not once per column", built, contains(0))
    }

    @Test
    fun `re-reading a row rebuilds it rather than retaining it`() {
        val cursor = cursor()

        cursor.moveToPosition(0)
        cursor.getLong(0)
        cursor.moveToPosition(1)
        cursor.getLong(0)
        cursor.moveToPosition(0)
        assertThat("the value is unchanged", cursor.getString(1), equalTo("row 0"))

        assertThat("only one row is cached at a time", built, contains(0, 1, 0))
    }

    @Test
    fun `values are typed as MatrixCursor types them`() {
        val cursor = cursor()

        cursor.moveToFirst()
        assertThat(cursor.getLong(0), equalTo(0L))
        assertThat(cursor.getString(1), equalTo("row 0"))
        assertThat(cursor.getDouble(2), equalTo(0.0))
        assertThat(cursor.getType(0), equalTo(Cursor.FIELD_TYPE_INTEGER))
        assertThat(cursor.getType(1), equalTo(Cursor.FIELD_TYPE_STRING))
        assertThat(cursor.getType(2), equalTo(Cursor.FIELD_TYPE_FLOAT))

        cursor.moveToNext()
        assertThat("a null value is reported as null", cursor.isNull(2), equalTo(true))
        assertThat(cursor.getType(2), equalTo(Cursor.FIELD_TYPE_NULL))
        assertThat("a null value reads as zero", cursor.getDouble(2), equalTo(0.0))
        assertThat("a null value has no string", cursor.getString(2), equalTo(null))
    }

    @Test
    fun `a non-numeric value is typed and read as a string`() {
        val cursor = LazyRowCursor(arrayOf("flag"), 1) { arrayOf(true) }

        cursor.moveToFirst()
        assertThat(cursor.getType(0), equalTo(Cursor.FIELD_TYPE_STRING))
        assertThat(cursor.getString(0), equalTo("true"))
    }

    @Test
    fun `reading outside the result set throws instead of building a row`() {
        val cursor = cursor()

        assertFailsWith<CursorIndexOutOfBoundsException> { cursor.getLong(0) }
        cursor.moveToPosition(3)
        assertFailsWith<CursorIndexOutOfBoundsException> { cursor.getLong(0) }

        assertThat("no row is built for an invalid position", built, empty())
    }

    @Test
    fun `an empty result set builds nothing`() {
        val cursor = cursor(rowCount = 0)

        assertThat(cursor.count, equalTo(0))
        assertThat("there is no first row", cursor.moveToFirst(), equalTo(false))
        assertThat(built, empty())
    }

    @Test
    fun `filling a window builds each row once`() {
        val cursor = cursor()
        val window = CursorWindow("test")

        fillWindow(cursor, window)

        assertThat("every row reaches the window", window.numRows, equalTo(3))
        assertThat("each row is built exactly once", built, contains(0, 1, 2))
        assertThat(window.getString(0, 1), equalTo("row 0"))
        assertThat("the null value survives the window", window.getString(1, 2), equalTo(null))
    }

    private fun fillWindow(
        cursor: Cursor,
        window: CursorWindow,
    ) {
        window.setNumColumns(cursor.columnCount)
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            check(window.allocRow())
            for (column in 0 until cursor.columnCount) {
                val row = cursor.position
                val put =
                    when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> window.putNull(row, column)
                        Cursor.FIELD_TYPE_INTEGER -> window.putLong(cursor.getLong(column), row, column)
                        Cursor.FIELD_TYPE_FLOAT -> window.putDouble(cursor.getDouble(column), row, column)
                        Cursor.FIELD_TYPE_BLOB -> window.putBlob(cursor.getBlob(column), row, column)
                        else -> window.putString(cursor.getString(column), row, column)
                    }
                check(put)
            }
        }
        cursor.moveToPosition(-1)
    }
}
