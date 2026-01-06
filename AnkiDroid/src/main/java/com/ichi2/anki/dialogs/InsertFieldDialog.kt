/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_FIELD_ITEMS
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_INSERT_FIELD_METADATA
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_REQUEST_KEY
import com.ichi2.anki.launchCatchingTask
import com.ichi2.utils.create
import com.ichi2.utils.customListAdapter
import com.ichi2.utils.negativeButton
import com.ichi2.utils.title

/**
 * Dialog fragment used to show the fields that the user can insert in the card editor. This
 * fragment can notify other fragments from the same activity about an inserted field.
 *
 * @see [CardTemplateEditor.CardTemplateFragment]
 */
class InsertFieldDialog : DialogFragment() {
    private val viewModel by viewModels<InsertFieldDialogViewModel>()
    private lateinit var requestKey: String

    /**
     * A dialog for inserting field in card template editor
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreate(savedInstanceState)
        requestKey = requireArguments().getString(KEY_REQUEST_KEY)!!
        val adapter: RecyclerView.Adapter<*> =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ): RecyclerView.ViewHolder {
                    val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
                    return object : RecyclerView.ViewHolder(root) {}
                }

                override fun onBindViewHolder(
                    holder: RecyclerView.ViewHolder,
                    position: Int,
                ) {
                    val textView = holder.itemView as TextView
                    val field = viewModel.fieldNames[position]
                    textView.text = field.name
                    textView.setOnClickListener { viewModel.selectNamedField(field) }
                }

                override fun getItemCount(): Int = viewModel.fieldNames.size
            }
        return AlertDialog.Builder(requireContext()).create {
            title(R.string.card_template_editor_select_field)
            negativeButton(R.string.dialog_cancel)
            customListAdapter(adapter)
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // setup flows
        launchCatchingTask {
            viewModel.selectedFieldFlow.collect { field ->
                if (field == null) return@collect
                parentFragmentManager.setFragmentResult(
                    requestKey,
                    bundleOf(KEY_INSERTED_FIELD to field.renderToTemplateTag()),
                )
                dismiss()
            }
        }
    }

    companion object {
        /**
         * A key in the extras of the Fragment Result
         *
         * Represents the template tag for the selected field: `{{Front}}`
         */
        const val KEY_INSERTED_FIELD = "key_inserted_field"

        /**
         * Creates a new instance of [InsertFieldDialog]
         *
         * @param fieldItems The list of field names to be displayed in the dialog.
         * @param requestKey The key used to identify the result when returning the selected field
         *                   to the calling fragment.
         */
        fun newInstance(
            fieldItems: List<String>,
            metadata: InsertFieldMetadata,
            requestKey: String,
        ): InsertFieldDialog =
            InsertFieldDialog().apply {
                arguments =
                    bundleOf(
                        KEY_FIELD_ITEMS to ArrayList(fieldItems),
                        KEY_INSERT_FIELD_METADATA to metadata,
                        KEY_REQUEST_KEY to requestKey,
                    )
            }
    }
}
