
package com.ichi2.anki.dialogs;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.EditText;
import android.widget.TextView;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class CustomStudyDialog extends DialogFragment {
    // These numbers must correspond to R.array.custom_study_options_labels
    public static final int CUSTOM_STUDY_NEW = 0;
    public static final int CUSTOM_STUDY_REV = 1;
    public static final int CUSTOM_STUDY_FORGOT = 2;
    public static final int CUSTOM_STUDY_AHEAD = 3;
    public static final int CUSTOM_STUDY_RANDOM = 4;
    public static final int CUSTOM_STUDY_PREVIEW = 5;
    public static final int CUSTOM_STUDY_TAGS = 6;
    
    private EditText mEditText;

    public interface CustomStudyDialogListener {
        public void dismissSimpleMessageDialog(boolean reload);
    }

    public static CustomStudyDialog newInstance(int id) {
        CustomStudyDialog f = new CustomStudyDialog();
        Bundle args = new Bundle();
        args.putInt("id", id);
        f.setArguments(args);
        return f;
    }

    @SuppressLint("InflateParams")
	@Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Resources res = getActivity().getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        // Set custom view
        View v = getActivity().getLayoutInflater().inflate(R.layout.styled_custom_study_details_dialog, null);
        TextView textView1 = (TextView) v.findViewById(R.id.custom_study_details_text1);
        TextView textView2 = (TextView) v.findViewById(R.id.custom_study_details_text2);
        mEditText = (EditText) v.findViewById(R.id.custom_study_details_edittext2);
        // Set the text
        textView1.setText(getText1());
        textView2.setText(getText2());
        mEditText.setText(getDefaultValue());
        // Give EditText focus and show keyboard
        mEditText.setSelectAllOnFocus(true);
        mEditText.requestFocus();
        builder.setView(v);
        // Setup buttons
        builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Collection col;
                // Get the value selected by user
                int n = Integer.parseInt(mEditText.getText().toString());
                // Set behavior when clicking OK button
                switch (getArguments().getInt("id")) {
                    case CUSTOM_STUDY_NEW:
                        // Get col, exit if not open
                        //TODO: Find a cleaner way to get the col() from StudyOptionsFragment loader
                        col = CollectionHelper.getInstance().getCol(getActivity());
                        if (col == null || col.getDb()== null) {
                            Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                    .getString(R.string.open_collection_failed_title), false);
                            return;
                        }
                        try {
                            AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendNew", n).commit();
                            JSONObject deck = col.getDecks().current();
                            deck.put("extendNew", n);
                            col.getDecks().save(deck);
                            col.getSched().extendLimits(n, 0);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case CUSTOM_STUDY_REV:
                        // Get col, exit if not open
                        //TODO: Find a cleaner way to get the col() from StudyOptionsFragment loader
                        col = CollectionHelper.getInstance().getCol(getActivity());
                        if (col == null || col.getDb()== null) {
                            Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                    .getString(R.string.open_collection_failed_title), false);
                            return;
                        }
                        try {
                            AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendRev", n).commit();
                            JSONObject deck = col.getDecks().current();
                            deck.put("extendRev", n);
                            col.getDecks().save(deck);
                            col.getSched().extendLimits(0, n);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case CUSTOM_STUDY_FORGOT:
                        JSONArray ar = new JSONArray();
                        try {
                            ar.put(0, 1);
                            ((StudyOptionsListener) getActivity()).createFilteredDeck(ar, new Object[] {
                                    String.format(Locale.US, "rated:%d:1", n), Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM }, false);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case CUSTOM_STUDY_AHEAD:
                        ((StudyOptionsListener) getActivity()).createFilteredDeck(new JSONArray(),
                                new Object[] { String.format(Locale.US, "prop:due<=%d", n),Consts.DYN_MAX_SIZE, Consts.DYN_DUE }, true);
                        break;
                    case CUSTOM_STUDY_RANDOM:
                        ((StudyOptionsListener) getActivity()).createFilteredDeck(new JSONArray(), 
                                new Object[] { "", n, Consts.DYN_RANDOM }, true);
                        break;
                    case CUSTOM_STUDY_PREVIEW:
                        ((StudyOptionsListener) getActivity()).createFilteredDeck(new JSONArray(),
                                new Object[] { "is:new added:" + Integer.toString(n), Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST }, false);
                        break;
                    default:
                        break;                       
                }
            }
        });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((AnkiActivity) getActivity()).dismissAllDialogFragments();
            }
        });
        // Create dialog
        StyledDialog d = builder.create();
        // Show soft keyboard
        d.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);;
        return d;
    }
   
    private String getText1() {
        Resources res = AnkiDroidApp.getAppResources();
        Collection col = CollectionHelper.getInstance().getCol(getActivity());
        switch (getArguments().getInt("id")) {
            case CUSTOM_STUDY_NEW:
                return res.getString(R.string.custom_study_new_total_new, col.getSched().totalNewForCurrentDeck());
            case CUSTOM_STUDY_REV:
                return res.getString(R.string.custom_study_rev_total_rev, col.getSched().totalRevForCurrentDeck());
            default:
                return "";
        }
    }
    
    private String getText2() {
        Resources res = AnkiDroidApp.getAppResources();
        switch (getArguments().getInt("id")) {
            case CUSTOM_STUDY_NEW:
                return res.getString(R.string.custom_study_new_extend);
            case CUSTOM_STUDY_REV:
                return res.getString(R.string.custom_study_rev_extend);
            case CUSTOM_STUDY_FORGOT:
                return res.getString(R.string.custom_study_forgotten);
            case CUSTOM_STUDY_AHEAD:
                return res.getString(R.string.custom_study_ahead);
            case CUSTOM_STUDY_RANDOM:
                return res.getString(R.string.custom_study_random);
            case CUSTOM_STUDY_PREVIEW:
                return res.getString(R.string.custom_study_preview);
            default:
                return "";
        }
    }

    private String getDefaultValue() {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getActivity());
        switch (getArguments().getInt("id")) {
            case CUSTOM_STUDY_NEW:
                return Integer.toString(prefs.getInt("extendNew", 10));
            case CUSTOM_STUDY_REV:
                return Integer.toString(prefs.getInt("extendRev", 50));
            case CUSTOM_STUDY_FORGOT:
                return Integer.toString(prefs.getInt("forgottenDays", 1));
            case CUSTOM_STUDY_AHEAD:
                return Integer.toString(prefs.getInt("aheadDays", 1));
            case CUSTOM_STUDY_RANDOM:
                return Integer.toString(prefs.getInt("randomCards", 100));
            case CUSTOM_STUDY_PREVIEW:
                return Integer.toString(prefs.getInt("previewDays", 1));
            default:
                return "";
        }
    }
}
