package com.ichi2.anki.lint.utils

import org.jetbrains.uast.UClass

object LintUtils {
    /**
     * A helper method to check for special classes(Time and SystemTime) where the rules related to time apis shouldn't
     * be applied.
     *
     * @param classes the list of classes to look through
     * @param allowedClasses  the list of classes where the checks should be ignored
     * @return true if this is a class where the checks should not be applied, false otherwise
     */
    @JvmStatic
    fun isAnAllowedClass(classes: List<UClass>, vararg allowedClasses: String): Boolean {
        var isInAllowedClass = false
        for (i in classes.indices) {
            val className = classes[i].name!!
            for (allowedClass in allowedClasses) {
                if (className == allowedClass) {
                    isInAllowedClass = true
                    break
                }
            }
        }
        return isInAllowedClass
    }
}
