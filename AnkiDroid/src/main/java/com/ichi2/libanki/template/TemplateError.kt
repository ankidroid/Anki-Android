/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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
package com.ichi2.libanki.template

import android.content.Context
import com.ichi2.anki.R

abstract class TemplateError : NoSuchElementException() {

    abstract fun message(context: Context): String

    class NoClosingBrackets(val remaining: String) : TemplateError() {
        override fun message(context: Context): String {
            return context.getString(R.string.missing_closing_bracket, remaining)
        }
    }

    class ConditionalNotClosed(val fieldName: String) : TemplateError() {
        override fun message(context: Context): String {
            return context.getString(R.string.open_tag_not_closed, fieldName)
        }
    }

    class WrongConditionalClosed(val found: String, val expected: String) : TemplateError() {
        override fun message(context: Context): String {
            return context.getString(R.string.wrong_tag_closed, found, expected)
        }
    }

    class ConditionalNotOpen(val closed: String) : TemplateError() {
        override fun message(context: Context): String {
            return context.getString(R.string.closed_tag_not_open, closed)
        }
    }

    class FieldNotFound(val filters: List<String>, val field: String) : TemplateError() {
        override fun message(context: Context): String {
            return context.getString(R.string.no_field, field)
        }
    }
}
