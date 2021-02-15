package com.ichi2.libanki;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.utils.JSONObject;

import androidx.annotation.StringRes;

public class StdModels {
    /** Essentially, the default name. As a resource, so that it can
     * be localized later. */
    @StringRes
    private final int defaultName;
    /**
     * Funtion creating the standard model. Needs to be a funtion to take the local language into account.
     */
    private final CreateStdModels fun;

    interface CreateStdModels {
        Model create(Models mm, String name);
    }

    public StdModels(CreateStdModels fun, @StringRes int defaultName) {
        this.fun = fun;
        this.defaultName = defaultName;
    }

    private Model _new(Models mm) {
        String name = getDefaultName();
        return _new(mm, name);
    }

    private Model _new(Models mm, String name) {
        return fun.create(mm, name);
    }

    public Model add(Collection col, String name) {
        Models mm = col.getModels();
        Model model = _new(mm, name);
        mm.add(model);
        return model;
    }

    public Model add(Collection col) {
        Models mm = col.getModels();
        Model model = _new(mm);
        mm.add(model);
        return model;
    }

    public String getDefaultName() {
        return AnkiDroidApp.getAppResources().getString(defaultName);
    }


    /// create the standard models

    public static final StdModels basicModel = new StdModels(
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

    public static final StdModels basicTypingModel = new StdModels
        ((mm, name) -> {
        Model m = basicModel._new(mm, name);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(0);
        String frontName = m.getJSONArray("flds").getJSONObject(0).getString("name");
        String backName = m.getJSONArray("flds").getJSONObject(1).getString("name");
        t.put("qfmt", "{{" + frontName + "}}\n\n{{type:" + backName + "}}");
        t.put("afmt", "{{" + frontName + "}}\n\n<hr id=answer>\n\n{{type:" + backName + "}}");
        return m;
    },
        R.string.basic_typing_model_name);

    public static final StdModels forwardReverseModel = new StdModels
        ((mm, name) -> {
        Model m = basicModel._new(mm, name);
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

    public static final StdModels forwardOptionalReverseModel = new StdModels
        ((mm, name) -> {
        Model m = forwardReverseModel._new(mm, name);
        String av = AnkiDroidApp.getAppResources().getString(R.string.field_to_ask_front_name);
        JSONObject fm = mm.newField(av);
        mm.addFieldInNewModel(m, fm);
        JSONObject t = m.getJSONArray("tmpls").getJSONObject(1);
        t.put("qfmt", "{{#" + av +"}}" + t.getString("qfmt") + "{{/" + av +"}}");
        return m;
    },
        R.string.forward_optional_reverse_model_name);

    public static final StdModels clozeModel = new StdModels
        ((mm, name) -> {
        Model m = mm.newModel(name);
        m.put("type", Consts.MODEL_CLOZE);
        String txt = AnkiDroidApp.getAppResources().getString(R.string.text_field_name);
        JSONObject fm = mm.newField(txt);
        mm.addFieldInNewModel(m, fm);
        String fieldExtraName = AnkiDroidApp.getAppResources().getString(R.string.extra_field_name);
        fm = mm.newField(fieldExtraName);
        mm.addFieldInNewModel(m, fm);
        String cardTypeClozeName = AnkiDroidApp.getAppResources().getString(R.string.cloze_model_name);
        JSONObject t = Models.newTemplate(cardTypeClozeName);
        String fmt = "{{cloze:" + txt + "}}";
        m.put("css", m.getString("css") + ".cloze {" + "font-weight: bold;" + "color: blue;" + "}");
        t.put("qfmt", fmt);
        t.put("afmt", fmt + "<br>\n{{" + fieldExtraName + "}}");
        mm.addTemplateInNewModel(m, t);
        return m;
    },
        R.string.cloze_model_name);

    public static final StdModels[] stdModels =
    {
        basicModel,
        basicTypingModel,
        forwardReverseModel,
        forwardOptionalReverseModel,
        clozeModel,
    };
}

