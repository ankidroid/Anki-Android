package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.*
import com.android.tools.lint.checks.infrastructure.TestLintTask.*
import org.intellij.lang.annotations.Language
import org.junit.Assert.*
import org.junit.Test

class DirectSystemCurrentTimeMillisUsageTest {
    @Language("JAVA")
    private val stubSystem = """                                     
package java.lang;                                             
                                                               
public class System {                                          
                                                               
    public static long currentTimeMillis() {                   
         return 1L;                                            
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileToBeTested = """                             
package com.ichi2.anki.lint.rules;                             
                                                               
import java.lang.System;                                       
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        long time = System.currentTimeMillis();                
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithSystemTime = """                         
package com.ichi2.anki.lint.rules;                             
                                                               
import java.lang.System;                                       
                                                               
public class SystemTime {                                      
                                                               
    public static void main(String[] args) {                   
        long time = System.currentTimeMillis();                
    }                                                          
}                                                              
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubSystem), create(javaFileToBeTested))
            .issues(DirectSystemCurrentTimeMillisUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(DirectSystemCurrentTimeMillisUsage.ID))
                assertTrue(output.contains(DirectSystemCurrentTimeMillisUsage.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageForSystemTime() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubSystem), create(javaFileWithSystemTime))
            .issues(DirectSystemCurrentTimeMillisUsage.ISSUE)
            .run()
            .expectClean()
    }
}
