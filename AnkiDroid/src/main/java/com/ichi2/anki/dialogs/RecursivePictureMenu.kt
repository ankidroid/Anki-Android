/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.utils.customListAdapter
import com.ichi2.utils.title
import java.util.*

/** A Dialog displaying The various options for "Help" in a nested structure  */
class RecursivePictureMenu : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val items: List<Item> = BundleCompat.getParcelableArrayList(requireArguments(), "bundle", Item::class.java)!!
        val title = requireContext().getString(requireArguments().getInt("titleRes"))
        val adapter: RecyclerView.Adapter<*> = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
                return object : RecyclerView.ViewHolder(root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val textView = holder.itemView as TextView
                val item = items[position]
                textView.setText(item.title)
                textView.setOnClickListener { item.execute(requireActivity() as AnkiActivity) }
                val icon = item.icon
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            }

            override fun getItemCount(): Int {
                return items.size
            }
        }
        val dialog = AlertDialog.Builder(requireContext()).apply {
            customListAdapter(adapter)
            title(text = title)
        }.create()
        return dialog
    }

    abstract class Item : Parcelable {
        @get:StringRes
        @StringRes
        val title: Int

        @DrawableRes
        val icon: Int
        private val analyticsId: String?

        constructor(@StringRes titleString: Int, @DrawableRes iconDrawable: Int, analyticsId: String?) {
            title = titleString
            icon = iconDrawable
            this.analyticsId = analyticsId
        }

        open val children: List<Item?>
            get() = ArrayList(0)

        protected constructor(parcel: Parcel) {
            title = parcel.readInt()
            icon = parcel.readInt()
            analyticsId = parcel.readString()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(title)
            dest.writeInt(icon)
            dest.writeString(analyticsId)
        }

        protected abstract fun onClicked(activity: AnkiActivity)
        fun sendAnalytics() {
            UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, analyticsId!!)
        }

        /* This method calls onClicked method to handle click event in a suitable manner and
         * the analytics of the item clicked are send.
         */
        fun execute(activity: AnkiActivity) {
            sendAnalytics()
            onClicked(activity)
        }

        abstract fun remove(toRemove: Item?)
    }

    class ItemHeader : Item, Parcelable {
        private val _children: MutableList<Item?>?

        constructor(@StringRes titleString: Int, i: Int, analyticsStringId: String?, vararg children: Item?) : super(titleString, i, analyticsStringId) {
            _children = ArrayList(listOf(*children))
        }

        override val children: List<Item?>
            get() = ArrayList(_children!!.toMutableList())

        public override fun onClicked(activity: AnkiActivity) {
            val children = ArrayList(children)
            val nextFragment: DialogFragment = createInstance(children, title)
            activity.showDialogFragment(nextFragment)
        }

        override fun remove(toRemove: Item?) {
            _children!!.remove(toRemove)
            for (i in _children) {
                i!!.remove(toRemove)
            }
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            if (parcel.readByte().toInt() == 0x01) {
                _children = ArrayList()
                ParcelCompat.readList(parcel, _children, Item::class.java.classLoader, Item::class.java)
            } else {
                _children = ArrayList(0)
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            if (_children == null) {
                dest.writeByte(0x00.toByte())
            } else {
                dest.writeByte(0x01.toByte())
                dest.writeList(_children)
            }
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<ItemHeader?> = object : Parcelable.Creator<ItemHeader?> {
                override fun createFromParcel(parcel: Parcel): ItemHeader {
                    return ItemHeader(parcel)
                }

                override fun newArray(size: Int): Array<ItemHeader?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        @CheckResult
        fun createInstance(itemList: ArrayList<Item?>?, @StringRes title: Int): RecursivePictureMenu {
            val helpDialog = RecursivePictureMenu()
            val args = Bundle()
            args.putParcelableArrayList("bundle", itemList)
            args.putInt("titleRes", title)
            helpDialog.arguments = args
            return helpDialog
        }

        fun removeFrom(allItems: List<Item>, toRemove: Item?) {
            // Note: currently doesn't remove the top-level elements.
            for (i in allItems) {
                i.remove(toRemove)
            }
        }
    }
}
