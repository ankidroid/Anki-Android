/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.model.AnkiDroidGitHub.Companion.getIssueLink
import com.ichi2.anki.model.AnkiDroidGitHub.GitHubIssue.Companion.gitHubBug
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.lessThan
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiDroidGitHubTest {

    @Test
    fun testIssue() {
        assertThat(getIssueLink(101).url, `is`("https://github.com/ankidroid/Anki-Android/issues/101"))
    }

    @Test
    fun testBug() {
        val issue = gitHubBug(title = "[Bug] Testing Submission via URL", body = "Requested by David")
        val expected = "https://github.com/ankidroid/Anki-Android/issues/new" +
            "?template=issue_template.md" +
            "&title=%5BBug%5D%20Testing%20Submission%20via%20URL" +
            "&body=Requested%20by%20David"
        assertThat(getIssueLink(issue).url, `is`(expected))
    }

    @Test
    fun testBugNewline() {
        // a newline [\n 0x0A] or [\r 0x0D] or [\r\n 0x0D0x0A] works on GitHub as a single newline
        val issue = gitHubBug(
            title = "[Bug] Testing Submission via URL",
            body = "Requested by David\n\nException"
        )

        val expected = "https://github.com/ankidroid/Anki-Android/issues/new" +
            "?template=issue_template.md" +
            "&title=%5BBug%5D%20Testing%20Submission%20via%20URL" +
            "&body=Requested%20by%20David%0A%0AException"

        assertThat(getIssueLink(issue).url, `is`(expected))
    }

    @Test
    fun testLongException() {
        // GitHub has a URl limit of 8192 characters. Ensure we don't go over this.

        val realEx = "net.ankiweb.rsdroid.RustBackendFailedException: java.lang.UnsatisfiedLinkError: d" +
            "lopen failed: cannot find \"\" from verneed[0] in DT_NEEDED list for \"/data/app/~~U6kDiFxMXVRvvREzyUeQfg==/com.ichi2.anki.c-tj2D5f8psdlhEFmNNbJgOw==/lib/arm64/librsdroid.so\"\n" +
            "at net.ankiweb.rsdroid.NativeMethods.ensureSetup(NativeMethods.java:9)\n" +
            "at net.ankiweb.rsdroid.BackendFactory.createInstance(BackendFactory.java:1)\n" +
            "at com.ichi2.libanki.backend.DroidBackendFactory.getInstance(DroidBackendFactory.java:2)\n" +
            "at com.ichi2.libanki.Storage.Collection(Storage.java:5)\n" +
            "at com.ichi2.anki.CollectionHelper.getCol(CollectionHelper.java:8)\n" +
            "at com.ichi2.anki.CollectionHelper.getCol(CollectionHelper.java:3)\n" +
            "at com.ichi2.anki.services.BootService.getColSafe(BootService.java:1)\n" +
            "at com.ichi2.anki.services.BootService.onReceive(BootService.java:5)\n" +
            "at com.ichi2.anki.AnkiDroidApp.onCreate(AnkiDroidApp.java:37)\n" +
            "at android.app.Instrumentation.callApplicationOnCreate(Instrumentation.java:1192)\n" +
            "at android.app.ActivityThread.handleMakeApplication(ActivityThread.java:7507)\n" +
            "at android.app.ActivityThread.handleBindApplication(ActivityThread.java:7446)\n" +
            "at android.app.ActivityThread.access\$1500(ActivityThread.java:301)\n" +
            "at android.app.ActivityThread\$H.handleMessage(ActivityThread.java:2148)\n" +
            "at android.os.Handler.dispatchMessage(Handler.java:106)\n" +
            "at android.os.Looper.loop(Looper.java:246)\n" +
            "at android.app.ActivityThread.main(ActivityThread.java:8512)\n" +
            "at java.lang.reflect.Method.invoke(Native Method)\n" +
            "at com.android.internal.os.RuntimeInit\$MethodAndArgsCaller.run(RuntimeInit.java:602)\n" +
            "at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1130)\n" +
            "Caused by: java.lang.UnsatisfiedLinkError: dlopen failed: cannot find \"\" from verneed[0] in DT_NEEDED list for \"/data/app/~~U6kDiFxMXVRvvREzyUeQfg==/com.ichi2.anki.c-tj2D5f8psdlhEFmNNbJgOw==/lib/arm64/librsdroid.so\"\n" +
            "at java.lang.Runtime.loadLibrary0(Runtime.java:1087)\n" +
            "at java.lang.Runtime.loadLibrary0(Runtime.java:1008)\n" +
            "at java.lang.System.loadLibrary(System.java:1664)\n" +
            "at net.ankiweb.rsdroid.NativeMethods.ensureSetup(NativeMethods.java:5)\n" +
            "... 19 more\n" +
            "#################################################################"

        val issue = gitHubBug(title = "[Bug] Testing Submission via URL", body = "```\n$realEx$realEx$realEx$realEx\n```")

        val url = getIssueLink(issue).url
        assertThat(url.length, lessThan(AnkiDroidGitHub.GITHUB_URL_MAX_LENGTH))
        assertThat("'\\n`' encoded is %0A%60", url, endsWith("%0A%60%60%60"))
    }
}
