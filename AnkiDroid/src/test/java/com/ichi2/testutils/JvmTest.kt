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

import android.annotation.SuppressLint
import androidx.annotation.CallSuper
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
import org.junit.Rule
import org.junit.rules.TestName
import org.robolectric.junit.rules.TimeoutRule
import timber.log.Timber
import timber.log.Timber.Forest.plant

open class JvmTest : TestClass {
    @get:Rule
    val timeoutRule: TimeoutRule = TimeoutRule.seconds(60)

    @get:Rule
    val testName = TestName()

    private fun maybeSetupBackend() {
        RustBackendLoader.ensureSetup()
    }

    override val col: Collection
        get() {
            if (_col == null) {
                _col = CollectionManager.getColUnsafe()
            }
            return _col!!
        }

    private var _col: Collection? = null

    @Before
    @CallSuper
    open fun setUp() {
        println("""-- executing test "${testName.methodName}"""")
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))

        plant(object : Timber.DebugTree() {
            @SuppressLint("PrintStackTraceUsage")
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // This is noisy in test environments
                if (tag == "Backend\$checkMainThreadOp") {
                    return
                }
                // use println(): Timber may not work under the Jvm
                println("$tag: $message")
                t?.printStackTrace()
            }
        })

        ChangeManager.clearSubscribers()

        maybeSetupBackend()

        Storage.setUseInMemory(true)
    }

    @After
    @CallSuper
    open fun tearDown() {
        try {
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            _col?.close()
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            TimeManager.reset()
        }
        _col = null
        Dispatchers.resetMain()
        runBlocking { CollectionManager.discardBackend() }
        Timber.uprootAll()
        println("""-- completed test "${testName.methodName}"""")
    }

    fun <T> assumeThat(actual: T, matcher: Matcher<T>?) {
        Assume.assumeThat(actual, matcher)
    }
}
