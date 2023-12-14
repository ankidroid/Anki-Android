/***************************************************************************************
 * Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.testutils

import androidx.annotation.CallSuper
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Storage
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assume
import org.junit.Before
import timber.log.Timber

open class JvmTest : TestClass {
    private fun maybeSetupBackend() {
        RustBackendLoader.ensureSetup()
    }

    override val col: Collection
        get() {
            if (col_ == null) {
                col_ = CollectionManager.getColUnsafe()
            }
            return col_!!
        }

    private var col_: Collection? = null

    @Before
    @CallSuper
    open fun setUp() {
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))

        ChangeManager.clearSubscribers()

        maybeSetupBackend()

        Timber.plant(AnkiDroidApp.RobolectricDebugTree())

        Storage.setUseInMemory(true)
    }

    @After
    @CallSuper
    open fun tearDown() {
        try {
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            col_?.close()
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            TimeManager.reset()
        }
        col_ = null
        Dispatchers.resetMain()
        runBlocking { CollectionManager.discardBackend() }
        Timber.uprootAll()
    }

    fun <T> assumeThat(actual: T, matcher: Matcher<T>?) {
        Assume.assumeThat(actual, matcher)
    }
}
