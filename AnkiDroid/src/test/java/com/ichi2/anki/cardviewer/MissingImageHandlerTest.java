/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>
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

package com.ichi2.anki.cardviewer;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

// PERF:
// Theoretically should be able to get away with not using this, but it requires WebResourceRequest (easy to mock)
// and URLUtil.guessFileName (static - likely harder)
@RunWith(AndroidJUnit4.class)
public class MissingImageHandlerTest {

    private MissingImageHandler mSut;
    private int mTimesCalled = 0;
    private List<String> mFileNames;

    @Before
    public void before() {
        mFileNames = new ArrayList<>();
        mSut = new MissingImageHandler();
    }


    @NonNull
    private Consumer<String> defaultHandler() {
        return (f) -> {
            mTimesCalled++;
            mFileNames.add(f);
        };
    }


    @Test
    public void firstTimeOnNewCardSends() {
        processFailure(getValidRequest("example.jpg"));
        assertThat(mTimesCalled, is(1));
        assertThat(mFileNames, contains("example.jpg"));
    }

    @Test
    public void twoCallsOnSameSideCallsOnce() {
        processFailure(getValidRequest("example.jpg"));
        processFailure(getValidRequest("example2.jpg"));
        assertThat(mTimesCalled, is(1));
        assertThat(mFileNames, contains("example.jpg"));
    }

    @Test
    public void callAfterFlipIsShown() {
        processFailure(getValidRequest("example.jpg"));
        mSut.onCardSideChange();
        processFailure(getValidRequest("example2.jpg"));
        assertThat(mTimesCalled, is(2));
        assertThat(mFileNames, contains("example.jpg", "example2.jpg"));
    }

    @Test
    public void thirdCallIsIgnored() {
        processFailure(getValidRequest("example.jpg"));
        mSut.onCardSideChange();
        processFailure(getValidRequest("example2.jpg"));
        mSut.onCardSideChange();
        processFailure(getValidRequest("example3.jpg"));
        assertThat(mTimesCalled, is(2));
        assertThat(mFileNames, contains("example.jpg", "example2.jpg"));
    }

    @Test
    public void invalidRequestIsIgnored() {
        WebResourceRequest invalidRequest = getInvalidRequest("example.jpg");
        processFailure(invalidRequest);
        assertThat(mTimesCalled, is(0));
    }


    private void processFailure(WebResourceRequest invalidRequest) {
        processFailure(invalidRequest, defaultHandler());
    }

    private void processFailure(WebResourceRequest invalidRequest, Consumer<String> consumer) {
        mSut.processFailure(invalidRequest, consumer);
    }

    private void processMissingSound(File file, @NonNull Consumer<String> onFailure) {
        mSut.processMissingSound(file, onFailure);
    }

    private void processInefficientImage(Runnable onFailure) {
        mSut.processInefficientImage(onFailure);
    }


    @Test
    public void uiFailureDoesNotCrash() {
        processFailure(getValidRequest("example.jpg"), (f) -> { throw new RuntimeException("expected"); });
        assertThat("Irrelevant assert to stop lint warnings", mTimesCalled, is(0));
    }


    @Test
    public void testMissingSound_NullFile() {
        processMissingSound(null, defaultHandler());
        assertThat(mTimesCalled, is(0));
    }

    @Test
    public void testThirdSoundIsIgnored() {
        //Tests that the third call to processMissingSound is ignored
        Consumer<String> handler = defaultHandler();
        processMissingSound(new File("example.wav"), handler);
        mSut.onCardSideChange();
        processMissingSound(new File("example2.wav"), handler);
        mSut.onCardSideChange();
        processMissingSound(new File("example3.wav"), handler);

        assertThat(mTimesCalled, is(2));
        assertThat(mFileNames, contains("example.wav", "example2.wav"));
    }

    @Test
    public void testMissingSound_ExceptionCaught() {
        assertDoesNotThrow(() -> processMissingSound(new File("example.wav"), (f) -> { throw new RuntimeException("expected");}));
    }

    @Test
    public void testInefficientImage() {
        //Tests that the runnable passed to processInefficientImage only runs once
        class runTest implements Runnable {
            private int mNTimesRun = 0;
            @Override
            public void run() {
                mNTimesRun++;
            }
            public int getNTimesRun() { return mNTimesRun; }
        }
        runTest runnableTest = new runTest();
        processInefficientImage(runnableTest);
        processInefficientImage(runnableTest);
        assertThat(runnableTest.getNTimesRun(), is(1));
    }


    private WebResourceRequest getValidRequest(String fileName) {
        // actual URL on Android 9
        String url =  String.format("file:///storage/emulated/0/AnkiDroid/collection.media/%s", fileName);
        return getWebResourceRequest(url);
    }

    private WebResourceRequest getInvalidRequest(@SuppressWarnings("SameParameterValue") String fileName) {
        // no collection.media in the URL
        String url =  String.format("file:///storage/emulated/0/AnkiDroid/%s", fileName);
        return getWebResourceRequest(url);
    }

    @NonNull
    private WebResourceRequest getWebResourceRequest(String url) {
        return new WebResourceRequest() {
            @Override
            public Uri getUrl() {
                return Uri.parse(url);
            }


            @Override
            public boolean isForMainFrame() {
                return false;
            }


            @Override
            public boolean isRedirect() {
                return false;
            }


            @Override
            public boolean hasGesture() {
                return false;
            }


            @Override
            public String getMethod() {
                return null;
            }


            @Override
            public Map<String, String> getRequestHeaders() {
                return null;
            }
        };
    }
}
