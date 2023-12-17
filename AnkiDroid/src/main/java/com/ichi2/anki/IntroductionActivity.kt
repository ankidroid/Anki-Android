/*
 *  Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import com.ichi2.anki.introduction.SetupCollectionFragment.*
import com.ichi2.anki.introduction.SetupCollectionFragment.Companion.handleCollectionSetupOption
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.annotations.NeedsTest
import timber.log.Timber

/**
 * App introduction for new users.
 * TODO: Background of introduction_layout does not display on API 25 emulator: https://github.com/ankidroid/Anki-Android/pull/12033#issuecomment-1228429130
 */
@NeedsTest("Ensure that we can get here on first run without an exception dialog shown")
class IntroductionActivity : AnkiActivity() {

    @NeedsTest("ensure this is called when the activity ends")
    private val onLoginResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("login successful, opening deck picker to sync")
            startDeckPicker(RESULT_SYNC_PROFILE)
        } else {
            Timber.i("login was not successful")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.introduction_activity)

        handleCollectionSetupOption { option ->
            when (option) {
                CollectionSetupOption.DeckPickerWithNewCollection -> startDeckPicker(RESULT_START_NEW)
                CollectionSetupOption.SyncFromExistingAccount -> openLoginDialog()
            }
        }
    }

    private fun openLoginDialog() {
        onLoginResult.launch(Intent(this, LoginActivity::class.java))
    }

    private fun startDeckPicker(result: Int = RESULT_START_NEW) {
        this.sharedPrefs().edit { putBoolean(INTRODUCTION_SLIDES_SHOWN, true) }
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (result == RESULT_SYNC_PROFILE) {
            deckPicker.putExtra(DeckPicker.INTENT_SYNC_FROM_LOGIN, true)
        }

        startActivity(deckPicker)
        finish()
    }

    companion object {
        const val RESULT_START_NEW = 1
        const val RESULT_SYNC_PROFILE = 2

        const val INTRODUCTION_SLIDES_SHOWN = "IntroductionSlidesShown"
    }
}
