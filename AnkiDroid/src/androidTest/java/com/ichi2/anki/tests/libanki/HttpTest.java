package com.ichi2.anki.tests.libanki;

import android.Manifest;
import android.os.Build;

import com.ichi2.async.Connection;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.sync.HostNum;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

@RunWith(AndroidJUnit4.class)
public class HttpTest {

    @Rule
    public GrantPermissionRule mRuntimeStoragePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE);


    @Test
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public void testLogin() {

        String username = "AnkiDroidInstrumentedTestUser";
        String password = "AnkiDroidInstrumentedTestInvalidPass";
        Connection.Payload invalidPayload = new Connection.Payload(new Object[]{username, password, new HostNum(null)});
        TestTaskListener testListener = new TestTaskListener(invalidPayload);

        // We have to carefully run things on the main thread here or the threading protections in BaseAsyncTask throw
        // The first one is just to run the static initializer, really
        Runnable onlineRunnable = () -> {
            try {
                Class.forName("com.ichi2.async.Connection");
            } catch (Exception e) {
                Assert.fail("Unable to load Connection class: " + e.getMessage());
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(onlineRunnable);

        // If we are not online this test is not nearly as interesting
        // TODO simulate offline programmatically - currently exercised by manually toggling an emulator offline pre-test
        if (!Connection.isOnline()) {
            Connection.login(testListener, invalidPayload);
            Assert.assertFalse("Successful login despite being offline", testListener.getPayload().success);
            Assert.assertTrue("onDisconnected not called despite being offline", testListener.mDisconnectedCalled);
            return;
        }

        Runnable r = () -> {
            Connection conn = Connection.login(testListener, invalidPayload);
            try {
                // This forces us to synchronously wait for the AsyncTask to do it's work
                conn.get();
            } catch (Exception e) {
                Assert.fail("Caught exception while trying to login: " + e.getMessage());
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
        Assert.assertFalse("Successful login despite invalid credentials", testListener.getPayload().success);
    }


    public static class TestTaskListener implements Connection.TaskListener {

        private Connection.Payload mPayload;
        private boolean mDisconnectedCalled = false;

        private Connection.Payload getPayload() {
            return mPayload;
        }

        private void setPayload(Connection.Payload payload) {
            mPayload = payload;
        }

        private TestTaskListener(Connection.Payload payload) {
            setPayload(payload);
        }

        @Override
        public void onPreExecute() {
            // do nothing
        }


        @Override
        public void onProgressUpdate(Object... values) {
            // do nothing
        }


        @Override
        public void onPostExecute(Connection.Payload data) {
            // do nothing
        }


        @Override
        public void onDisconnected() {
            mDisconnectedCalled = true;
        }
    }
}
