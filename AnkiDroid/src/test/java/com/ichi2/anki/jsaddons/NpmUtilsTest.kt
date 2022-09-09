/****************************************************************************************
 * Copyright (c) 2021 Mani infinyte01@gmail.com                                         *
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

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.jsaddons.NpmUtils.validateName
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NpmUtilsTest : RobolectricTest() {

    @Test
    fun validateNameTest() {
        // test case taken from
        // https://stackoverflow.com/questions/50050436/use-npm-package-to-validate-a-package-name

        // valid, errors list contains no errors so it is null
        assertNull(validateName("dashes-hyphens-minus-sign-valid"))
        assertNull(validateName("example.com-periods-valid"))
        assertNull(validateName("under_scores-valid"))
        assertNull(validateName("example-123-numbers-valid"))
        assertNull(validateName("123-starting-with-numbers-valid"))
        assertNull(validateName("@scope/at-sign-for-scope-names-valid"))

        // invalid, errors list contains errors so it is not null
        assertNotNull(validateName(""))
        assertNotNull(validateName(" "))
        assertNotNull(validateName(" foo"))
        assertNotNull(validateName("foo "))
        assertNotNull(validateName("foo "))
        assertNotNull(validateName("foo bar"))
        assertNotNull(validateName("@foo /bar"))
        assertNotNull(validateName("@ foo/bar"))
        assertNotNull(validateName("@foo/ bar"))
        assertNotNull(validateName("slashes@using-at-sign-for-non-scopename-uses-invalid"))
        assertNotNull(validateName("slashes/on-nonscoped-names-invalid"))
        assertNotNull(validateName("pipes|invalid"))
        assertNotNull(validateName("exclamations!for!new!packages!invalid"))
        assertNotNull(validateName("space invalid"))
        assertNotNull(validateName("html%20entities-invalid"))
        assertNotNull(validateName("square[brackets]-invalid"))
        assertNotNull(validateName("commas,invalid"))
        assertNotNull(validateName("colon:invalid"))
        assertNotNull(validateName("semicolon;invalid"))
        assertNotNull(validateName("single-quote\'invalid"))
        assertNotNull(validateName("double-quote\"invalid"))
        assertNotNull(validateName("tilde~invalid"))
        assertNotNull(validateName("simple-(brackets)-invalid"))
        assertNotNull(validateName("curly-{braces}-invalid"))
        assertNotNull(validateName("asterisks*-invalid"))
        assertNotNull(validateName("carets^-invalid"))
        assertNotNull(validateName("pound-sign#-invalid"))
        assertNotNull(validateName("plus-sign+-invalid"))
        assertNotNull(validateName("question-mark?-invalid"))
    }
}
