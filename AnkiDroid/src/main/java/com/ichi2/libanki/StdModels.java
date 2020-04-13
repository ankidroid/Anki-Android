package com.ichi2.libanki;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Models;

import com.ichi2.utils.JSONObject;

import androidx.annotation.StringRes;

public class StdModels {
    /** Essentially, the default name. As a resource, so that it can
     * be localized later. */
    @StringRes
    private int defaultName;
    /**
     * Funtion creating the standard model. Needs to be a funtion to take the local language into account.
     */
    private CreateStdModels fun;

    interface CreateStdModels {
        JSONObject create(Models mm, String name);
    }

    public StdModels(CreateStdModels fun, @StringRes int defaultName) {
        this.fun = fun;
        this.defaultName = defaultName;
    }

    private JSONObject _new(Models mm) {
        String name = getDefaultName();
        return _new(mm, name);
    }

    private JSONObject _new(Models mm, String name) {
        return fun.create(mm, name);
    }

    public JSONObject add(Collection col, String name) {
        Models mm = col.getModels();
        JSONObject model = _new(mm, name);
        mm.add(model);
        return model;
    }

    public JSONObject add(Collection col) {
        Models mm = col.getModels();
        JSONObject model = _new(mm);
        mm.add(model);
        return model;
    }

    public String getDefaultName() {
        return AnkiDroidApp.getAppResources().getString(defaultName);
    }


    /// create the standard models

    public static final StdModels basicModel = new StdModels(
            (Models mm, String name) -> {
                JSONObject m = mm.newModel(name);
                String frontName = AnkiDroidApp.getAppResources().getString(R.string.front_field_name);
                JSONObject fm = mm.newField(frontName);
                mm.addFieldInNewModel(m, fm);
                String backName = AnkiDroidApp.getAppResources().getString(R.string.back_field_name);
                fm = mm.newField(backName);
                mm.addFieldInNewModel(m, fm);
                String cardOneName = AnkiDroidApp.getAppResources().getString(R.string.card_one_name);
                JSONObject t = mm.newTemplate(cardOneName);
                t.put("qfmt", "{{" + frontName + "}}");
                t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{" + backName + "}}");
                mm.addTemplateInNewModel(m, t);
                return m;
            },
            R.string.basic_model_name);

    public static final StdModels basicTypingModel = new StdModels
        ( (Models mm, String name) -> {
        JSONObject m = basicModel._new(mm, name);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(0);
        t.put("afmt", "{{"+"Front"+"}}\n\n<hr id=answer>\n\n{{type:"+"Back"+"}}");
        return m;
    },
        R.string.basic_typing_model_name);

    public static final StdModels forwardReverseModel = new StdModels
        ( (Models mm, String name) -> {
        JSONObject m = basicModel._new(mm, name);
        String frontName = m.getJSONArray("flds").getJSONObject(0).getString("name");
        String backName = m.getJSONArray("flds").getJSONObject(1).getString("name");
        String cardTwoName = AnkiDroidApp.getAppResources().getString(R.string.card_two_name);
        JSONObject t = mm.newTemplate(cardTwoName);
        t.put("qfmt", "{{" + backName + "}}");
        t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{"+frontName+"}}");
        mm.addTemplateInNewModel(m, t);
        return m;
    },
        R.string.forward_reverse_model_name);

    public static final StdModels forwardOptionalReverseModel = new StdModels
        ( (Models mm, String name) -> {
        JSONObject m = forwardReverseModel._new(mm, name);
        String av = AnkiDroidApp.getAppResources().getString(R.string.field_to_ask_front_name);
        JSONObject fm = mm.newField(av);
        mm.addFieldInNewModel(m, fm);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(1);
        t.put("qfmt", "{{#" + av +"}}" + t.get("qfmt") + "{{/" + av +"}}");
        return m;
    },
        R.string.forward_optional_reverse_model_name);

    public static final StdModels clozeModel = new StdModels
        ( (Models mm, String name) -> {
        JSONObject m = mm.newModel(name);
        m.put("type", Consts.MODEL_CLOZE);
        String txt = AnkiDroidApp.getAppResources().getString(R.string.text_field_name);
        JSONObject fm = mm.newField(txt);
        mm.addFieldInNewModel(m, fm);
        String fieldExtraName = AnkiDroidApp.getAppResources().getString(R.string.extra_field_name);
        fm = mm.newField(fieldExtraName);
        mm.addFieldInNewModel(m, fm);
        String cardTypeClozeName = AnkiDroidApp.getAppResources().getString(R.string.card_cloze_name);
        JSONObject t = mm.newTemplate(cardTypeClozeName);
        String fmt = "{{cloze:" + txt + "}}";
        m.put("css", m.getString("css") + ".cloze {" + "font-weight: bold;" + "color: blue;" + "}");
        t.put("qfmt", fmt);
        t.put("afmt", fmt + "<br>\n{{" + fieldExtraName + "}}");
        mm.addTemplateInNewModel(m, t);
        return m;
    },
        R.string.cloze_model_name);

    public static StdModels[] stdModels =
    {
        basicModel,
        basicTypingModel,
        forwardReverseModel,
        forwardOptionalReverseModel,
        clozeModel,
    };
}

