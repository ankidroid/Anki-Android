/*
 * Copyright (c) 2026 Sonal Yadav <sonal.y6390@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.lint.utils

import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField

/**
 * All valid layout prefixes for component types.
 */
val ALL_PREFIXES = listOf("activity_", "dialog_", "fragment_", "view_")

/**
 * Represents the type of Android component a class extends.
 */
enum class ComponentType {
    ACTIVITY,
    DIALOG,
    FRAGMENT,
    VIEW,
    UNKNOWN,
}

/**
 * Extension property to get all ViewBinding fields from a UClass.
 * A binding field is identified by its type ending with "Binding".
 */
val UClass.bindings: List<UField>
    get() = fields.filter { it.type.canonicalText.endsWith("Binding") }

/**
 * Determines the component type of a class by traversing its inheritance hierarchy.
 *
 * @return The [ComponentType] of the class, or [ComponentType.UNKNOWN] if it doesn't
 *         extend any known Android component.
 */
fun UClass.getComponentType(): ComponentType {
    var superClass = javaPsi.superClass
    while (superClass != null) {
        val name = superClass.qualifiedName ?: ""

        // Check for Dialog types first (more specific)
        if (name.endsWith("DialogFragment") || name.endsWith("BottomSheetDialogFragment") ||
            name == "android.app.Dialog" || name == "androidx.appcompat.app.AlertDialog"
        ) {
            return ComponentType.DIALOG
        }

        // Check for Activity
        if (name.endsWith("Activity")) {
            return ComponentType.ACTIVITY
        }

        // Check for Fragment (but not DialogFragment which was already checked)
        if (name.endsWith("Fragment")) {
            return ComponentType.FRAGMENT
        }

        // Check for View types
        if (name == "android.view.View" || name == "android.view.ViewGroup" ||
            name.endsWith("Layout") || name.endsWith("ViewGroup")
        ) {
            return ComponentType.VIEW
        }

        superClass = superClass.superClass
    }
    return ComponentType.UNKNOWN
}

/**
 * Converts a ViewBinding class name to the corresponding layout file name.
 *
 * Example: CardBrowserAppearanceBinding -> card_browser_appearance
 *          ActivityDeckPickerBinding -> activity_deck_picker
 *
 * @return The layout name in snake_case, or null if the input doesn't end with "Binding".
 */
fun bindingClassToLayoutName(bindingClassName: String): String? {
    if (!bindingClassName.endsWith("Binding")) return null
    val nameWithoutSuffix = bindingClassName.removeSuffix("Binding")
    return nameWithoutSuffix
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .lowercase()
}

/**
 * Gets the layout name from a binding field.
 *
 * @return The layout name in snake_case, or null if the field is not a binding type.
 */
fun UField.getLayoutName(): String? {
    val type = this.type.canonicalText
    if (!type.endsWith("Binding")) return null
    val bindingClassName = type.substringAfterLast('.')
    return bindingClassToLayoutName(bindingClassName)
}
