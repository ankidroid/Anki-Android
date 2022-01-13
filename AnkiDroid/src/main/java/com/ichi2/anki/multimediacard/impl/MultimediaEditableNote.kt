/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.impl

import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import org.acra.util.IOUtils
import java.util.*

/**
 * Implementation of the editable note.
 * <p>
 * Has to be translate to and from anki db format.
 */
class MultimediaEditableNote : IMultimediaEditableNote {
    override var isModified = false
        private set
    private var mFields: ArrayList<IField?>? = null
    var modelId: Long = 0

    /**
     * Field values in the note editor, before any editing has taken place
     * These values should not be modified
     */
    private var mInitialFields: ArrayList<IField?>? = null
    private fun setThisModified() {
        isModified = true
    }

    // package
    fun setNumFields(numberOfFields: Int) {
        fieldsPrivate.clear()
        for (i in 0 until numberOfFields) {
            fieldsPrivate.add(null)
        }
    }

    private val fieldsPrivate: ArrayList<IField?>
        get() {
            if (mFields == null) {
                mFields = ArrayList(0)
            }
            return mFields!!
        }
    override val numberOfFields: Int
        get() = fieldsPrivate.size

    override fun getField(index: Int): IField? {
        return if (index in 0 until numberOfFields) {
            fieldsPrivate[index]
        } else null
    }

    override fun setField(index: Int, field: IField?): Boolean {
        if (index in 0 until numberOfFields) {
            // If the same unchanged field is set.
            if (getField(index) === field) {
                if (field!!.isModified) {
                    setThisModified()
                }
            } else {
                setThisModified()
            }
            fieldsPrivate[index] = field
            return true
        }
        return false
    }

    fun freezeInitialFieldValues() {
        mInitialFields = ArrayList()
        for (f in mFields!!) {
            mInitialFields!!.add(cloneField(f))
        }
    }

    override val initialFieldCount: Int
        get() = mInitialFields!!.size

    override fun getInitialField(index: Int): IField? {
        return cloneField(mInitialFields!![index])
    }

    private fun cloneField(f: IField?): IField? {
        return IOUtils.deserialize(IField::class.java, IOUtils.serialize(f!!))
    }

    companion object {
        private const val serialVersionUID = -6161821367135636659L
    }
}
