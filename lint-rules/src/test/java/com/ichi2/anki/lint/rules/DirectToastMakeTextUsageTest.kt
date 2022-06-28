/*
 * Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>
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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.*
import com.android.tools.lint.checks.infrastructure.TestLintTask.*
import org.intellij.lang.annotations.Language
import org.junit.Assert.*
import org.junit.Test

class DirectToastMakeTextUsageTest {
    @Language("JAVA")
    private val stubToast = """                                      
package android.widget;                                        
public class Toast {                                           
                                                               
    public static Toast makeText(Context context,              
                                String text,                   
                                int duration) {                
         // Stub                                               
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileToBeTested = """                             
package com.ichi2.anki.lint.rules;                             
                                                               
import android.widget.Toast;                                   
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        Toast.makeText();                                      
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithUIUtils = """                            
package com.ichi2.anki.lint.rules;                             
                                                               
import android.widget.Toast;                                   
                                                               
public class UIUtils {                                         
                                                               
    public static void main(String[] args) {                   
        Toast.makeText();                                      
    }                                                          
}                                                              
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubToast), create(javaFileToBeTested))
            .issues(DirectToastMakeTextUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(DirectToastMakeTextUsage.ID))
                assertTrue(output.contains(DirectToastMakeTextUsage.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageForUIUtils() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubToast), create(javaFileWithUIUtils))
            .issues(DirectToastMakeTextUsage.ISSUE)
            .run()
            .expectClean()
    }
}
