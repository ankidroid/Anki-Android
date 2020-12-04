package com.ichi2.anki.lint.utils;

import org.jetbrains.uast.UClass;

import java.util.List;

public class LintUtils {

    private LintUtils() {
        // no instantiation
    }


    /**
     * A helper method to check for special classes(Time and SystemTime) where the rules related to time apis shouldn't
     * be applied.
     *
     * @param classes the list of classes to look through
     * @param allowedClasses  the list of classes where the checks should be ignored
     * @return true if this is a class where the checks should not be applied, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAnAllowedClass(List<UClass> classes, String... allowedClasses) {
        boolean isInAllowedClass = false;
        for (int i = 0; i < classes.size(); i++) {
            final String className = classes.get(i).getName();
            for (String allowedClass: allowedClasses) {
                if (className.equals(allowedClass)) {
                    isInAllowedClass = true;
                    break;
                }
            }
        }
        return isInAllowedClass;
    }
}
