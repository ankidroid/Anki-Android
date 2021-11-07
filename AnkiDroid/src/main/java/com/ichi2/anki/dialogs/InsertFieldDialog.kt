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
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import java.util.*

/**
 * This is a reusable convenience class which makes it easy to show a insert field dialog as a DialogFragment.
 * Create a new instance with required fields list, then show it via the fragment manager as usual.
 */
class InsertFieldDialog(private val insertFieldListener: InsertFieldListener) : DialogFragment() {
    private lateinit var mDialog: MaterialDialog
    private lateinit var mFieldList: List<String>
    fun withArguments(fieldItems: List<String>): InsertFieldDialog {
        val args = Bundle()
        args.putStringArrayList("fieldItems", ArrayList(fieldItems))
        this.arguments = args
        return this
    }

    /**
     * A dialog for inserting field in card template editor
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        mFieldList = requireArguments().getStringArrayList("fieldItems")!!
        val adapter: RecyclerView.Adapter<*> = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
                return object : RecyclerView.ViewHolder(root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val textView = holder.itemView as TextView
                textView.text = mFieldList[position]
                textView.setOnClickListener { selectFieldAndClose(textView) }
            }

            override fun getItemCount(): Int {
                return mFieldList.size
            }
        }
        mDialog = MaterialDialog.Builder(requireContext())
            .title(R.string.card_template_editor_select_field)
            .negativeText(R.string.dialog_cancel)
            .adapter(adapter, null)
            .build()
        return mDialog
    }

    private fun selectFieldAndClose(textView: TextView) {
        insertFieldListener.insertField(textView.text.toString())
        mDialog.dismiss()
    }

    interface InsertFieldListener {
        fun insertField(field: String?)
    }
}
