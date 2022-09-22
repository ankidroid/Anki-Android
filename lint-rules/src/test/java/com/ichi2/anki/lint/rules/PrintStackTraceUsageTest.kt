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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UnstableApiUsage") // .issues() is unstable
class PrintStackTraceUsageTest {
    @Suppress("EmptyTryBlock") // in code samples
    companion object {
        @Language("JAVA")
        private val printStackTraceUsage = """import java.io.IOException;          
public class Test {                  
    public Test() {                  
        try {                        
        } catch (IOException ex) {   
            ex.printStackTrace();    
        }                            
    }                                
}"""

        @Language("JAVA")
        private val printStackTraceWithMethodArgument =
            """import java.io.IOException;                            
import java.io.PrintWriter;                            
public class Test {                                    
    public Test() {                                    
        try {                                          
        } catch (IOException ex) {                     
            ex.printStackTrace(new PrintWriter(sw));   
        }                                              
    }                                                  
}"""
    }

    @Test
    fun showsErrorForUsageWithNoParam() {
        lint()
            .allowMissingSdk()
            .files(create(printStackTraceUsage))
            .issues(PrintStackTraceUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String -> assertTrue(output.contains(PrintStackTraceUsage.ID)) })
    }

    @Test
    fun noErrorIfParamUsage() {
        // .check() is not required for the code to execute
        // If we have a parameter, we're not writing to stdout, so it's OK
        lint()
            .allowMissingSdk()
            .files(create(printStackTraceWithMethodArgument))
            .issues(PrintStackTraceUsage.ISSUE)
            .run()
            .expectErrorCount(0)
    }
}
