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
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MyAccountTest : RobolectricTest() {

    @Test
    fun testLoginEmailPasswordProvided() {
        val myAccount = super.startActivityNormallyOpenCollectionWithIntent(
            MyAccount::class.java,
            Intent()
        )

        val testPassword = "randomStrongPassword"
        val testEmail = "random.email@example.com"

        myAccount.binding.username.setText(testEmail)
        myAccount.binding.password.setText(testPassword)
        val loginButton = myAccount.binding.loginButton
        assertTrue(loginButton.isEnabled)
    }

    @Test
    fun testLoginFailsNoEmailProvided() {
        val myAccount = super.startActivityNormallyOpenCollectionWithIntent(
            MyAccount::class.java,
            Intent()
        )

        val testPassword = "randomStrongPassword"

        myAccount.binding.password.setText(testPassword)
        val loginButton = myAccount.binding.loginButton
        assertFalse(loginButton.isEnabled)
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        val myAccount = super.startActivityNormallyOpenCollectionWithIntent(
            MyAccount::class.java,
            Intent()
        )

        val testEmail = "random.email@example.com"

        myAccount.binding.username.setText(testEmail)
        val loginButton = myAccount.binding.loginButton
        assertFalse(loginButton.isEnabled)
    }
}
