# AnkiDroid Code Conventions

Version 1.0, September 2010. Copyright © 2010. Ported from [repo](https://github.com/ankidroid/Anki-Android/blob/28276da066f3a2842f3ec5dc9fee861358a19e5f/docs/code_conventions/AnkiDroid_code_conventions.html)


# Introduction

This document lists the recommendations applied to AnkiDroid, an Android project based on Anki.

If you find any error on this document or any inconsistency between the recommendations presented here and the automatic checkstyle and formatter, please open an issue.

## Layout of the Recommendations

Each recommendation is presented as a rule followed by examples

## Recommendations Importance

In this document the words _must_, _should_ and _can_ are used to convey the importance of a recommendation. A must requirement have to be followed, a should is a strong recommendation and a can is a general suggestion.

### Automatic Style Checking and Formatting

TODO

# General Recommendations

>  Any violation to the guide is allowed if it enhances one of the following, by order of importance (higher levels cannot be sacrificed for lower levels):
* Logical Structure
* Consistency
* Readability
* Ease of modifications

# Naming Conventions
## General Naming Conventions
### All names should be written in English.
### Package names should be in all lower case.
* `com.company.application.ui`
* `com.sun.eng`
* `edu.cmu.cs.bovik.cheese`
### Class names must be nouns, in mixed case with the first letter of each internal word capitalized.
* `class Line;`
* `class AudioSystem;`
### Variable names must be in mixed case starting with lower case.
* `int age;`
* `float availableWidth;`
### Non-public, non-static field names should start with m.
* `private long mLongVariable;`
* `private int mIntVariable;`
### Static field names should start with s.
* `private static MyClass sSingleton;`
### Constant (final variables) names must be all uppercase using underscore to separate words.
* `public static final int SOME_CONSTANT = 42;`
### Associated constants (final variables) should be prefixed by a common type name.
* `public static final int COLOR_RED = 1;`
* `public static final int COLOR_GREEN = 2;```
* `public static final int COLOR_BLUE = 3; `
### Method names should be verbs in mixed case, with the first letter in lowercase and with the first letter of each internal word capitalized.
* `getName();`
* `computeTotalWidth();`
### Functions (methods with a return) should be named after what they return and procedures (void methods) after what they do.

### In a method name, the name of the object is implicit and should be avoided.
* `employee.getName();`// NOT: <del>employee.getEmployeeName();</del>
### Negated boolean variable names must be avoided.
* `boolean isLoaded;`// NOT: boolean isNotLoaded;`
* `boolean isError;`// NOT:<DEL> boolean isNotError;</del>
### Abbreviations in names should be avoided.
* `computeAverage();`// NOT:<DEL> compAvg();</del>
* `ActionEvent event;`// NOT:<DEL> ActionEvent e;</del>
* `catch (Exception exception) {`// NOT:<DEL> catch (Exception e) {</del>
### Abbreviations and acronyms should not be uppercase when used in a name.
* `getCustomerId();`// NOT:<DEL> getCustomerID();</del>
* `exportHtmlSource();`// NOT:<DEL> exportHTMLSource(); </del>
### Generic variables should have the same name as their type.
* `void setView(View view);`// NOT:<DEL> void setView(View v);</del>
* ``// NOT:<DEL> void setView(View aView);</del>
                              
* `void close(Database database);`// NOT:<DEL> void close(Database db);</del>
* // NOT:<DEL> void close(Database sqliteDB);</del>
### The terms get/set must be used where a class attribute is accessed directly.
* `author.getName();`
* `author.setName(name);`

* `point.getX();`
* `point.setX(3);`
### is prefix should be used for boolean variables and methods. Alternatively and if it fits better, has, can and should prefixes can be used.
* `boolean isVisible;`
* `boolean isOpen();`
* `boolean hasLicense();`
* `boolean canEvaluate();`
* `boolean shouldAbort = false;`
### The term compute can be used in methods where something is computed.
* `valueSet.computeAverage();`
* `matrix.computeInverse();`
### The term find can be used in methods where something is looked up.
* `vertex.findNearestVertex();`
* `matrix.findSmallestElement();`
* `node.findShortestPath(Node destinationNode); `
### The term initialize can be used where an object or a concept is set up.
* `initializeViews();`
### Variables with a large scope should have very descriptive (and usually long) names, variables with a small scope can have short names.

### Iterator variables can be called i, j, k, m, n...
```java
for (int i = 0; i < downloads.size(); i++) {
    statements; 
}
```
### Plural form should be used on names representing a collection of objects.
* `ArrayList downloads;`
* `int[] points;`
### num prefix should be used for variables representing a number of objects.
* `int numPoints = points.length();`
### Number suffix should be used for variables representing an entity number.
* `int employeeNumber;`
* `int comicNumber;`
### Exception classes should have Exception like a suffix.
```java 
class CustomException extends Exception {
    ... 
} 
```
### Singleton classes should return their unique instance through the getInstance method.
```java 
class MySingletonClass {
  private final static MySingletonClass sInstance = new MySingletonClass();

  private MySingletonClass() {
      ...
  }

  public static MySingletonClass getInstance() {  // NOT: get() or instance()...
      return sInstance;
  }
}
```
## Specific Naming Conventions
### String key resources must be all lowercase using underscore to separate words.
* `<string name="good_example_key">Example value</string>  `// NOT:<DEL> <string name="badExampleKey">Example value</string></del>
### XML elements identifiers must be all lowercase using underscore to separate words.
```xml
<TextView android:id="@+id/good_id_example"              // NOT: <TextView android:id="@+id/badIdExample"
    android:layout_width="wrap_content"                              android:layout_width="wrap_content"
    android:layout_height="wrap_content"                             android:layout_height="wrap_content"
    />                                                               />
```
### Activiy names can have Activity like a suffix.
```java
public class ExampleActivity extends Activity {
    ...
}
```
### Test method names should be composed by a name representing what is being tested and a name stating which specific case is being tested, separated with underscore.
* `testMethod_specificCase1`
* `testMethod_specificCase2`

```java
void testIsDistinguishable_protanopia() {
    ColorMatcher colorMatcher = new ColorMatcher(PROTANOPIA)
    assertFalse(colorMatcher.isDistinguishable(Color.RED, Color.BLACK))
    assertTrue(colorMatcher.isDistinguishable(Color.X, Color.Y))
}
```
# Layout Techniques
## Length Line
### File content must be kept within 120 columns.
## Indentation
### Basic indentation should be 4 spaces, without using tabs.
```java
if (condition) {
    statements;
    ...
}
```
### Indentation of any wrapping line should be 8 spaces, without using tabs.
```java
if ((condition1 && condition2)
        || (condition3 && condition4)
        ||!(condition5 && condition6)) {
    doSomethingAboutIt();
}
```
## Braces
### 1TBS (One True Brace Style) must be used. That means:
* Opening brace "{" appears at the end of the same line as the declaration statement.
* Ending brace "}" takes up an entire line by itself and it is intended at the same level that its correspondent opening statement.
* Braces are mandatory, even for single-statements or empty blocks.
```java
class MyClass {
    int func() {
        if (something) {
            // ...
        } else if (somethingElse) {
            // ...
        } else {
            // ...
        }
    }
}
```

```java
// NOT: 
if (condition) body();

if (condition)
    body();
```
## White Spaces
### White space should be used in the following cases:
* After and before operators.
* Before an opening brace.
* After Java reserved words.
* After commas.
* After semicolons in for statements.
* After any comment identifier.
* `a = (b + c) * d;`// NOT: <DEL>a=(b+c)*d</del>

* `if (true) { `// NOT: <DEL>if (true){</del>

* `while (true) {  `// NOT: <DEL>while(true) {</del>

* `doSomething(a, b, c, d); `// NOT: <DEL>doSomething(a,b,c,d);</del>

* `for (i = 0; i < 10; i++) { `// NOT: <DEL>for(i=0;i<10;i++){</del>

* `// This is a comment `// NOT: <DEL>//This is a comment</del>

```java
/**                   // NOT: /**
 * This is a javadoc           *This is a javadoc
 * comment                     *comment
 */                            */
```
## Blank Lines
### Three blank lines should be used in the following circumstances:
* Between sections of a source file.
* Between class and interface definitions.
### Two blank lines should be used between methods.
### One blank line should be used in the following circumstances:
* Between the local variables in a method and its first statement.
* Before a block or single-line comment.
* Between logical sections inside a method, to improve readability
# Control Structures
## if
### The if-else class of statements should have the following form:
```java
if (condition) {
    statements;
}
```

```java
if (condition) {
    statements;
} else {
    statements;
}
```

```java
if (condition) {
    statements;
} else if (condition) {
    statements;
} else {
    statements;
}
```
## for
### The for statement should have the following form:
```java
for (initialization; condition; update) {
    statements;
}
```
## while
### The while statement should have the following form:
```java
while (condition) {
    statements;
}
```
## do-while
### The do-while statement should have the following form:
```java
do {
    statements;
} while (condition);
```
## switch
### The switch statement should have the following form:
```java
switch (condition) {
    case ABC:
        statements;
        // falls through

    case DEF:
        statements;
        break;

    case XYZ:
        statements;
        break;

    default:
        statements;
        break;
}
```
## try-catch
### A try-catch statement should have the following form:
```java
try {
    statements;
} catch (Exception exception) {
    statements;
}
```

```java
try {
    statements;
} catch (Exception exception) {
    statements;
} finally {
    statements;
}
```
# Comments
### All comments should be written in English.
### Comments should not be used to compensate for or explain bad code. Tricky or bad code should be rewritten.
### There should be a white space after any comment identifier.
* `// This is a comment    NOT: //This is a comment`

```java
/**                     NOT: /**
 * This is a javadoc          *This is a javadoc
 * comment                    *comment
 */                           */
```
### Comments should be indented relative to their position in the code.
```java
while (true) {       // NOT: while (true) { 

    // Do something          // Do something
    something();                 something();
}                            }
```
### Javadoc comments should have the following form:
```java
/**
 * Return lateral location of the specified position.
 * If the position is unset, NaN is returned.
 *
 * @param x    X coordinate of position.
 * @param y    Y coordinate of position.
 * @param zone Zone of position.
 * @return     Lateral location.
 * @throws IllegalArgumentException  If zone is <= 0.
 */
public double computeLocation(double x, double y, int zone)
  throws IllegalArgumentException
{
  ...
}
```

### // should be used for all non-Javadoc comments, including multi-line comments.
* `// Comment spanning`
* `// more than one line.`
### All public classes and all public and protected methods within public classes should be documented using Javadoc conventions.
### If a collection of objects can not be qualified with its type, it should be followed by a comment stating the type of its elements.
* `private Vector points; // of Point`
* `private Set shapes; // of Shape`
### For separation comments within differents parts of a file, the following forms should be used (depending on its level of importance):
```java
//*********************
//
//*********************
```

```java
//---------------------
//
//---------------------
```
### TODO and FIXME must be written all in capitals and followed by a colon.
* `// TODO: Calculate the new order            `// NOT: <DEL>// TODO -> Calculate the new order</del>
* `// FIXME: Fix the synchronization algorithm `// NOT: <DEL>fixme: Fix the synchronization algorithm</del>
### The TODO comment should be used to indicate pending tasks, code that is temporary, a short-term solution or good enough but not perfect code.
### The FIXME comment should be used to flag something that is bogus and broken.
# Logging
### All logs should be written in English.
### The use of logs in release should be strictly restricted to the necessary ones.
### Logs should be terse but still understandable.
### Logs must never contain private information or protected content.
### System.out.println() or printf (in native code) must never be used.
### The ERROR level should only be used when something fatal has happened.
### The WARNING level should be used when something serious and unexpected happened.
### The INFORMATIVE level should be used to note something interesting to most people.
### The DEBUG level should be used to note what is happening on the device that could be relevant to investigate and debug unexpected behaviours.
### The VERBOSE level should be used for everything else.
### A DEBUG log should be inside an if (LOCAL_LOGD) block.
```java
if (LOCAL_LOGD) {
    Timber.d("Debugging application");
}
```
### A VERBOSE log should be inside an if (LOCAL_LOGV) block.
```java
if (LOCAL_LOGV) {
    Timber.v("Infroming about current state");
}
```
# File Organization
### The package statement must be the first statement of the file.
### The import statements must follow the package statement. import statements should be sorted by importance, grouped together by packages and leaving one blank line between groups.
The ordering of packages according to their importance is as follows:
* Android imports
* Third parties imports (com, junit, net, org)
* Java imports (java and javax)
* Within each group, the order of imports is alphabetical, considering that capital letters come before lower letters (e.g. Z before a).
```java
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ichi2.utils.DiffEngine;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
```
### Imported classes should always be listed explicitly.
```java
import android.app.Activity;      // NOT: import android.app.*;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
```
### Class and Interface declarations should be organized in the following manner:
1. Class/Interface documentation.
1. class or interface statement.
1. Class (static) variables in the order public, protected, package (no access modifier), private.
1. Instance variables in the order public, protected, package (no access modifier), private.
1. Constructors.
1. Methods.
1. Inner classes.
### Android Components (Activity, Service, BroadcastReceiver and ContentProvider) declarations should be organized in the following manner:
1. Component documentation.
1. class statement.
1. Class (static) variables in the order public, protected, package (no access modifier), private.
1. Instance variables in the order public, protected, package (no access modifier), private.
1. Constructors.
1. Lifecycle methods (ordered following the natural lifecycle, from creation to destruction).
1. Other methods.
1. Inner classes.
### Methods should be vertically ordered following the two following criteria:
#### Dependency
If one function calls another, they should be vertically close, and the caller should be above the callee, if at all possible.
#### Conceptual Affinity
Methods that perform similar tasks or have similar naming should be vertically close.
### Method modifiers should be given in the following order:
1. access modifier: public, protected or private
1. abstract
1. static
1. final
1. transient
1. volatile
1. synchronized
1. native
1. strictfp

* `public static double square(double a); `// NOT: <DEL>static public double square(double a);</del>
# Miscellaneous
## General
### Each declaration should take up an entire line.
```java
int level;
int size;
```
* // NOT: <del> int level, size;</del>
### Each statement should take up an entire line.
```java
i++;
j++;
```// NOT: <del>i++; j++;</del>
### Static variables or methods must always be accessed through the class name and never through an instance variable.
`AClass.classMethod(); `// NOT: <DEL>anObject.classMethod();</del>
### The incompleteness of split lines must be made obvious.
```java
totalSum = a + b + c +
        d + e;
```
        
```java
method(param1, param2, 
        param3);
```
        
```java
setText("Long line split" + 
        "into two parts."); 
```
### Special characters like TAB and page break must be avoided.
## Types
### Type conversions must always be done explicitly. Never rely on implicit type conversion.
* `floatValue = (int) intValue; `// NOT: <DEL>floatValue = intValue;</del>
### Arrays should be declared with their brackets next to the type.
* `int[] points = new int[20]; `// NOT: <DEL>int points[] = new int[20];</del>
## Variables and Constants
### Variables should be initialized where they are declared and they should be declared in the smallest scope possible.
### Variables must never have dual meaning.
### Floating point variables should always be written with decimal point and at least one decimal.
* `double total = 0.0;   `// NOT: <DEL>double total = 0; </del>
* `double speed = 3.0e8; `// NOT: <DEL>double speed = 3e8; </del>
* `double sum; `
* `sum = (a + b) * 10.0; `
### Floating point variables should always be written with a digit before the decimal point.
* `double probability = 0.5; `// NOT: <DEL>double probability = .5;</del>
### Numerical constants (except, in some cases, -1, 0 and 1) should not be coded directly. Use constants instead.
```java
private static final int TEAM_SIZE = 11;
...
Player[] players = new Player[TEAM_SIZE];
```
* // NOT: <del>Player[] players = new Player[11];</del>
## Operators
### Embedded assignments must be avoided.
* `a = b + c; `// NOT: <DEL>d = (a = b + c) + r;</del>
* `d = a + r;`
### The assignment operator should not be used in a place where it can be easily confused with the equality operator.
```java
// NOT:
if (c++ = d++) {
    ...
}
```
### Parenthesis should be used liberally in expressions involving mixed operators in order to make the precedence clear.
* `if ((a == b) && (c == d)) `// NOT: <DEL>if (a == b && c == d)</del>
### If an expression containing a binary operator appears before the ? in the ternary ?: operator, it should be parenthesized.
* `(x >= 0) ? x : -x; `// NOT: <DEL>x >= 0 ? x : -x;</del>
## Conditionals
### Complex conditional expressions must be avoided. Introduce temporary boolean variables instead.
```java
bool isFinished = (elementNo < 0) || (elementNo > maxElement);
bool isRepeatedEntry = elementNo == lastElement; 
if (isFinished || isRepeatedEntry) {
    ... 
} 
```

```java
// NOT: 
if ((elementNo < 0) || (elementNo > maxElement)|| elementNo == lastElement) {
    ... 
}
```
### In an if statement, the normal case should be put in the if-part and the exception in the else-part
```java
boolean isOk = openDatabase(databasePath);
if (isOk) {
    ... 
} else { 
    ... 
} 
```
### Executable statements in conditionals should be avoided.
```java
InputStream stream = File.open(fileName, "w");
if (stream != null) {
    ...
} 
```

```java
// NOT: 
if (File.open(fileName, "w") != null)) {
    ... 
} 
```
## Loops
### Only loop control statements must be included in the for() construction.
```java
maxim = -1;                               // NOT: for (i = 0, maxim = -1; i < 100; i++) { 
for (i = 0; i < 100; i++) {			      maxim = max(maxim, value[i]);
	maxim = max(maxim, value[i]);	          }
}
```
### Loop variables should be initialized immediately before the loop.
```java
boolean isFound = false;    // NOT: boolean isFound = false;
while (!isFound) {                  ...
    ...                             while (!isFound) {
}                                       ...
                                    }
```
### do-while loops can be avoided.
### The use of break and continue in loops should be avoided.
## Exceptions and Finalizers
### Exceptions must not be ignored without a good explanation.
```java
// NOT:
void setServerPort(String value) {
    try {
        serverPort = Integer.parseInt(value);
    } catch (NumberFormatException e) { 
    }
}
```
### Generic Exception should not be caught except at the root of the stack.
```java
try {
    someComplicatedIOFunction();        // may throw IOException 
    someComplicatedParsingFunction();   // may throw ParsingException 
    someComplicatedSecurityFunction();  // may throw SecurityException 
} catch (IOException exception) {                
    handleIOError();                  
} catch (ParsingException exception) {
    handleParsingError();
} catch (SecurityException exception) {
    handleSecurityError();
}	
```

```java
// NOT:
try {
    someComplicatedIOFunction();        // may throw IOException 
    someComplicatedParsingFunction();   // may throw ParsingException 
    someComplicatedSecurityFunction();  // may throw SecurityException 
} catch (Exception exception) {
    handleError();
}
```
### Finalizers should be avoided.

## Android XML Layouts
### At the same level of the element's name, it should only appear the xmlns attribute or the id attribute, in this order.
```xml
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/example_id"
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    >
```

```xml
<RelativeLayout android:id="@+id/example_id"
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    >
```
    
```xml
<RelativeLayout
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    >
```
    
```xml
// NOT:
<RelativeLayout android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    >
    
<RelativeLayout android:id="@+id/example_id"
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    >
```
### Each attribute should take up an entire line.
```xml
<EditText android:id="@+id/answer_field"      	    // NOT: <EditText android:id="@+id/answer_field"
    android:layout_width="fill_parent"				android:layout_width="fill_parent" android:layout_height="wrap_content"
    android:layout_height="wrap_content"	                android:maxLines="2" android:visibility="gone"/>
    android:maxLines="2"
    android:visibility="gone"
    />
```
### Attributes should be grouped by type (layout properties, text propertiesâ€¦) and within the same type, they should be ordered alphabetically.
```xml
<TextView android:id="@+id/example_id"
    android:layout_height="wrap_content"
    android:layout_width="fill_parent"
    android:textColor="#ffffff"
    android:textSize="24sp"
    />
```

```xml
// NOT:
<TextView android:id="@+id/example_id"
    android:layout_height="wrap_content"
    android:textColor="#ffffff"
    android:layout_width="fill_parent"
    android:textSize="24sp"
    />
    
<TextView android:id="@+id/example_id"
    android:layout_height="wrap_content"
    android:layout_width="fill_parent"
    android:textSize="24sp"
    android:textColor="#ffffff"
    />
```
### LinearLayout elements must explicity state the orientation attribute.
```xml
<LinearLayout                                   // NOT: <LinearLayout
    android:layout_width="fill_parent"                      android:layout_width="fill_parent"
    android:layout_height="wrap_content"                    android:layout_height="wrap_content"
    android:orientation="horizontal"                        >
    >	
```
