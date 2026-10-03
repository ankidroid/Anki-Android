// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.AbstractCursor
import android.database.MatrixCursor

/**
 * A cursor that loads rows on demand and retains at most one loaded row.
 *
 * Creating the cursor or reading its count does not load any rows. Moving to a different valid
 * position calls [loadRow], which must add exactly one row to the supplied [MatrixCursor].
 * The previous row is discarded, so returning to it calls [loadRow] again and may yield updated
 * values. Android can copy rows into a cursor window without this cursor retaining all the results.
 *
 * For example, defer fetching card details until the cursor moves to a result:
 * ```kotlin
 * val cursor = LazyCursor(arrayOf("_id", "reps"), cardIds.size) { position, row ->
 *     val card = col.getCardOrNull(cardIds[position])
 *     row.newRow().add(card.id).add(card.reps)
 * }
 * ```
 */
internal class LazyCursor(
    private val columns: Array<String>,
    private val rowCount: Int,
    private var loadRow: ((Int, MatrixCursor) -> Unit)?,
) : AbstractCursor() {
    private var row: MatrixCursor? = null

    override fun getCount(): Int = rowCount

    override fun getColumnNames(): Array<String> = columns

    override fun onMove(
        oldPosition: Int,
        newPosition: Int,
    ): Boolean {
        check(!isClosed)
        row?.close()
        row = null
        val next = MatrixCursor(columns, 1)
        try {
            loadRow!!(newPosition, next)
            check(next.count == 1)
            next.moveToFirst()
            row = next
        } catch (e: Exception) {
            next.close()
            throw e
        }
        return true
    }

    private fun currentRow(): MatrixCursor {
        check(!isClosed)
        checkPosition()
        return checkNotNull(row)
    }

    override fun getString(column: Int): String? = currentRow().getString(column)

    override fun getShort(column: Int): Short = currentRow().getShort(column)

    override fun getInt(column: Int): Int = currentRow().getInt(column)

    override fun getLong(column: Int): Long = currentRow().getLong(column)

    override fun getFloat(column: Int): Float = currentRow().getFloat(column)

    override fun getDouble(column: Int): Double = currentRow().getDouble(column)

    override fun getBlob(column: Int): ByteArray? = currentRow().getBlob(column)

    override fun getType(column: Int): Int = currentRow().getType(column)

    override fun isNull(column: Int): Boolean = currentRow().isNull(column)

    override fun close() {
        row?.close()
        row = null
        loadRow = null
        super.close()
    }
}
