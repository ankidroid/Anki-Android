/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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
package com.ichi2.libanki

import androidx.annotation.StringRes
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R

@Suppress("FunctionName") // `_new` was a name in libAnki
class StdModels(
    /**
     * Function creating the standard model. Needs to be a function to take the local language into account.
     */
    private val function: CreateStdModels,
    /** Essentially, the default name. As a resource, so that it can
     * be localized later.  */
    @field:StringRes @param:StringRes
    private val defaultNameRes: Int
) {
    fun interface CreateStdModels {
        fun create(mm: Notetypes, name: String): NotetypeJson
    }

    private fun _new(mm: Notetypes): NotetypeJson {
        val name: String = defaultName
        return _new(mm, name)
    }

    private fun _new(mm: Notetypes, name: String): NotetypeJson {
        return function.create(mm, name)
    }

    fun add(col: Collection, name: String): NotetypeJson {
        val mm = col.notetypes
        val model = _new(mm, name)
        mm.add(model)
        return model
    }

    fun add(col: Collection): NotetypeJson {
        val mm = col.notetypes
        val model = _new(mm)
        mm.add(model)
        return model
    }

    val defaultName: String
        get() = AnkiDroidApp.appResources.getString(defaultNameRes)

    companion object {
        // / create the standard models
        val BASIC_MODEL = StdModels(
            { mm: Notetypes, name: String ->
                val m = mm.newModel(name)
                val frontName = AnkiDroidApp.appResources.getString(R.string.front_field_name)
                var fm = mm.newField(frontName)
                mm.addFieldInNewModel(m, fm)
                val backName = AnkiDroidApp.appResources.getString(R.string.back_field_name)
                fm = mm.newField(backName)
                mm.addFieldInNewModel(m, fm)
                val cardOneName = AnkiDroidApp.appResources.getString(R.string.card_n_name, 1)
                val t = Notetypes.newTemplate(cardOneName)
                t.put("qfmt", "{{$frontName}}")
                t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{$backName}}")
                mm.addTemplateInNewModel(m, t)
                m
            },
            R.string.basic_model_name
        )
        val BASIC_TYPING_MODEL = StdModels(
            { mm: Notetypes, name: String ->
                val m = BASIC_MODEL._new(mm, name)
                val t = m.getJSONArray("tmpls").getJSONObject(0)
                val frontName = m.getJSONArray("flds").getJSONObject(0).getString("name")
                val backName = m.getJSONArray("flds").getJSONObject(1).getString("name")
                t.put("qfmt", "{{$frontName}}\n\n{{type:$backName}}")
                t.put("afmt", "{{$frontName}}\n\n<hr id=answer>\n\n{{type:$backName}}")
                m
            },
            R.string.basic_typing_model_name
        )
        private val FORWARD_REVERSE_MODEL = StdModels(
            { mm: Notetypes, name: String ->
                val m = BASIC_MODEL._new(mm, name)
                val frontName = m.getJSONArray("flds").getJSONObject(0).getString("name")
                val backName = m.getJSONArray("flds").getJSONObject(1).getString("name")
                val cardTwoName = AnkiDroidApp.appResources.getString(R.string.card_n_name, 2)
                val t = Notetypes.newTemplate(cardTwoName)
                t.put("qfmt", "{{$backName}}")
                t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{$frontName}}")
                mm.addTemplateInNewModel(m, t)
                m
            },
            R.string.forward_reverse_model_name
        )
        private val FORWARD_OPTIONAL_REVERSE_MODEL = StdModels(
            { mm: Notetypes, name: String ->
                val m = FORWARD_REVERSE_MODEL._new(mm, name)
                val av = AnkiDroidApp.appResources.getString(R.string.field_to_ask_front_name)
                val fm = mm.newField(av)
                mm.addFieldInNewModel(m, fm)
                val t = m.getJSONArray("tmpls").getJSONObject(1)
                t.put("qfmt", "{{#" + av + "}}" + t.getString("qfmt") + "{{/" + av + "}}")
                m
            },
            R.string.forward_optional_reverse_model_name
        )
        private val CLOZE_MODEL = StdModels(
            { mm: Notetypes, name: String? ->
                val m = mm.newModel(
                    name!!
                )
                m.put("type", Consts.MODEL_CLOZE)
                val txt = AnkiDroidApp.appResources.getString(R.string.text_field_name)
                var fm = mm.newField(txt)
                mm.addFieldInNewModel(m, fm)
                val fieldExtraName =
                    AnkiDroidApp.appResources.getString(R.string.extra_field_name_new)
                fm = mm.newField(fieldExtraName)
                mm.addFieldInNewModel(m, fm)
                val cardTypeClozeName =
                    AnkiDroidApp.appResources.getString(R.string.cloze_model_name)
                val t = Notetypes.newTemplate(cardTypeClozeName)
                val fmt = "{{cloze:$txt}}"
                m.put(
                    "css",
                    """${m.getString("css")}
.cloze {
 font-weight: bold;
 color: blue;
}
.nightMode .cloze {
 color: lightblue;
}
"""
                )
                t.put("qfmt", fmt)
                t.put("afmt", "$fmt<br>\n{{$fieldExtraName}}")
                mm.addTemplateInNewModel(m, t)
                m
            },
            R.string.cloze_model_name
        )
        val STD_MODELS = arrayOf(
            BASIC_MODEL,
            BASIC_TYPING_MODEL,
            FORWARD_REVERSE_MODEL,
            FORWARD_OPTIONAL_REVERSE_MODEL,
            CLOZE_MODEL
        )
    }
}
