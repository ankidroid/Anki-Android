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
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.KotlinCleanup
import java.io.File

abstract class Importer(col: Collection, protected var file: String) {
    protected open var needMapper = false
    protected open var needDelimiter = false
    var fileName: String
        protected set
    var log: MutableList<String>
        protected set
    var cardCount: Int
        protected set
    protected val mCol: Collection

    @KotlinCleanup("rename")
    protected var _total: Int
    private var mTs: Long = 0
    protected lateinit var dst: Collection
    protected lateinit var src: Collection
    protected val context: Context
    protected var progress: TaskManager.ProgressCallback<String>? = null

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
        mTs = time.maxID(dst.db)
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
        progress = progressCallback
    }

    protected val res: Resources
        get() = context.resources

    init {
        @KotlinCleanup("combined declaration and initialization")
        fileName = File(file).name
        log = ArrayList()
        mCol = col
        _total = 0
        cardCount = 0
        context = col.context
    }
}
