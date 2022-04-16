// noinspection MissingCopyrightHeader #8659
/*
 * The MIT License (MIT)

 Copyright (c) 2014-2016 Aidan Michael Follestad

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
package com.ichi2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.ui.ButtonItemAdapter.ButtonVH
import com.ichi2.utils.KotlinCleanup
import java.util.*

/**
 * RecyclerView.Adapter class copied almost completely from the Material Dialogs library example
 * {@see [](https://github.com/afollestad/material-dialogs/blob/0.9.6.0/sample/src/main/java/com/afollestad/materialdialogssample/ButtonItemAdapter.java>ButtonItemAdapter.java</a>
 ) */
@KotlinCleanup("Fix all IDE lint issues")
class ButtonItemAdapter(private val items: kotlin.collections.ArrayList<String>) : RecyclerView.Adapter<ButtonVH>() {
    @KotlinCleanup("make field non null")
    private var mItemCallback: ItemCallback? = null
    @KotlinCleanup("make field non null")
    private var mButtonCallback: ButtonCallback? = null

    fun remove(searchName: String) {
        items.remove(searchName)
    }

    fun setCallbacks(itemCallback: ItemCallback, buttonCallback: ButtonCallback) {
        mItemCallback = itemCallback
        mButtonCallback = buttonCallback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_browser_item_my_searches_dialog, parent, false)
        return ButtonVH(view, this)
    }

    override fun onBindViewHolder(holder: ButtonVH, position: Int) {
        holder.mTitle.text = items[position]
        holder.mButton.tag = items[position]
    }

    override fun getItemCount(): Int {
        return items.size
    }

    @KotlinCleanup("make this a fun interface to use a lambda at the call site")
    interface ItemCallback {
        fun onItemClicked(searchName: String)
    }

    @KotlinCleanup("make this a fun interface to use a lambda at the call site")
    interface ButtonCallback {
        fun onButtonClicked(searchName: String)
    }

    inner class ButtonVH constructor(itemView: View, adapter: ButtonItemAdapter) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val mTitle: TextView
        val mButton: ImageButton
        private val mAdapter: ButtonItemAdapter

        override fun onClick(view: View) {
            if (mAdapter.mItemCallback == null) {
                return
            }
            if (view is ImageButton) {
                mAdapter.mButtonCallback!!.onButtonClicked(items[bindingAdapterPosition])
            } else {
                mAdapter.mItemCallback!!.onItemClicked(items[bindingAdapterPosition])
            }
        }

        init {
            mTitle = itemView.findViewById(R.id.card_browser_my_search_name_textview)
            mButton = itemView.findViewById(R.id.card_browser_my_search_remove_button)
            mAdapter = adapter
            itemView.setOnClickListener(this)
            mButton.setOnClickListener(this)
        }
    }

    /**
     * Ensure our strings are sorted alphabetically - call this explicitly after changing
     * the saved searches in any way, prior to displaying them again
     */
    fun notifyAdapterDataSetChanged() {
        Collections.sort(items) { obj: String, str: String? -> obj.compareTo(str!!, ignoreCase = true) }
        super.notifyDataSetChanged()
    }
}
