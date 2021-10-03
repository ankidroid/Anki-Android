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

package com.ichi2.libanki;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.utils.JSONObject;

import androidx.annotation.StringRes;

public class StdModels {
    /** Essentially, the default name. As a resource, so that it can
     * be localized later. */
    @StringRes
    private final int mDefaultName;
    /**
     * Funtion creating the standard model. Needs to be a funtion to take the local language into account.
     */
    private final CreateStdModels mFun;

    interface CreateStdModels {
        Model create(ModelManager mm, String name);
    }

    public StdModels(CreateStdModels fun, @StringRes int defaultName) {
        this.mFun = fun;
        this.mDefaultName = defaultName;
    }

    private Model _new(ModelManager mm) {
        String name = getDefaultName();
        return _new(mm, name);
    }

    private Model _new(ModelManager mm, String name) {
        return mFun.create(mm, name);
    }

    public Model add(Collection col, String name) {
        ModelManager mm = col.getModels();
        Model model = _new(mm, name);
        mm.add(model);
        return model;
    }

    public Model add(Collection col) {
        ModelManager mm = col.getModels();
        Model model = _new(mm);
        mm.add(model);
        return model;
    }

    public String getDefaultName() {
        return AnkiDroidApp.getAppResources().getString(mDefaultName);
    }


    /// create the standard models

    public static final StdModels BASIC_MODEL = new StdModels(
            (mm, name) -> {
                Model m = mm.newModel(name);
                String frontName = AnkiDroidApp.getAppResources().getString(R.string.front_field_name);
                JSONObject fm = mm.newField(frontName);
                mm.addFieldInNewModel(m, fm);
                String backName = AnkiDroidApp.getAppResources().getString(R.string.back_field_name);
                fm = mm.newField(backName);
                mm.addFieldInNewModel(m, fm);
                String cardOneName = AnkiDroidApp.getAppResources().getString(R.string.card_n_name, 1);
                JSONObject t = Models.newTemplate(cardOneName);
                t.put("qfmt", "{{" + frontName + "}}");
                t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{" + backName + "}}");
                mm.addTemplateInNewModel(m, t);
                return m;
            },
            R.string.basic_model_name);

    public static final StdModels BASIC_TYPING_MODEL = new StdModels
        ((mm, name) -> {
        Model m = BASIC_MODEL._new(mm, name);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(0);
        String frontName = m.getJSONArray("flds").getJSONObject(0).getString("name");
        String backName = m.getJSONArray("flds").getJSONObject(1).getString("name");
        t.put("qfmt", "{{" + frontName + "}}\n\n{{type:" + backName + "}}");
        t.put("afmt", "{{" + frontName + "}}\n\n<hr id=answer>\n\n{{type:" + backName + "}}");
        return m;
    },
        R.string.basic_typing_model_name);

    public static final StdModels FORWARD_REVERSE_MODEL = new StdModels
        ((mm, name) -> {
        Model m = BASIC_MODEL._new(mm, name);
        String frontName = m.getJSONArray("flds").getJSONObject(0).getString("name");
        String backName = m.getJSONArray("flds").getJSONObject(1).getString("name");
        String cardTwoName = AnkiDroidApp.getAppResources().getString(R.string.card_n_name, 2);
        JSONObject t = Models.newTemplate(cardTwoName);
        t.put("qfmt", "{{" + backName + "}}");
        t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{"+frontName+"}}");
        mm.addTemplateInNewModel(m, t);
        return m;
    },
        R.string.forward_reverse_model_name);

    public static final StdModels FORWARD_OPTIONAL_REVERSE_MODEL = new StdModels
        ((mm, name) -> {
        Model m = FORWARD_REVERSE_MODEL._new(mm, name);
        String av = AnkiDroidApp.getAppResources().getString(R.string.field_to_ask_front_name);
        JSONObject fm = mm.newField(av);
        mm.addFieldInNewModel(m, fm);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(1);
        t.put("qfmt", "{{#" + av +"}}" + t.getString("qfmt") + "{{/" + av +"}}");
        return m;
    },
        R.string.forward_optional_reverse_model_name);

    public static final StdModels CLOZE_MODEL = new StdModels
        ((mm, name) -> {
        Model m = mm.newModel(name);
        m.put("type", Consts.MODEL_CLOZE);
        String txt = AnkiDroidApp.getAppResources().getString(R.string.text_field_name);
        JSONObject fm = mm.newField(txt);
        mm.addFieldInNewModel(m, fm);
        String fieldExtraName = AnkiDroidApp.getAppResources().getString(R.string.extra_field_name_new);
        fm = mm.newField(fieldExtraName);
        mm.addFieldInNewModel(m, fm);
        String cardTypeClozeName = AnkiDroidApp.getAppResources().getString(R.string.cloze_model_name);
        JSONObject t = Models.newTemplate(cardTypeClozeName);
        String fmt = "{{cloze:" + txt + "}}";
        m.put("css", m.getString("css") +
                "\n" +
                ".cloze {\n" +
                " font-weight: bold;\n" +
                " color: blue;\n" +
                "}\n" +
                ".nightMode .cloze {\n" +
                " color: lightblue;\n" +
                "}\n");
        t.put("qfmt", fmt);
        t.put("afmt", fmt + "<br>\n{{" + fieldExtraName + "}}");
        mm.addTemplateInNewModel(m, t);
        return m;
    },
        R.string.cloze_model_name);

    public static final StdModels[] STD_MODELS =
    {
        BASIC_MODEL,
        BASIC_TYPING_MODEL,
        FORWARD_REVERSE_MODEL,
        FORWARD_OPTIONAL_REVERSE_MODEL,
        CLOZE_MODEL,
    };
}

