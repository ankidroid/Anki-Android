/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.google.common.annotations.Beta
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

@Suppress("UnstableApiUsage")
@Beta
class DirectDateInstantiationTest {
    @Language("JAVA")
    private val stubDate = """                         
package java.util;                               
                                                 
public class Date {                              
                                                 
    public Date() {                              
                                                 
    }                                            
    public Date(long time) {                     
                                                 
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileToBeTested = """               
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public class TestJavaClass {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date();                     
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileWithTime = """                 
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public abstract class Time {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date();                     
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileUsingDateWithLong = """        
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public class TestJavaClass {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date(1L);                   
    }                                            
}                                                
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        TestLintTask.lint().allowMissingSdk().allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileToBeTested))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectDateInstantiation.ID))
                Assert.assertTrue(output.contains(DirectDateInstantiation.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageInTimeClass() {
        TestLintTask.lint().allowMissingSdk().allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileWithTime))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun allowsUsageWithLongValue() {
        TestLintTask.lint().allowMissingSdk().allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileUsingDateWithLong))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectClean()
    }
}
