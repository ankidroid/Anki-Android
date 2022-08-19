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
import com.android.tools.lint.checks.infrastructure.TestMode
import org.intellij.lang.annotations.Language
import org.junit.Test

class PreferIsEmptyOverSizeCheckTest {

    companion object {
        @Language("JAVA")
        private val stubListWithoutIsEmpty = """                  
package com.ichi2.anki.lint.rules;                             
import java.util.*;                                            
                                                               
class SizeNoEmpty {                                            
   private List<String> ls = new ArrayList<>();                
                                                               
   public boolean add(String s) {                              
       return this.ls.add(s);                                  
   }                                                           
                                                               
   public int size() {                                         
       return this.ls.size();                                  
   }                                                           
}                                                              
"""

        @Language("JAVA")
        private val discoverableFile = """                        
package com.ichi2.anki.lint.rules;                             
import java.util.*;                                            
                                                               
class MyTest {                                                 
   private List<String> ls = new ArrayList<>();                
                                                               
   public boolean isEmpty1() {                                 
       return this.ls.size() == 0;                             
   }                                                           
                                                               
   public boolean isEmpty5() {                                 
       return this.ls.size() < 1;                              
   }                                                           
                                                               
   public boolean isNotEmpty1() {                              
       return this.ls.size() > 0;                              
   }                                                           
                                                               
   public boolean isNotEmpty2() {                              
       return this.ls.size() >= 1;                             
   }                                                           
}                                                              
"""

        @Language("JAVA")
        private val missingIsEmptyFile = """                      
package com.ichi2.anki.lint.rules;                             
import com.ichi2.anki.lint.rules.MyList;                       
                                                               
class MyTest {                                                 
   private static final int ZERO = 0;                          
   private SizeNoEmpty ls = new SizeNoEmpty<>();               
                                                               
   public boolean isEmpty1() {                                 
       return this.ls.size() == 0;                             
   }                                                           
                                                               
   public boolean isEmpty2() {                                 
       return this.ls.size() == MyTest.ZERO;                   
   }                                                           
}                                                              
"""

        @Language("JAVA")
        private val greaterThenOneFile = """                      
package com.ichi2.anki.lint.rules;                             
import java.util.*;                                            
                                                               
class MyTest {                                                 
   private List<String> ls = new ArrayList<>();                
                                                               
   public boolean isEqual2() {                                 
       return this.ls.size() == 2;                             
   }                                                           
                                                               
   public boolean isEqualOrGrater0() {                         
       return this.ls.size() >= 2;                             
   }                                                           
                                                               
   public boolean isGreater1() {                               
       return this.ls.size() > 1;                              
   }                                                           
}                                                              
"""

        @Language("JAVA")
        private val nonLiteralExpressionFile = """                
package com.ichi2.anki.lint.rules;                             
import java.util.*;                                            
                                                               
class MyTest {                                                 
   private static final int ZERO = 0;                          
   private List<String> ls = new ArrayList<>();                
   private List<String> ls2 = new ArrayList<>();               
                                                               
   public boolean isEmptyNotLiteral1() {                       
       return this.ls.size() == MyTest.ZERO;                   
   }                                                           
                                                               
   public boolean isEmptyNotLiteral2() {                       
       List<String> s;                                         
       return (s != null ? s : this.ls).size() == MyTest.ZERO; 
   }                                                           
                                                               
   public boolean isEmptyNotLiteral3() {                       
       return this.ls.size() <= MyTest.ZERO;                   
   }                                                           
                                                               
   public boolean isNotEmptyNotLiteral() {                     
       List<String> s;                                         
       return (s != null ? s : this.ls).size() > MyTest.ZERO;  
   }                                                           
                                                               
   public boolean sizesAreEqual0() {                           
       return this.ls.size() == this.ls2.size();               
   }                                                           
}                                                              
"""
    }

    @Test
    fun showErrorsForSizeChecks() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(discoverableFile))
            .testModes(TestMode.DEFAULT)
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect(
                """src/com/ichi2/anki/lint/rules/MyTest.java:9: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]
       return this.ls.size() == 0;                             
              ~~~~~~~~~~~~~~~~~~~
src/com/ichi2/anki/lint/rules/MyTest.java:13: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]
       return this.ls.size() < 1;                              
              ~~~~~~~~~~~~~~~~~~
src/com/ichi2/anki/lint/rules/MyTest.java:17: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]
       return this.ls.size() > 0;                              
              ~~~~~~~~~~~~~~~~~~
src/com/ichi2/anki/lint/rules/MyTest.java:21: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]
       return this.ls.size() >= 1;                             
              ~~~~~~~~~~~~~~~~~~~
4 errors, 0 warnings"""
            ).expectFixDiffs(
                """
    Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 9: Replace with this.ls.isEmpty():
    @@ -9 +9
    -        return this.ls.size() == 0;                             
    +        return this.ls.isEmpty();                             
    Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 13: Replace with this.ls.isEmpty():
    @@ -13 +13
    -        return this.ls.size() < 1;                              
    +        return this.ls.isEmpty();                              
    Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 17: Replace with !this.ls.isEmpty():
    @@ -17 +17
    -        return this.ls.size() > 0;                              
    +        return !this.ls.isEmpty();                              
    Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 21: Replace with !this.ls.isEmpty():
    @@ -21 +21
    -        return this.ls.size() >= 1;                             
    +        return !this.ls.isEmpty();                             
                """.trimIndent()
            )
    }

    @Test
    fun shouldIgnoreWhenIsEmptyMissing() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                create(stubListWithoutIsEmpty),
                create(missingIsEmptyFile)
            )
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.")
    }

    @Test
    fun shouldIgnoreWhenSizeCheckingGreaterOne() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(greaterThenOneFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.")
    }

    @Test
    fun shouldIgnoreWhenSizeCheckingWithNonLiteral() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(nonLiteralExpressionFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.")
    }
}
