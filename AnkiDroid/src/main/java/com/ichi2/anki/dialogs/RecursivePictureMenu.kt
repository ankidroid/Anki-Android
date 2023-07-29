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
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.customListAdapter
import com.afollestad.materialdialogs.list.getRecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
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
                val icon = item.mIcon
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            }

            override fun getItemCount(): Int {
                return items.size
            }
        }
        return MaterialDialog(requireContext()).show {
            customListAdapter(adapter, null)
            title(text = title)
        }.apply { getRecyclerView().updatePadding(bottom = 0) }
    }

    abstract class Item : Parcelable {
        @get:StringRes
        @StringRes
        val title: Int

        @DrawableRes
        val mIcon: Int
        private val mAnalyticsId: String?

        constructor(@StringRes titleString: Int, @DrawableRes iconDrawable: Int, analyticsId: String?) {
            title = titleString
            mIcon = iconDrawable
            mAnalyticsId = analyticsId
        }

        open val children: List<Item?>
            get() = ArrayList(0)

        protected constructor(parcel: Parcel) {
            title = parcel.readInt()
            mIcon = parcel.readInt()
            mAnalyticsId = parcel.readString()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(title)
            dest.writeInt(mIcon)
            dest.writeString(mAnalyticsId)
        }

        protected abstract fun onClicked(activity: AnkiActivity)
        fun sendAnalytics() {
            UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, mAnalyticsId!!)
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
        private val mChildren: MutableList<Item?>?

        constructor(@StringRes titleString: Int, i: Int, analyticsStringId: String?, vararg children: Item?) : super(titleString, i, analyticsStringId) {
            mChildren = ArrayList(listOf(*children))
        }

        override val children: List<Item?>
            get() = ArrayList(mChildren!!.toMutableList())

        public override fun onClicked(activity: AnkiActivity) {
            val children = ArrayList(children)
            val nextFragment: DialogFragment = createInstance(children, title)
            activity.showDialogFragment(nextFragment)
        }

        override fun remove(toRemove: Item?) {
            mChildren!!.remove(toRemove)
            for (i in mChildren) {
                i!!.remove(toRemove)
            }
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            if (parcel.readByte().toInt() == 0x01) {
                mChildren = ArrayList()
                ParcelCompat.readList(parcel, mChildren, Item::class.java.classLoader, Item::class.java)
            } else {
                mChildren = ArrayList(0)
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            if (mChildren == null) {
                dest.writeByte(0x00.toByte())
            } else {
                dest.writeByte(0x01.toByte())
                dest.writeList(mChildren)
            }
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused_symbol")
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
