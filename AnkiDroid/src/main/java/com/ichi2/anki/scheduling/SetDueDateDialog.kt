/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.scheduling

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.libanki.CardId
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * Dialog for [Scheduler.setDueDate], containing two tabs: [Tab.SINGLE_DAY] and [Tab.DATE_RANGE]
 *
 *
 * @see SetDueDateViewModel
 */
// We explicitly do not use calendar controls here
// User feedback:
// (1) Don't have to think about what today is in order to use it,
// (2) looking at a calendar makes the future date too concrete
//  (... easier to consider as a nebulous range than a deadline)
// (3) If the interval is changed, it will be set to a number of days, not a date.
// (4) Inconsistent with Anki Desktop
// TODO: This does not handle configuration changes on some EditTexts [screen rotate/night mode]
class SetDueDateDialog : DialogFragment() {
    val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

    // used to determine if a rotation has taken place
    private var initialRotation: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardIds = requireNotNull(requireArguments().getLongArray(ARG_CARD_IDS)) { ARG_CARD_IDS }
        viewModel.init(cardIds)
        Timber.d("Set due date dialog: %d card(s)", cardIds.size)
        this.initialRotation = getScreenRotation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // HACK: After significant effort, I was unable to properly handle the interaction
        // between the ViewPager and `android:configChanges="orientation"`
        // (the window size & position was incorrectly calculated)

