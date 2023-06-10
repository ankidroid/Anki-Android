/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.tests.libanki

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.async.Connection
import com.ichi2.libanki.sync.HostNum
import com.ichi2.utils.NetworkUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpTest {

    @get:Rule
    var runtimeStoragePermissionRule = grantPermissions(
        storagePermission,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    // #7108: AsyncTask
    @Suppress("DEPRECATION")
    @Test
    fun testLogin() {
        val username = "AnkiDroidInstrumentedTestUser"
        val password = "AnkiDroidInstrumentedTestInvalidPass"
        val invalidPayload = Connection.Payload(arrayOf(username, password, HostNum(null)))
        val testListener = TestTaskListener(invalidPayload)

        // We have to carefully run things on the main thread here or the threading protections in BaseAsyncTask throw
        // The first one is just to run the static initializer, really
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                Class.forName("com.ichi2.async.Connection")
            } catch (e: Exception) {
                Assert.fail("Unable to load Connection class: " + e.message)
            }
        }

        // If we are not online this test is not nearly as interesting
        // TODO simulate offline programmatically - currently exercised by manually toggling an emulator offline pre-test
        if (!NetworkUtils.isOnline) {
            Connection.login(testListener, invalidPayload)
            Assert.assertFalse(
                "Successful login despite being offline",
                testListener.getPayload()!!.success
            )
            Assert.assertTrue(
                "onDisconnected not called despite being offline",
                testListener.disconnectedCalled
            )
            return
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val conn = Connection.login(testListener, invalidPayload)
            try {
                // This forces us to synchronously wait for the AsyncTask to do it's work
                conn!!.get()
            } catch (e: Exception) {
                Assert.fail("Caught exception while trying to login: " + e.message)
            }
        }
        Assert.assertFalse(
            "Successful login despite invalid credentials",
            testListener.getPayload()!!.success
        )
    }

    class TestTaskListener(payload: Connection.Payload) :
        Connection.TaskListener {

        private var payload: Connection.Payload? = null
        var disconnectedCalled = false
        private fun setPayload(payload: Connection.Payload) {
            this.payload = payload
        }

        override fun onPreExecute() {
            // do nothing
        }

        override fun onProgressUpdate(vararg values: Any?) {
            // do nothing
        }

        fun getPayload(): Connection.Payload? {
            return payload
        }

        override fun onPostExecute(data: Connection.Payload) {
            // do nothing
        }

        override fun onDisconnected() {
            disconnectedCalled = true
        }

        init {
            setPayload(payload)
        }
    }
}
