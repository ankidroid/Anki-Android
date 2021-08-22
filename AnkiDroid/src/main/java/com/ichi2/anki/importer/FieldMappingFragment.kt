/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.importer

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.UiUtil.setSelectedValue
import timber.log.Timber

/** Allows a user to select the mapping between a CSV file with a number of columns, and a note type */
internal class FieldMappingFragment(val mapping: CsvMapping) : Fragment(R.layout.generic_fragment_host) {
    // We could likely improve this further with LiveData, but this change is the first major use
    // of fragments in the app, and I don't want to introduce too many technical concepts in one PR

    private lateinit var rootLayout: LinearLayout
    lateinit var allOptions: List<String>
    lateinit var spinners: MutableList<Spinner>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val viewGroup = view as ViewGroup

        rootLayout = LinearLayout(context)
        rootLayout.orientation = LinearLayout.VERTICAL
        viewGroup.addView(rootLayout)

        setupSpinners(context)

        mapping.onChange.add(Runnable { setValuesFromCsv() })
        mapping.onMajorChange = Runnable { setupSpinners(context) }
    }

    /** Handles a change in CSV (which changes the number of fields) */
    private fun setupSpinners(context: Context) {
        rootLayout.removeAllViews()

        // the list of all available options for mapping (as strings)
        allOptions = mapping.availableOptions.map { it.toDisplayString(context) }

        val spinnerArrayAdapter = ArrayAdapter(context, R.layout.multiline_spinner_item, allOptions)

        spinners = mutableListOf()

        for (mappingBehavior in mapping.csvMap.withIndex()) {
            val label = FixedTextView(context).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                text = getString(R.string.import_field_mapping_label, mappingBehavior.index + 1)
                id = View.generateViewId()
            }

            /* use MODE_DIALOG to allow scrolling without accidentally changing values,
             * and for more room to see a number of options */
            val spinner = Spinner(context, Spinner.MODE_DIALOG).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                adapter = spinnerArrayAdapter
                id = View.generateViewId()
            }

            spinners.add(spinner)

            // relativelayout allows one item on the left, and one on the right
            RelativeLayout(context).apply {
                addView(label)

                addView(spinner)
                rootLayout.addView(this)
            }

            // set spinner on the right of the layout, keeping label on the left
            (spinner.layoutParams as RelativeLayout.LayoutParams).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.END_OF, label.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                spinner.layoutParams = this
            }

            (label.layoutParams as RelativeLayout.LayoutParams).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
                label.layoutParams = this
            }
        }

        setValuesFromCsv()

        for ((index, spinner) in spinners.withIndex()) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    mapping.setMapping(index, mapping.availableOptions[position])

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // intentionally blank
                }
            }
        }
    }

    /** Handles a mapping change */
    private fun setValuesFromCsv() {
        Timber.d("CSV mapping changed")
        for (mappingBehavior in mapping.csvMap.withIndex()) {
            val spinner = spinners[mappingBehavior.index]
            val value = mapping.csvMap[mappingBehavior.index]

            val index = mapping.availableOptions.indexOf(value)
            val valueToSelect = allOptions[index]
            spinner.setSelectedValue(valueToSelect)
        }
    }

    class Factory(private val csvMapping: CsvMapping) : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            if (className == FieldMappingFragment::class.java.name) {
                return FieldMappingFragment(csvMapping)
            }
            return super.instantiate(classLoader, className)
        }
    }
}
