/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.createTransientDirectory
import io.mockk.every
import io.mockk.mockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class ScopedStorageAndroidTest {

    @Test
    fun best_default_root_first() {
        // define two directories
        runTestWithTwoExternalDirectories { externalDirectories ->
            val templatePath = externalDirectories[0].createTransientDirectory("AD")
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("should return parent directory", best.canonicalPath, equalTo(externalDirectories[0].canonicalPath))
        }
    }

    @Test
    fun best_default_root_second() {
        runTestWithTwoExternalDirectories { externalDirectories ->
            val templatePath = externalDirectories[1].createTransientDirectory("AD")
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("should return parent directory", best.canonicalPath, equalTo(externalDirectories[1].canonicalPath))
        }
    }

    @Test
    fun best_default_root_returns_first_if_no_match() {
        runTestWithTwoExternalDirectories { externalDirectories ->
            val templatePath = createTransientDirectory("unrelated")
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("should return first path", best.canonicalPath, equalTo(externalDirectories[0].canonicalPath))
        }
    }

    @Test
    fun in_nested_paths_closest_is_returned() {
        val sharedRootDirectory = createTransientDirectory("first") // ./first
        val secondRootDirectory = sharedRootDirectory.createTransientDirectory("second") // ./first.second
        val thirdRootDirectory = secondRootDirectory.createTransientDirectory("third") // ./first.second.third
        val sharedDirectories = arrayOf(sharedRootDirectory, secondRootDirectory, thirdRootDirectory)

        val templatePath = secondRootDirectory.createTransientDirectory("template") // ./first.second.template

        runTestWithTwoExternalDirectories(sharedDirectories) {
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("should return second path as closest to the root", best.canonicalPath, equalTo(secondRootDirectory.canonicalPath))
        }
        runTestWithTwoExternalDirectories(sharedDirectories.reversedArray()) {
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("should return second path as closest to the root", best.canonicalPath, equalTo(secondRootDirectory.canonicalPath))
        }
    }

    @Test
    fun sibling_of_second_external() {
        val phoneRootDirectory = createTransientDirectory("phone")
        val sdRootDirectory = createTransientDirectory("sd")
        val phoneExternal = phoneRootDirectory.createTransientDirectory("external")
        val sdExternal = sdRootDirectory.createTransientDirectory("external")
        val sdAnkiDroid = sdRootDirectory.createTransientDirectory("ankidroid")
        runTestWithTwoExternalDirectories(arrayOf(phoneExternal, sdExternal)) {
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), sdAnkiDroid)
            assertThat("ambiguous, so should return first path", best.canonicalPath, equalTo(sdExternal.canonicalPath))
        }
    }

    @Test
    fun if_root_is_shared_return_first_path() {
        val sharedRootDirectory = createTransientDirectory("root")
        val depthOneRootDirectory = sharedRootDirectory.createTransientDirectory("first")
        val depthTwoRootDirectory = sharedRootDirectory.createTransientDirectory("second").createTransientDirectory("second")
        val depthThreeRootDirectory = sharedRootDirectory.createTransientDirectory("third").createTransientDirectory("third").createTransientDirectory("third")
        val templatePath = sharedRootDirectory.createTransientDirectory("final")

        runTestWithTwoExternalDirectories(arrayOf(depthTwoRootDirectory, depthThreeRootDirectory, depthOneRootDirectory)) {
            val best = ScopedStorageService.getBestDefaultRootDirectory(ApplicationProvider.getApplicationContext(), templatePath)
            assertThat("ambiguous, so should return first path", best.canonicalPath, equalTo(depthTwoRootDirectory.canonicalPath))
        }
    }

    /**
     * run the test [test], with the hypothesis that there are two distinct app specific external directories.
     * Those two directories are given as argument to the test.
     */
    private fun runTestWithTwoExternalDirectories(test: (Array<File>) -> Unit) {
        val twoDirs = arrayOf(createTransientDirectory(), createTransientDirectory())
        runTestWithTwoExternalDirectories(twoDirs, test)
    }

    /**
     * run the test [test], with the hypothesis that [externalDirectories] are the external directories of the system.
     */
    private fun runTestWithTwoExternalDirectories(externalDirectories: Array<File>, test: (Array<File>) -> Unit) {
        mockkObject(CollectionHelper) {
            every { CollectionHelper.getAppSpecificExternalDirectories(any()) } returns externalDirectories
            test(externalDirectories)
        }
    }
}
