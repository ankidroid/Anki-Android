/*
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

/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki.introduction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption.*

/**
 * Allows a user multiple choices for setting up the collection:
 *
 * * Starting normally
 * * Syncing from AnkiWeb - this allows the user to log in and performs a sync when the DeckPicker is loaded
 *
 * This exists for two reasons:
 * 1) Ensuring that a user does not create two profiles: one for Anki Desktop and one for AnkiDroid
 * 2) Adds a screen that allows for 'advanced' setup.
 * for example: selecting a 'safe' directory using scoped storage, which would not have been deleted
 * if the app is uninstalled.
 */
class SetupCollectionFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.introduction_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.get_started).apply {
            setOnClickListener { setResult(DeckPickerWithNewCollection) }
        }
        view.findViewById<Button>(R.id.sync_profile).apply {
            setOnClickListener { setResult(SyncFromExistingAccount) }
        }
    }

    private fun setResult(option: CollectionSetupOption) {
        setFragmentResult(FRAGMENT_KEY, bundleOf(RESULT_KEY to option))
    }

    enum class CollectionSetupOption {
        /** Continues to the DeckPicker with a new collection */
        DeckPickerWithNewCollection,

        /** Syncs an existing profile from AnkiWeb */
        SyncFromExistingAccount
    }

    companion object {
        const val FRAGMENT_KEY = "collectionSetup"
        const val RESULT_KEY = "result"

        /** Handles a result from a [SetupCollectionFragment] */
        @Suppress("deprecation") // get
        fun FragmentActivity.handleCollectionSetupOption(handleResult: (CollectionSetupOption) -> Unit) {
            supportFragmentManager.setFragmentResultListener(FRAGMENT_KEY, this) { _, b ->
                val item = b[RESULT_KEY] as CollectionSetupOption
                handleResult(item)
            }
        }
    }
}
