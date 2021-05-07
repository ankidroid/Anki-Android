/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

package com.ichi2.anki

import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.libanki.Utils
import com.ichi2.utils.HelperUtil
import com.ichi2.utils.HtmlUtils

class HelpItemAdapter(val mContext: Context, val mHelperUtilList: List<HelperUtil>?) : RecyclerView.Adapter<HelpItemAdapter.MyHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.helper_item, parent, false)
        return MyHolder(view)
    }

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val question = mHelperUtilList!![position].getQuestion()
        val answer = mHelperUtilList[position].getAnswer()

        // set help item
        holder.mQuestionView.text = question
        // LinkMovementMethod provides text selection(href links) functionality in a TextView.
        holder.mAnswerView.movementMethod = LinkMovementMethod.getInstance()
        holder.mAnswerView.text = formatAnswer(answer)

        // set invisible for all answer view at start
        holder.mAnswerView.visibility = View.GONE

        // set to visible if users click on question
        holder.mQuestionView.setOnClickListener {
            if (holder.mAnswerView.visibility == View.GONE) {
                holder.mAnswerView.visibility = View.VISIBLE
            } else {
                holder.mAnswerView.visibility = View.GONE
            }
        }
    }

    @VisibleForTesting
    fun formatAnswer(desc: String?): Spanned {
        // #5715: In deck description, ignore what is in style and script tag
        // Since we don't currently execute the JS/CSS, it's not worth displaying.
        val withStrippedTags = Utils.stripHTMLScriptAndStyleTags(desc)
        // #5188 - fromHtml displays newlines as " "
        val withFixedNewlines = HtmlUtils.convertNewlinesToHtml(withStrippedTags)
        return HtmlCompat.fromHtml(withFixedNewlines!!, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount(): Int {
        return mHelperUtilList!!.size
    }

    class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var mQuestionView: TextView = itemView.findViewById(R.id.helper_item_question)
        var mAnswerView: TextView = itemView.findViewById(R.id.helper_item_answer)
    }
}
