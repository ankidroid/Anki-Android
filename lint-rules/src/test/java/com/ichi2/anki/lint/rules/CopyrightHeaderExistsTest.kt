/*
 *  Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>
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

package com.ichi2.anki.lint.rules;

import com.google.common.annotations.Beta;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("UnstableApiUsage")
@Beta
public class CopyrightHeaderExistsTest {

    @Language("JAVA")
    private final String mCopyrightHeader = "/*\n" +
            " *  This program is free software; you can redistribute it and/or modify it under\n" +
            " *  the terms of the GNU General Public License as published by the Free Software\n" +
            " *  Foundation; either version 3 of the License, or (at your option) any later\n" +
            " *  version.\n" +
            " *\n" +
            " *  This program is distributed in the hope that it will be useful, but WITHOUT ANY\n" +
            " *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A\n" +
            " *  PARTICULAR PURPOSE. See the GNU General Public License for more details.\n" +
            " *\n" +
            " *  You should have received a copy of the GNU General Public License along with\n" +
            " *  this program.  If not, see <http://www.gnu.org/licenses/>.\n" +
            " */";

    @Language("JAVA")
    private final String mNoCopyrightHeader = "\n" +
            "package com.ichi2.upgrade;\n" +
            "\n" +
            "import com.ichi2.libanki.Collection;\n" +
            "\n" +
            "import com.ichi2.utils.JSONException;\n" +
            "import com.ichi2.utils.JSONObject;\n" +
            "\n" +
            "import timber.log.Timber;\n" +
            "\n" +
            "public class Upgrade {\n" +
            "}";


    @Test
    public void fileWithCopyrightHeaderPasses() {
        lint()
                .allowMissingSdk()
                .files(create(mCopyrightHeader))
                .issues(CopyrightHeaderExists.ISSUE)
                .run()
                .expectClean();
    }

    @Test
    public void fileWithNoCopyrightHeaderFails() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors() // import failures
                .files(create(mNoCopyrightHeader))
                .issues(CopyrightHeaderExists.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(CopyrightHeaderExists.ID));
                    assertTrue(output.contains(CopyrightHeaderExists.DESCRIPTION));
                });
    }
}