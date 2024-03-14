/*
 *  Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.widgets.DeckDropDownAdapter
import com.ichi2.libanki.DeckId
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Handles changing of deck in Statistics webview
 *
 * Based in [SingleFragmentActivity], but with `configChanges="orientation|screenSize"`
 * to avoid unwanted activity recreations
 */
class StatisticsActivity :
    SingleFragmentActivity(),
    DeckSelectionDialog.DeckSelectionListener,
    DeckDropDownAdapter.SubtitleListener {

    override val subtitleText: String
        get() = resources.getString(R.string.statistics)

    private val statisticsViewModel: StatisticsViewModel by viewModels()

    private var deckSpinnerSelection: DeckSpinnerSelection? = null
    var deckId: DeckId = 0

    // menu wouldn't be visible in fragment's toolbar unless we override here
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.statistics, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun setupDeckSelector(spinner: Spinner, supportActionBar: ActionBar) {
        startLoadingCollection()
        deckSpinnerSelection = DeckSpinnerSelection(
            this,
            spinner,
            showAllDecks = true,
            alwaysShowDefault = false,
            showFilteredDecks = false
        ).apply {
            lifecycleScope.launch {
                initializeStatsBarDeckSpinner(supportActionBar)
            }
            launchCatchingTask {
                selectDeckById(deckId, true)
            }
        }
    }

    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        if (deck == null) {
            return
        }
        statisticsViewModel.setDeckName(deck.name)
        deckId = deck.deckId
        launchCatchingTask {
            deckSpinnerSelection!!.selectDeckById(deckId, true)
        }
    }

    companion object {

        fun getIntent(context: Context, fragmentClass: KClass<out Fragment>, arguments: Bundle? = null): Intent {
            return Intent(context, StatisticsActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}

/**
 * ViewModel for the StatisticsActivity, storing and managing the current deck name.
 */
class StatisticsViewModel : ViewModel() {
    private val _deckName = MutableLiveData<String>()
    val deckName: LiveData<String> = _deckName

    fun setDeckName(value: String) {
        _deckName.value = value
    }
}
