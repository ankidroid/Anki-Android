/*
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.compat

import android.os.Build
import androidx.annotation.RequiresApi
import com.ichi2.anki.model.Directory
import com.ichi2.testutils.assertThrowsSubclass
import com.ichi2.testutils.createTransientDirectory
import org.junit.After
import org.junit.Before
import org.junit.runners.Parameterized
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.io.File
import java.io.IOException

/**
 * Allows to test with CompatV21 and V26.
 * In particular it allows to test version of the code that uses [Files] and [Path] classes.
 * And versions that must restrict themselves to [File].
 */
@RequiresApi(Build.VERSION_CODES.O) // This requirement is necessary for compilation. However, it still allows to test CompatV21
open class Test21And26(
    open val compat: Compat,
    /** Used in the "Test Results" Window */
    @Suppress("unused") private val unitTestDescription: String
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")

        fun data(): Iterable<Array<Any>> = sequence {
            yield(arrayOf(CompatV21(), "CompatV21"))
            yield(arrayOf(CompatV26(), "CompatV26"))
        }.asIterable()
    }

    val isV21: Boolean
        get() = compat is CompatV21
    val isV26: Boolean
        get() = compat is CompatV26

    lateinit var mocked: MockedStatic<CompatHelper>

    @Before
    open fun setup() {
        mocked = Mockito.mockStatic(CompatHelper::class.java)
        mocked.`when`<Compat> { CompatHelper.getCompat() }.doReturn(compat)
    }

    // Allow to cancel every static mock, appart from the setup's one.
    // Required because individual method can't be unregistered.
    fun restart() {
        tearDown()
        setup()
    }

    @After
    fun tearDown() {
        mocked.close()
    }

    /**
     * Represents structure and compat required to simulate https://github.com/ankidroid/Anki-Android/issues/10358
     * This is a bug that occurred in a smartphone, where listFiles returned `null` on an existing directory.
     */
    inner class PermissionDenied constructor(val directory: Directory, val compat: Compat) {
        /**
         * This run test, ensuring that [newDirectoryStream] throws on [directory].
         * This is useful in the case where we can't directly access the directory or compat
         */
        fun <T> runWithPermissionDenied(test: () -> T): T {
            mocked.`when`<Compat> { CompatHelper.getCompat() }.doReturn(compat)
            val result = test()
            restart()
            return result
        }

        /**
         * Allow to ensure that [test] throw an IOException.
         * We plan to use it to ensure that if we don't have permission to read the directory
         * the exception is not catched.
         */
        fun assertThrowsWhenPermissionDenied(test: () -> Unit): IOException =
            runWithPermissionDenied { assertThrowsSubclass(test) }
    }

    /**
     * Create a directory [directory]. Ensures that [directory.hasFile] returns [null],
     * which simulates to simulate https://github.com/ankidroid/Anki-Android/issues/10358.
     * Also ensure that [Files.newDirectoryStream] fails on this directory.
     */
    fun createPermissionDenied(): PermissionDenied {
        val directory = createTransientDirectory()
        val compat = CompatHelper.getCompat()
        val directoryWithPermissionDenied =
            spy(directory) {
                on { listFiles() } doReturn null
            }
        val compatWithPermissionDenied =
            if (compat is CompatV26) {
                // Closest to simulate [newDirectoryStream] throwing [AccessDeniedException]
                // since this method calls toPath.
                spy(compat) {
                    doThrow(AccessDeniedException(directory)).whenever(it).newDirectoryStream(eq(directory.toPath()))
                }
            } else {
                compat
            }
        return PermissionDenied(Directory.createInstance(directoryWithPermissionDenied)!!, compatWithPermissionDenied)
    }
}
