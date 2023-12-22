/****************************************************************************************
 * Copyright (c) 2022 Ali Ahnaf <aliahnaf327@gmail.com>                                 *
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
 * this program.  If not, see http://www.gnu.org/licenses/>.                            *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.ui.TextInputEditField
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MyAccountTest : RobolectricTest() {
    @Test
    fun testLoginFailsNoEmailProvided() {
        val myAccount =
            super.startActivityNormallyOpenCollectionWithIntent(
                MyAccount::class.java,
                Intent(),
            )

        val testPassword = "randomStrongPassword"

        myAccount.findViewById<TextInputEditField>(R.id.password).setText(testPassword)
        myAccount.findViewById<Button>(R.id.login_button).performClick()
        val error = myAccount.findViewById<TextInputEditText>(R.id.username).error
        assertEquals(error, targetContext.getString(R.string.email_id_empty))
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        val myAccount =
            super.startActivityNormallyOpenCollectionWithIntent(
                MyAccount::class.java,
                Intent(),
            )

        val testEmail = "random.email@example.com"

        myAccount.findViewById<TextInputEditText>(R.id.username).setText(testEmail)
        myAccount.findViewById<Button>(R.id.login_button).performClick()
        val error = myAccount.findViewById<TextInputEditField>(R.id.password).error
        assertEquals(error, targetContext.getString(R.string.password_empty))
    }
}
