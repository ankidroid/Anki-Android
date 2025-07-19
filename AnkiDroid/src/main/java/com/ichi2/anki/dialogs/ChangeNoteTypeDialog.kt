/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.Standard
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.WithWarning
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_REGULAR
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_REGULAR
import com.ichi2.anki.launchCatchingRequiringOneWaySync
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.showError
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.withProgress
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.boldList
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Supports bulk remapping of notes a different note type, remapping fields and templates
 *
 * A full sync is required for this operation
 *
 * input:
 * - a distinct list of notes, which all have the same note type
 *
 * state:
 * - output note type
 * - a map of input field to output field
 * - a map of input template to output template
 *     - only if both input and output note types are non-cloze
 *
 * For maps: a user selects each field/template of the **input note type** which maps to the output
 *
 * @see ChangeNoteTypeViewModel
 */
class ChangeNoteTypeDialog : AnalyticsDialogFragment() {
    private val viewModel: ChangeNoteTypeViewModel by viewModels { defaultViewModelProviderFactory }

    private var initialRotation: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.initialRotation = getScreenRotation()
        setupFlows()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (getScreenRotation() != initialRotation) {
            Timber.d("recreating activity: orientation changed with 'Change Note Type' open")
            requireAnkiActivity().recreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .create {
                title(
                    text =
                        TR.browsingChangeNotetype().toSentenceCase(
                            this@ChangeNoteTypeDialog,
                            R.string.sentence_change_note_type,
                        ),
                )
                positiveButton(R.string.dialog_ok)
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.change_note_type_dialog)
            }.apply {
                show()
                positiveButton.setOnClickListener {
                    requireAnkiActivity().changeNoteType(viewModel)
                    // dismiss() is handled via closeDialogFlow
                }
                launchCatchingTask {
                    val dialogMainLayout = findViewById<View>(R.id.change_note_type_layout)!!
                    val dialogLoadingLayout = findViewById<View>(R.id.change_note_type_loading_layout)!!

                    viewModel.flowOfInitStatus.collect {
                        Timber.i("dialog init: %s", it)
                        when (it) {
                            InitStatus.Pending, InitStatus.InProgress -> {
                                dialogMainLayout.isVisible = false
                                dialogLoadingLayout.isVisible = true
                                positiveButton.isEnabled = false
                            }
                            InitStatus.Completed -> {
                                dialogMainLayout.isVisible = true
                                dialogLoadingLayout.isVisible = false
                                positiveButton.isEnabled = true
                                setupChangeNoteTypeDialog(dialog = this@apply)
                            }
                            is InitStatus.Failed -> {
                                showError(requireActivity(), it.exception.toString(), it.exception)
                                dismiss()
                            }
                        }
                    }
                }
            }

    private fun setupFlows() {
        launchCatchingTask {
            viewModel.closeDialogFlow.filterNotNull().collect {
                Timber.i("Dismissing dialog")
                dismiss()
            }
        }
    }

    private fun setupChangeNoteTypeDialog(dialog: Dialog) {
        Timber.d("setting up dialog")

        data class DisplayNoteType(
            val name: String,
            val isClose: Boolean,
        )

        // set up the note type spinner. Cloze note types are blue to signify that cloze/non-cloze
        // impacts the dialog
        dialog.findViewById<Spinner>(R.id.dest_note_type_spinner)!!.apply {
            val noteTypes = viewModel.availableNoteTypes.map { DisplayNoteType(it.name, it.isCloze) }
            adapter =
                object : ArrayAdapter<DisplayNoteType>(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    noteTypes,
                ) {
                    var clozeColor = MaterialColors.getColor(context, R.attr.clozeColor, Color.BLUE)
                    var defaultViewTextColor: Int? = null
                    var defaultDropDownViewTextColor: Int? = null

                    override fun getItemId(position: Int): Long = viewModel.availableNoteTypes[position].id

                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ) = (super.getView(position, convertView, parent) as CheckedTextView).apply {
                        val noteType = getItem(position)!!
                        text = noteType.name

                        if (defaultViewTextColor == null) {
                            defaultViewTextColor = this.currentTextColor
                        }
                        setTextColor(if (noteType.isClose) clozeColor else defaultViewTextColor!!)
                    }

                    override fun getDropDownView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ) = (super.getDropDownView(position, convertView, parent) as CheckedTextView).apply {
                        val noteType = getItem(position)!!
                        text = noteType.name

                        if (defaultDropDownViewTextColor == null) {
                            defaultDropDownViewTextColor = this.currentTextColor
                        }
                        setTextColor(if (noteType.isClose) clozeColor else defaultDropDownViewTextColor!!)
                    }

                    override fun hasStableIds() = true
                }.apply {
                    // The resource passed to the constructor is normally used for both the spinner view
                    // and the dropdown list. This keeps the former and overrides the latter.
                    setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                }

