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

/**
 * RecyclerView.Adapter class copied almost completely from the Material Dialogs library example
 * {@see [](https://github.com/afollestad/material-dialogs/blob/0.9.6.0/sample/src/main/java/com/afollestad/materialdialogssample/ButtonItemAdapter.java>ButtonItemAdapter.java</a>
 ) */
class ButtonItemAdapter(
    private val items: ArrayList<String>,
    private val itemCallback: ItemCallback,
    private val buttonCallback: ButtonCallback
) : RecyclerView.Adapter<ButtonVH>() {
    fun remove(searchName: String) {
        items.remove(searchName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_browser_item_my_searches_dialog, parent, false)
        return ButtonVH(view, this)
    }

    override fun onBindViewHolder(holder: ButtonVH, position: Int) {
        holder.apply {
            title.text = items[position]
            button.tag = items[position]
        }

    }

    override fun getItemCount() = items.size

    inner class ButtonVH constructor(itemView: View, private val adapter: ButtonItemAdapter) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val title: TextView = itemView.findViewById(R.id.card_browser_my_search_name_textview)
        val button: ImageButton = itemView.findViewById<ImageButton?>(R.id.card_browser_my_search_remove_button).apply {
            setOnClickListener(this@ButtonVH)
        }

        override fun onClick(view: View) {
            if (view is ImageButton) {
                adapter.buttonCallback.onButtonClicked(items[bindingAdapterPosition])
            } else {
                adapter.itemCallback.onItemClicked(items[bindingAdapterPosition])
            }
        }

        init {
            itemView.setOnClickListener(this)
        }
    }

    /**
     * Ensure our strings are sorted alphabetically - call this explicitly after changing
     * the saved searches in any way, prior to displaying them again
     */
    fun notifyAdapterDataSetChanged() {
        items.sortWith { obj: String, str: String -> obj.compareTo(str, ignoreCase = true) }
        super.notifyDataSetChanged()
    }

    fun interface ItemCallback {
        fun onItemClicked(searchName: String)
    }

    fun interface ButtonCallback {
        fun onButtonClicked(searchName: String)
    }
}
