// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.AbstractCursor
import android.database.Cursor

/**
 * A [Cursor] which builds a row only when it is read, keeping at most one row in memory. Use it
 * over `MatrixCursor` when rows are expensive to build, such as rendering card HTML.
 *
 * @param columns the column names, in the order [rowProvider] returns their values
 * @param rowCount the number of rows, known before any row is built
 * @param rowProvider builds one row of `MatrixCursor`-typed values. May run on a binder thread
 * after `query()` returns, and more than once per position, so it must not outlive the query.
 */
class LazyRowCursor(
    private val columns: Array<String>,
    private val rowCount: Int,
    private val rowProvider: (position: Int) -> Array<Any?>,
) : AbstractCursor() {
    private var cachedPosition = -1
    private var cachedRow: Array<Any?>? = null

    override fun getCount(): Int = rowCount

    override fun getColumnNames(): Array<String> = columns

    private fun valueAt(column: Int): Any? {
        checkPosition()
        val row =
            cachedRow.takeIf { cachedPosition == position } ?: rowProvider(position).also {
                cachedRow = it
                cachedPosition = position
            }
        return row[column]
    }

    override fun getString(column: Int): String? = valueAt(column)?.toString()

    override fun getShort(column: Int): Short = (valueAt(column) as? Number)?.toShort() ?: 0

    override fun getInt(column: Int): Int = (valueAt(column) as? Number)?.toInt() ?: 0

    override fun getLong(column: Int): Long = (valueAt(column) as? Number)?.toLong() ?: 0

    override fun getFloat(column: Int): Float = (valueAt(column) as? Number)?.toFloat() ?: 0f

    override fun getDouble(column: Int): Double = (valueAt(column) as? Number)?.toDouble() ?: 0.0

    override fun getBlob(column: Int): ByteArray? = valueAt(column) as? ByteArray

    override fun isNull(column: Int): Boolean = valueAt(column) == null

    override fun getType(column: Int): Int =
        when (valueAt(column)) {
            null -> Cursor.FIELD_TYPE_NULL
            is ByteArray -> Cursor.FIELD_TYPE_BLOB
            is Float, is Double -> Cursor.FIELD_TYPE_FLOAT
            is Number -> Cursor.FIELD_TYPE_INTEGER
            else -> Cursor.FIELD_TYPE_STRING
        }

    override fun close() {
        cachedRow = null
        cachedPosition = -1
        super.close()
    }
}
