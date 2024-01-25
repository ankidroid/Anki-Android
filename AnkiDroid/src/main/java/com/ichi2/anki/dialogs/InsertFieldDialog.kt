/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                          *
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

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.R
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
    private lateinit var fieldList: List<String>

    /**
     * A dialog for inserting field in card template editor
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreate(savedInstanceState)
        fieldList = requireArguments().getStringArrayList(KEY_FIELD_ITEMS)!!
        val adapter: RecyclerView.Adapter<*> = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
                return object : RecyclerView.ViewHolder(root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val textView = holder.itemView as TextView
                textView.text = fieldList[position]
                textView.setOnClickListener { selectFieldAndClose(textView) }
            }

            override fun getItemCount(): Int {
                return fieldList.size
            }
        }
        return AlertDialog.Builder(requireContext()).apply {
            title(R.string.card_template_editor_select_field)
            negativeButton(R.string.dialog_cancel)
            customListAdapter(adapter)
        }.create()
    }

    private fun selectFieldAndClose(textView: TextView) {
        parentFragmentManager.setFragmentResult(
            REQUEST_FIELD_INSERT,
            bundleOf(KEY_INSERTED_FIELD to textView.text.toString())
        )
        dismiss()
    }

    companion object {
        /**
         * Other fragments sharing the activity can use this with
         * [androidx.fragment.app.FragmentManager.setFragmentResultListener] to get a result back.
         */
        const val REQUEST_FIELD_INSERT = "request_field_insert"

        /**
         * This fragment requires that a list of fields names to be passed in.
         */
        const val KEY_INSERTED_FIELD = "key_inserted_field"
        private const val KEY_FIELD_ITEMS = "key_field_items"

        fun newInstance(fieldItems: List<String>): InsertFieldDialog = InsertFieldDialog().apply {
            arguments = bundleOf(KEY_FIELD_ITEMS to ArrayList(fieldItems))
        }
    }
}