            val position = viewModel.availableNoteTypes.indexOfFirst { it.id == viewModel.outputNoteType.id }
            setSelection(position, false)

            onItemSelectedListener =
                BasicItemSelectedListener { position, id: NoteTypeId ->
                    viewModel.setOutputNoteTypeId(id)
                }
        }

        // setup viewpager + tabs
        val viewPager = dialog.findViewById<ViewPager2>(R.id.change_note_type_pager)!!
        viewPager.adapter = ChangeNoteTypeStateAdapter(this@ChangeNoteTypeDialog)
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    ChangeNoteTypeViewModel.Tab.entries
                        .first { it.position == position }
                        .let { selectedTab ->
                            viewModel.currentTab = selectedTab
                        }
                    super.onPageSelected(position)
                }
            },
        )

        val tabLayout = dialog.findViewById<TabLayout>(R.id.change_note_type_tab_layout)!!
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            val customView = LayoutInflater.from(tabLayout.context).inflate(R.layout.tab_layout_icon_on_end, null)
            val iconView = customView.findViewById<ImageView>(R.id.tab_icon)
            val textView = customView.findViewById<TextView>(R.id.tab_text)
            when (position) {
                0 -> {
                    iconView.setImageResource(R.drawable.ic_mode_edit_white)
                    textView.text = TR.changeNotetypeFields()
                    tab.text = TR.changeNotetypeFields()
                }
                1 -> {
                    iconView.setImageResource(R.drawable.ic_card_question)
                    textView.text = TR.changeNotetypeTemplates()
                    tab.text = TR.changeNotetypeTemplates()
                }
                else -> throw IllegalStateException("invalid position: $position")
            }
            tab.customView = customView
        }.attach()
        tabLayout.selectTab(tabLayout.getTabAt(0))
    }

    private fun getScreenRotation() = ContextCompat.getDisplayOrDefault(requireContext()).rotation

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"

        @CheckResult
        fun newInstance(noteIds: List<NoteId>) =
            ChangeNoteTypeDialog().apply {
                val ids = noteIds.distinct()
                arguments =
                    bundleOf(
                        ARG_NOTE_IDS to ids.toLongArray(),
                    )
                Timber.i("Showing 'change note type' dialog for %d notes", ids.size)
            }
    }

    class ChangeNoteTypeStateAdapter(
        fragment: Fragment,
    ) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> SelectFieldsFragment()
                1 -> SelectTemplateFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }

        override fun getItemCount() = 2
    }

    class SelectFieldsFragment : Fragment(R.layout.dialog_fields) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })

        val fieldsContainer: LinearLayout
            get() = requireView().findViewById(R.id.fields_container)

        val fieldTextContainer: MaterialTextView
            get() = requireView().findViewById(R.id.field_removal_text)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<MaterialTextView>(R.id.current_field_label).text =
                TR.changeNotetypeCurrent()
            view.findViewById<MaterialTextView>(R.id.new_field_label).text = TR.changeNotetypeNew()

            lifecycleScope.launch {
                viewModel.flowOfInitStatus.collect {
                    if (it is InitStatus.Completed) {
                        setupFlows()
                        createFieldSpinner()
                    }
                }
            }
        }

        override fun onResume() {
            super.onResume()
            requireView().requestLayout()
        }

        fun setupFlows() {
            lifecycleScope.launch {
                viewModel.outputNoteTypeFlow.collect {
                    createFieldSpinner()
                }
            }

            lifecycleScope.launch {
                viewModel.discardedFieldsFlow.collect { fields ->
                    showDiscardedFieldsMessage(fields)
                }
            }
        }

        private fun showDiscardedFieldsMessage(discardedFields: List<String>) {
            fieldTextContainer.isVisible = discardedFields.isNotEmpty()
            if (discardedFields.isEmpty()) {
                return
            }

            fieldTextContainer.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardContent() + " ")
                    .boldList(discardedFields, ", ")
        }

        private fun createFieldSpinner() {
            fieldsContainer.removeAllViews()

            val inputFieldNames = viewModel.inputNoteType.fieldsNames
            val outputFieldNames = viewModel.outputNoteType.fieldsNames

            fun buildFieldLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildFieldSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val fieldSpinnerOptions = inputFieldNames + TR.changeNotetypeNothing()
                        Timber.d("createTemplateSpinner: %d items + (nothing)", fieldSpinnerOptions.size - 1)
                        this.adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                fieldSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                this.setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                            }

                        val selectionIndex = viewModel.fieldChangeMap[spinnerIndex] ?: fieldSpinnerOptions.lastIndex
                        setSelection(selectionIndex, false)

                        // Add an item selection listener to update the field mapping when user changes selection
                        val oldIndex = spinnerIndex
                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == fieldSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateFieldMapping(oldIndex, newMapping)
                            }
                    }

            fun buildFieldText(initialText: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = initialText
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            for (i in outputFieldNames.indices) {
                val fieldLayout =
                    buildFieldLayout().apply {
                        addView(buildFieldSpinner(i))
                        addView(buildFieldText(outputFieldNames[i]))
                    }
                fieldsContainer.addView(fieldLayout)
            }
        }
    }

    class SelectTemplateFragment : Fragment(R.layout.dialog_templates) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })

        val templatesCurrentHeader: MaterialTextView
            get() = requireView().findViewById(R.id.current_template_label)

        val templatesNewHeader: MaterialTextView
            get() = requireView().findViewById(R.id.new_template_label)

        val templatesDefaultLayout: LinearLayout
            get() = requireView().findViewById(R.id.templates_container)
        val templatesHeaderLayout: LinearLayout
            get() = requireView().findViewById(R.id.templates_header_layout)
        val clozeInfoLayout: LinearLayout
            get() = requireView().findViewById(R.id.cloze_info_layout)
        val clozeInfoTextView: MaterialTextView
            get() = requireView().findViewById(R.id.cloze_info_text)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            templatesCurrentHeader.text = TR.changeNotetypeCurrent()
            templatesNewHeader.text = TR.changeNotetypeNew()

            lifecycleScope.launch {
                viewModel.flowOfInitStatus.collect {
                    if (it is InitStatus.Completed) {
                        setupFlows()
                        createTemplateSpinner()
                    }
                }
            }
        }

        fun setupFlows() {
            // show/hide cloze info layout based on note type
            lifecycleScope.launch {
                viewModel.outputNoteTypeFlow.collect {
                    createTemplateSpinner()
                }
            }

            lifecycleScope.launch {
                viewModel.conversionTypeFlow.collect { type ->
                    when (val layout = Layout.fromConversionType(type)) {
                        is Standard -> {
                            clozeInfoLayout.isVisible = false
                        }
                        is WithWarning -> {
                            clozeInfoLayout.isVisible = true
                            clozeInfoTextView.text = getString(layout.warningRes)
                        }
                    }
                }
            }

            lifecycleScope.launch {
                viewModel.canChangeTemplatesFlow.collect { canChangeTemplates ->
                    templatesDefaultLayout.isVisible = canChangeTemplates
                    templatesHeaderLayout.isVisible = canChangeTemplates
                }
            }

            lifecycleScope.launch {
                viewModel.discardedTemplatesFlow.collect { discarded ->
                    showDiscardedTemplatesMessage(discarded)
                }
            }
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout()
        }

        sealed class Layout {
            data object Standard : Layout()

            data class WithWarning(
                @StringRes val warningRes: Int,
            ) : Layout()

            companion object {
                fun fromConversionType(conversionType: ConversionType): Layout =
                    when (conversionType) {
                        REGULAR_TO_REGULAR -> Standard
                        CLOZE_TO_CLOZE, REGULAR_TO_CLOZE ->
                            WithWarning(
                                warningRes = R.string.card_numbers_unchanged,
                            )
                        // Improvement: we could detect this using the max ord of provided notes
                        CLOZE_TO_REGULAR ->
                            WithWarning(
                                warningRes = R.string.extra_cloze_deletions_removed,
                            )
                    }
            }
        }

        private fun showDiscardedTemplatesMessage(discardedTemplateNames: List<String>) {
            val templateRemovalLabel = requireView().findViewById<MaterialTextView>(R.id.template_removal_text)
            templateRemovalLabel.isVisible = discardedTemplateNames.isNotEmpty()
            if (discardedTemplateNames.isEmpty()) {
                return
            }

            templateRemovalLabel.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardCards())
                    .append(" ")
                    .boldList(discardedTemplateNames, LanguageUtil.getListSeparator(requireContext()))
        }

        private fun createTemplateSpinner() {
            templatesDefaultLayout.removeAllViews()

            val inputTemplateNames = viewModel.inputNoteType.templatesNames
            val outputTemplateNames = viewModel.outputNoteType.templatesNames

            fun buildTemplateLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildTemplateSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val templateSpinnerOptions = inputTemplateNames + TR.changeNotetypeNothing()
                        Timber.d("createTemplateSpinner: %d items + (nothing)", templateSpinnerOptions.size - 1)
                        adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                templateSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                            }

                        val selectionIndex = viewModel.templateChangeMap[spinnerIndex] ?: templateSpinnerOptions.lastIndex
                        setSelection(selectionIndex, false)

                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == templateSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateTemplateMapping(outputTemplateIndex = spinnerIndex, newMapping)
                            }
                    }

            fun buildTemplateText(templateName: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = templateName
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }

            for ((i, name) in outputTemplateNames.withIndex()) {
                val templateLayout =
                    buildTemplateLayout().apply {
                        addView(
                            buildTemplateSpinner(
                                spinnerIndex = i,
                            ),
                        )
                        addView(buildTemplateText(templateName = name))
                    }
                templatesDefaultLayout.addView(templateLayout)
            }
        }
    }
}

/**
 * Changes note type of multiple notes, displaying a message on success
 */
private fun AnkiActivity.changeNoteType(viewModel: ChangeNoteTypeViewModel) =
    this.launchCatchingRequiringOneWaySync {
        try {
            val notesUpdated =
                withProgress {
                    viewModel.executeChangeNoteTypeAsync().await()
                }
            val message = resources.getQuantityString(R.plurals.change_note_type_complete, notesUpdated, notesUpdated)
            showSnackbar(message, Snackbar.LENGTH_SHORT)
        } catch (ex: ChangeNoteTypeException) {
            showError(ankiActivity, ex.kind.toString(this))
        }
    }

@VisibleForTesting
fun ChangeNoteTypeException.Kind.toString(context: Context): String =
    when (this) {
        ChangeNoteTypeException.Kind.NO_CHANGES -> context.getString(R.string.error_no_changes_to_save)
    }
