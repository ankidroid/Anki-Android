/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker.CheckDatabaseListener
import com.ichi2.libanki.Collection.CheckDatabaseResult
import org.hamcrest.MatcherAssert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class DeckPickerCheckDatabaseListenerTest : RobolectricTest() {
    private lateinit var mImpl: DeckPickerTestImpl
    override fun setUp() {
        super.setUp()
        // .visible() crashes: Layout state should be one of 100 but it is 10
        val controller = Robolectric.buildActivity(DeckPickerTestImpl::class.java, Intent())
            .create().start().resume()
        saveControllerForCleanup(controller)
        mImpl = controller.get().apply {
            resetVariables()
        }
    }

    @Test
    fun failedResultWithNoDataWillDisplayFailedDialog() {
        val result = failedResultNoData()

        execute(result)

        assertThat("Load Failed dialog should be shown if no data is supplied", mImpl.didDisplayDialogLoadFailed)
    }

    @Test
    fun failedResultWithEmptyDataWillDisplayFailedDialog() {
        val validData = validData()
        val result = failedResultWithData(validData)

        execute(result)

        assertThat("Load Failed dialog should be shown if empty data is supplied", mImpl.didDisplayDialogLoadFailed)
    }

    @Test
    fun validResultWithValidDataWillDisplayMessageBox() {
        val validData = validData()
        val result = validResultWithData(validData)

        execute(result)

        assertThat("Load Failed dialog should not be shown if invalid data is supplied", !mImpl.didDisplayDialogLoadFailed)
        assertThat("Dialog should be displayed", mImpl.didDisplayMessage)
    }

    @Test
    fun validResultWithFailedDatabaseWillShowFailedDialog() {
        val failedDb = failedDatabase()
        val result = validResultWithData(failedDb)

        execute(result)

        assertThat("Load Failed dialog should be shown if failed data is supplied", mImpl.didDisplayDialogLoadFailed)
        assertThat("Locked Database dialog should be shown if Db was locked", !mImpl.didDisplayLockedDialog)
        assertThat("Dialog should not be displayed", !mImpl.didDisplayMessage)
    }

    @Test
    fun validResultWithLockedDatabaseWillShowLockedDialog() {
        val lockedDb = lockedDatabase()
        val result = validResultWithData(lockedDb)

        execute(result)

        assertThat("Load Failed dialog should not be shown if invalid data is supplied", !mImpl.didDisplayDialogLoadFailed)
        assertThat("Locked Database dialog should be shown if Db was locked", mImpl.didDisplayLockedDialog)
        assertThat("Dialog should not be displayed", !mImpl.didDisplayMessage)
    }

    private fun lockedDatabase(): CheckDatabaseResult {
        return CheckDatabaseResult(1).markAsLocked()
    }

    private fun failedDatabase(): CheckDatabaseResult {
        return CheckDatabaseResult(1).markAsFailed()
    }

    private fun validData(): CheckDatabaseResult {
        return CheckDatabaseResult(1)
    }

    private fun failedResultWithData(obj: CheckDatabaseResult): Pair<Boolean, CheckDatabaseResult?> {
        return Pair(false, obj)
    }

    private fun validResultWithData(obj: CheckDatabaseResult): Pair<Boolean, CheckDatabaseResult?> {
        return Pair(true, obj)
    }

    private fun failedResultNoData(): Pair<Boolean, CheckDatabaseResult?> {
        return Pair(false, null)
    }

    private fun execute(result: Pair<Boolean, CheckDatabaseResult?>) {
        val listener = getInstance(mImpl)
        listener.onPostExecute(result)
    }

    private fun getInstance(test: DeckPickerTestImpl?): CheckDatabaseListener {
        return test!!.CheckDatabaseListener()
    }

    /**COULD_BE_BETTER: Listener is too coupled to this  */
    private class DeckPickerTestImpl : DeckPicker() {
        var didDisplayDialogLoadFailed = false
            private set

        var didDisplayMessage = false
            private set

        var didDisplayLockedDialog = false
            private set

        override fun handleDbError() {
            didDisplayDialogLoadFailed = true
            super.handleDbError()
        }

        override fun handleDbLocked() {
            didDisplayLockedDialog = true
            super.handleDbLocked()
        }

        fun resetVariables() {
            didDisplayMessage = false
            didDisplayDialogLoadFailed = false
            didDisplayLockedDialog = false
        }

        override fun showSimpleMessageDialog(message: String?, title: String, reload: Boolean) {
            didDisplayMessage = true
            super.showSimpleMessageDialog(message = message, title = title, reload = reload)
        }
    }
}
