/***************************************************************************************
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki.importer

import android.content.Context
import android.content.res.Resources
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import java.util.ArrayList
import kotlin.Throws

@Suppress("PMD.MethodNamingConventions")
abstract class Importer(col: Collection, var file: String) {
    protected open var mNeedMapper = false
    protected open var mNeedDelimiter = false
    protected open lateinit var log: MutableList<String>
        protected set

    @JvmField
    protected val mCol: Collection
    protected var mTotal: Int
    private var mTs: Long = 0

    @JvmField
    protected var mDst: Collection? = null

    @JvmField
    protected var mSrc: Collection? = null

    @JvmField
    protected val mContext: Context

    @JvmField
    protected var mProgress: TaskManager.ProgressCallback<String>? = null

    @Throws(ImportExportException::class)
    abstract fun run()

    /**
     * Timestamps
     * ***********************************************************
     * It's too inefficient to check for existing ids on every object,
     * and a previous import may have created timestamps in the future, so we
     * need to make sure our starting point is safe.
     */
    protected fun _prepareTS() {
        mTs = mCol.time.maxID(mDst!!.db)
    }

    protected fun ts(): Long {
        mTs++
        return mTs
    }

    /**
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    fun setProgressCallback(progressCallback: TaskManager.ProgressCallback<String>?) {
        mProgress = progressCallback
    }

    protected val res: Resources
        get() = mContext.resources

    init {
        log = ArrayList()
        mCol = col
        mTotal = 0
        mContext = col.context
    }
}