        // There was a minor bug in Reviewer (timer is reset), which meant that
        // generally we could not remove the configChanges, we probably can with the CardBrowser
        // For now, only recreate the activity if this dialog is open
        if (getScreenRotation() != initialRotation) {
            Timber.d("recreating activity: orientation changed with 'Set due date' open")
            requireAnkiActivity().recreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext()).create {
            title(text = CollectionManager.TR.actionsSetDueDate().toSentenceCase(this@SetDueDateDialog, R.string.sentence_set_due_date))
            positiveButton(R.string.dialog_ok) { launchUpdateDueDate() }
            negativeButton(R.string.dialog_cancel)
            neutralButton(R.string.help)
            setView(R.layout.dialog_set_due_date)
        }.apply {
            show()

            // This onClickListener stops the dialog from closing when the button is clicked.
            getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener {
                openUrl(R.string.link_set_due_date_help)
            }

            lifecycleScope.launch {
                viewModel.isValidFlow.collect { isValid -> positiveButton.isEnabled = isValid }
            }
            // setup viewpager + tabs
            val viewPager = findViewById<ViewPager2>(R.id.set_due_date_pager)!!
            viewPager.adapter = DueDateStateAdapter(this@SetDueDateDialog)
            val tabLayout = findViewById<TabLayout>(R.id.tab_layout)!!
            TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
                SetDueDateViewModel.Tab.entries.first { it.position == position }
                    .let { selectedTab ->
                        tab.setIcon(selectedTab.icon)
                    }
            }.attach()
            tabLayout.selectTab(tabLayout.getTabAt(0))

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    SetDueDateViewModel.Tab.entries.first { it.position == position }.let { selectedTab ->
                        viewModel.currentTab = selectedTab
                    }
                    super.onPageSelected(position)
                }
            })

            // setup 'set interval to same value' checkbox
            findViewById<MaterialCheckBox>(R.id.change_interval)!!.apply {
                isChecked = viewModel.updateIntervalToMatchDueDate
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.updateIntervalToMatchDueDate = isChecked
                }
            }
        }
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        // this is required for the keyboard to appear: https://stackoverflow.com/a/10133603/
        dialog.window?.clearFlags(FLAG_NOT_FOCUSABLE or FLAG_ALT_FOCUSABLE_IM)

        // The dialog is too wide on tablets
        // Select either 450dp (tablets)
        // or 100% of the screen width (smaller phones)
        val intendedWidth = min(MAX_WIDTH_DP.dpToPx(this.requireContext()), resources.displayMetrics.widthPixels)
        Timber.d("updating width to %d", intendedWidth)
        this.dialog?.window?.setLayout(
            intendedWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun getScreenRotation() = ContextCompat.getDisplayOrDefault(requireContext()).rotation

    private fun launchUpdateDueDate() = requireAnkiActivity().updateDueDate(viewModel)

    companion object {
        const val ARG_CARD_IDS = "ARGS_CARD_IDS"
        const val MAX_WIDTH_DP = 450

        @CheckResult
        fun newInstance(cardIds: List<CardId>) = SetDueDateDialog().apply {
            arguments = bundleOf(ARG_CARD_IDS to cardIds.toLongArray())
            Timber.i("Showing 'set due date' dialog for %d cards", cardIds.size)
        }
    }

    class DueDateStateAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SelectSingleDateFragment()
                1 -> SelectDateRangeFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }
        }

        override fun getItemCount() = 2
    }

    class SelectSingleDateFragment : Fragment(R.layout.set_due_date_single) {

        private val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.findViewById<TextInputLayout>(R.id.set_due_date_single_day_text).apply {
                editText!!.apply {
                    viewModel.nextSingleDayDueDate?.let { value -> setText(value.toString()) }
                    doOnTextChanged { text, _, _, _ ->
                        val currentValue = text?.toString()?.toIntOrNull()
                        viewModel.nextSingleDayDueDate = currentValue
                        suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, currentValue ?: 0)
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                    helperText = getString(
                        R.string.set_due_date_hintText,
                        resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0), // 0 days
                        resources.getQuantityString(R.plurals.set_due_date_label_suffix, 1) // 1 day
                    )
                }
            }
            view.findViewById<TextView>(R.id.date_single_label).text =
                resources.getQuantityString(R.plurals.set_due_date_single_day_label, viewModel.cardCount)
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout() // update the height of the ViewPager
        }
    }

    /**
     * Allows a user to select a start and end date
     */
    class SelectDateRangeFragment : Fragment(R.layout.set_due_date_range) {

        private val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.findViewById<TextInputLayout>(R.id.date_range_start_layout).apply {
                editText!!.apply {
                    viewModel.dateRange.start?.let { start -> setText(start.toString()) }
                    doOnTextChanged { text, _, _, _ ->
                        val value = text.toString().toIntOrNull()
                        viewModel.setNextDateRangeStart(value)
                        suffixText =
                            resources.getQuantityString(R.plurals.set_due_date_label_suffix, value ?: 0)
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                }
            }
            view.findViewById<TextInputLayout>(R.id.date_range_end_layout).apply {
                editText!!.apply {
                    doOnTextChanged { text, _, _, _ ->
                        val value = text.toString().toIntOrNull()
                        viewModel.setNextDateRangeEnd(value)
                        suffixText =
                            resources.getQuantityString(R.plurals.set_due_date_label_suffix, value ?: 0)
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                    viewModel.dateRange.end?.let { end -> setText(end.toString()) }
                }
            }
            view.findViewById<TextView>(R.id.date_range_label).text =
                resources.getQuantityString(R.plurals.set_due_date_range_label, viewModel.cardCount)
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout() // update the height of the ViewPager
        }
    }
}

// this can outlive the lifetime of the fragment
private fun AnkiActivity.updateDueDate(viewModel: SetDueDateViewModel) = this.launchCatchingTask {
    // NICE_TO_HAVE: Display a snackbar if the activity is recreated while this executes
    val cardsUpdated = withProgress {
        // this is async as it should be run on the viewModel
        viewModel.updateDueDateAsync().await()
    }
    Timber.d("updated %d cards", cardsUpdated)

    if (cardsUpdated == null) {
        Timber.w("unable to update due date")
        showThemedToast(this@updateDueDate, R.string.something_wrong, true)
        return@launchCatchingTask
    }
    showSnackbar(TR.schedulingSetDueDateDone(cardsUpdated), Snackbar.LENGTH_SHORT)
}

// TODO: See if we can turn this to a `val` when context parameters are back
fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()
