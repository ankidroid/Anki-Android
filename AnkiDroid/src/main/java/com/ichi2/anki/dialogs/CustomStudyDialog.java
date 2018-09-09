/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckOptions;
import com.ichi2.anki.R;
import com.ichi2.anki.Reviewer;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class CustomStudyDialog extends DialogFragment {
    // Different configurations for the context menu
    public static final int CONTEXT_MENU_STANDARD = 0;
    public static final int CONTEXT_MENU_LIMITS = 1;
    public static final int CONTEXT_MENU_EMPTY_SCHEDULE = 2;
    // Standard custom study options to show in the context menu
    private static final int CUSTOM_STUDY_NEW = 100;
    private static final int CUSTOM_STUDY_REV = 101;
    private static final int CUSTOM_STUDY_FORGOT = 102;
    private static final int CUSTOM_STUDY_AHEAD = 103;
    private static final int CUSTOM_STUDY_RANDOM = 104;
    private static final int CUSTOM_STUDY_PREVIEW = 105;
    private static final int CUSTOM_STUDY_TAGS = 106;
    // Special items to put in the context menu
    private static final int DECK_OPTIONS = 107;
    private static final int MORE_OPTIONS = 108;

    public interface CustomStudyListener {
        void onCreateCustomStudySession();
        void onExtendStudyLimits();
    }

    /**
     * Instance factories
     */
    public static CustomStudyDialog newInstance(int id, long did) {
        return newInstance(id, did, false);
    }

    public static CustomStudyDialog newInstance(int id, long did, boolean jumpToReviewer) {
        CustomStudyDialog f = new CustomStudyDialog();
        Bundle args = new Bundle();
        args.putInt("id", id);
        args.putLong("did", did);
        args.putBoolean("jumpToReviewer", jumpToReviewer);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int dialogId = getArguments().getInt("id");
        if (dialogId < 100) {
            // Select the specified deck
            CollectionHelper.getInstance().getCol(getActivity()).getDecks().select(getArguments().getLong("did"));
            return buildContextMenu(dialogId);
        } else {
            return buildInputDialog(dialogId);
        }
    }

    /**
     * Build a context menu for custom study
     * @param id
     * @return
     */
    private MaterialDialog buildContextMenu(int id) {
        int[] listIds = getListIds(id);
        final boolean jumpToReviewer = getArguments ().getBoolean("jumpToReviewer");
        return new MaterialDialog.Builder(this.getActivity())
                .title(R.string.custom_study)
                .cancelable(true)
                .itemsIds(listIds)
                .items(ContextMenuHelper.getValuesFromKeys(getKeyValueMap(), listIds))
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int which,
                                            CharSequence charSequence) {
                        AnkiActivity activity = (AnkiActivity) getActivity();
                        switch (view.getId()) {
                            case DECK_OPTIONS: {
                                // User asked to permanently change the deck options
                                Intent i = new Intent(activity, DeckOptions.class);
                                i.putExtra("did", getArguments().getLong("did"));
                                getActivity().startActivity(i);
                                break;
                            }
                            case MORE_OPTIONS: {
                                // User asked to see all custom study options
                                CustomStudyDialog d = CustomStudyDialog.newInstance(CONTEXT_MENU_STANDARD,
                                        getArguments().getLong("did"), jumpToReviewer);
                                activity.showDialogFragment(d);
                                break;
                            }
                            case CUSTOM_STUDY_TAGS: {
                                /*
                                 * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                                 * number, it is necessary to collect a list of tags. This case handles the creation
                                 * of that Dialog.
                                 */
                                long currentDeck = getArguments().getLong("did");
                                TagsDialog dialogFragment = TagsDialog.newInstance(
                                        TagsDialog.TYPE_CUSTOM_STUDY_TAGS, new ArrayList<String>(),
                                        new ArrayList<>(activity.getCol().getTags().byDeck(currentDeck, true)));
                                dialogFragment.setTagsDialogListener(new TagsDialog.TagsDialogListener() {
                                    @Override
                                    public void onPositive(List<String> selectedTags, int option) {
                                        /*
                                         * Here's the method that gathers the final selection of tags, type of cards and
                                         * generates the search screen for the custom study deck.
                                         */
                                        StringBuilder sb = new StringBuilder();
                                        switch (option) {
                                            case 1:
                                                sb.append("is:new ");
                                                break;
                                            case 2:
                                                sb.append("is:due ");
                                                break;
                                            default:
                                                // Logging here might be appropriate : )
                                                break;
                                        }
                                        List<String> arr = new ArrayList<>();
                                        if (selectedTags.size() > 0) {
                                            for (String tag : selectedTags) {
                                                arr.add(String.format("tag:'%s'", tag));
                                            }
                                            sb.append("(").append(TextUtils.join(" or ", arr)).append(")");
                                        }
                                        createCustomStudySession(new JSONArray(), new Object[]{sb.toString(),
                                                Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, true);
                                    }
                                });
                                activity.showDialogFragment(dialogFragment);
                                break;
                            }
                            default: {
                                // User asked for a standard custom study option
                                CustomStudyDialog d = CustomStudyDialog.newInstance(view.getId(),
                                        getArguments().getLong("did"), jumpToReviewer);
                                ((AnkiActivity) getActivity()).showDialogFragment(d);
                            }
                        }
                    }
                }).build();
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param dialogId
     * @return
     */
    private MaterialDialog buildInputDialog(final int dialogId) {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
        */
        // Input dialogs
        Resources res = getActivity().getResources();
        // Show input dialog for an individual custom study dialog
        View v = getActivity().getLayoutInflater().inflate(R.layout.styled_custom_study_details_dialog, null);
        TextView textView1 = (TextView) v.findViewById(R.id.custom_study_details_text1);
        TextView textView2 = (TextView) v.findViewById(R.id.custom_study_details_text2);
        final EditText mEditText = (EditText) v.findViewById(R.id.custom_study_details_edittext2);
        // Set the text
        textView1.setText(getText1());
        textView2.setText(getText2());
        mEditText.setText(getDefaultValue());
        // Give EditText focus and show keyboard
        mEditText.setSelectAllOnFocus(true);
        mEditText.requestFocus();
        // deck id
        final long did = getArguments().getLong("did");
        // Whether or not to jump straight to the reviewer
        final boolean jumpToReviewer = getArguments ().getBoolean("jumpToReviewer");
        // Set builder parameters
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .customView(v, true)
                .positiveText(res.getString(R.string.dialog_ok))
                .negativeText(res.getString(R.string.dialog_cancel))
                .onPositive((dialog, which) -> {
                    Collection col = CollectionHelper.getInstance().getCol(getActivity());
                    // Get the value selected by user
                    int n;
                    try {
                        n = Integer.parseInt(mEditText.getText().toString());
                    } catch (Exception ignored) {
                        n = Integer.MAX_VALUE;
                    }

                    // Set behavior when clicking OK button
                    switch (dialogId) {
                        case CUSTOM_STUDY_NEW: {
                            try {
                                AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendNew", n).commit();
                                JSONObject deck = col.getDecks().get(did);
                                deck.put("extendNew", n);
                                col.getDecks().save(deck);
                                col.getSched().extendLimits(n, 0);
                                onLimitsExtended(jumpToReviewer);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        }
                        case CUSTOM_STUDY_REV: {
                            try {
                                AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendRev", n).commit();
                                JSONObject deck = col.getDecks().get(did);
                                deck.put("extendRev", n);
                                col.getDecks().save(deck);
                                col.getSched().extendLimits(0, n);
                                onLimitsExtended(jumpToReviewer);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        }
                        case CUSTOM_STUDY_FORGOT: {
                            JSONArray ar = new JSONArray();
                            try {
                                ar.put(0, 1);
                                createCustomStudySession(ar, new Object[] {String.format(Locale.US,
                                        "rated:%d:1", n), Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, false);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        }
                        case CUSTOM_STUDY_AHEAD: {
                            createCustomStudySession(new JSONArray(), new Object[] {String.format(Locale.US,
                                    "prop:due<=%d", n), Consts.DYN_MAX_SIZE, Consts.DYN_DUE}, true);
                            break;
                        }
                        case CUSTOM_STUDY_RANDOM: {
                            createCustomStudySession(new JSONArray(),
                                    new Object[] {"", n, Consts.DYN_RANDOM}, true);
                            break;
                        }
                        case CUSTOM_STUDY_PREVIEW: {
                            createCustomStudySession(new JSONArray(), new Object[] {"is:new added:" +
                                    Integer.toString(n), Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST}, false);
                            break;
                        }
                        default:
                            break;
                    }
                })
                .onNegative((dialog, which) -> ((AnkiActivity) getActivity()).dismissAllDialogFragments());
        final MaterialDialog dialog = builder.build();
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() == 0) {
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
                } else {
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                }
            }
        });

        // Show soft keyboard
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    private HashMap<Integer, String> getKeyValueMap() {
        Resources res = getResources();
        HashMap<Integer, String> keyValueMap = new HashMap<>();
        keyValueMap.put(CONTEXT_MENU_STANDARD, res.getString(R.string.custom_study));
        keyValueMap.put(CUSTOM_STUDY_NEW, res.getString(R.string.custom_study_increase_new_limit));
        keyValueMap.put(CUSTOM_STUDY_REV, res.getString(R.string.custom_study_increase_review_limit));
        keyValueMap.put(CUSTOM_STUDY_FORGOT, res.getString(R.string.custom_study_review_forgotten));
        keyValueMap.put(CUSTOM_STUDY_AHEAD, res.getString(R.string.custom_study_review_ahead));
        keyValueMap.put(CUSTOM_STUDY_RANDOM, res.getString(R.string.custom_study_random_selection));
        keyValueMap.put(CUSTOM_STUDY_PREVIEW, res.getString(R.string.custom_study_preview_new));
        keyValueMap.put(CUSTOM_STUDY_TAGS, res.getString(R.string.custom_study_limit_tags));
        keyValueMap.put(DECK_OPTIONS, res.getString(R.string.study_options));
        keyValueMap.put(MORE_OPTIONS, res.getString(R.string.more_options));
        return keyValueMap;
    }


    /**
     * Retrieve the list of ids to put in the context menu list
     * @param dialogId option to specify which tasks are shown in the list
     * @return the ids of which values to show
     */
    private int[] getListIds(int dialogId) {
        Collection col = ((AnkiActivity) getActivity()).getCol();
        switch (dialogId) {
            case CONTEXT_MENU_STANDARD:
                // Standard context menu
                return new int[] {CUSTOM_STUDY_NEW, CUSTOM_STUDY_REV, CUSTOM_STUDY_FORGOT, CUSTOM_STUDY_AHEAD,
                        CUSTOM_STUDY_RANDOM, CUSTOM_STUDY_PREVIEW, CUSTOM_STUDY_TAGS};
            case CONTEXT_MENU_LIMITS:
                // Special custom study options to show when the daily study limit has been reached
                if (col.getSched().newDue() && col.getSched().revDue()) {
                    return new int[] {CUSTOM_STUDY_NEW, CUSTOM_STUDY_REV, DECK_OPTIONS, MORE_OPTIONS};
                } else {
                    if (col.getSched().newDue()) {
                        return new int[]{CUSTOM_STUDY_NEW, DECK_OPTIONS, MORE_OPTIONS};
                    } else {
                        return new int[]{CUSTOM_STUDY_REV, DECK_OPTIONS, MORE_OPTIONS};
                    }
                }
            case CONTEXT_MENU_EMPTY_SCHEDULE:
                // Special custom study options to show when extending the daily study limits is not applicable
                return new int[] {CUSTOM_STUDY_FORGOT, CUSTOM_STUDY_AHEAD, CUSTOM_STUDY_RANDOM,
                        CUSTOM_STUDY_PREVIEW, CUSTOM_STUDY_TAGS, DECK_OPTIONS};
            default:
                break;
        }
        return null;
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

    /**
     * Create a custom study session
     * @param delays delay options for scheduling algorithm
     * @param terms search terms
     * @param resched whether to reschedule the cards based on the answers given (or ignore them if false)
     */
    private void createCustomStudySession(JSONArray delays, Object[] terms, Boolean resched) {
        JSONObject dyn;
        final AnkiActivity activity = (AnkiActivity) getActivity();
        Collection col = CollectionHelper.getInstance().getCol(activity);
        try {
            long did = getArguments().getLong("did");
            String deckName = col.getDecks().get(did).getString("name");
            String customStudyDeck = getResources().getString(R.string.custom_study_deck_name);
            JSONObject cur = col.getDecks().byName(customStudyDeck);
            if (cur != null) {
                if (cur.getInt("dyn") != 1) {
                    new MaterialDialog.Builder(getActivity())
                            .content(R.string.custom_study_deck_exists)
                            .negativeText(R.string.dialog_cancel)
                            .build().show();
                    return;
                } else {
                    // safe to empty
                    col.getSched().emptyDyn(cur.getLong("id"));
                    // reuse; don't delete as it may have children
                    dyn = cur;
                    col.getDecks().select(cur.getLong("id"));
                }
            } else {
                long customStudyDid = col.getDecks().newDyn(customStudyDeck);
                dyn = col.getDecks().get(customStudyDid);
            }
            // and then set various options
            if (delays.length() > 0) {
                dyn.put("delays", delays);
            } else {
                dyn.put("delays", JSONObject.NULL);
            }
            JSONArray ar = dyn.getJSONArray("terms");
            ar.getJSONArray(0).put(0, "deck:\"" + deckName + "\" " + terms[0]);
            ar.getJSONArray(0).put(1, terms[1]);
            ar.getJSONArray(0).put(2, terms[2]);
            dyn.put("resched", resched);
            // Rebuild the filtered deck
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, new DeckTask.TaskListener() {
                @Override
                public void onCancelled() {
                }

                @Override
                public void onPreExecute() {
                    activity.showProgressBar();
                }

                @Override
                public void onPostExecute(DeckTask.TaskData result) {
                    activity.hideProgressBar();
                    ((CustomStudyListener) activity).onCreateCustomStudySession();
                }

                @Override
                public void onProgressUpdate(DeckTask.TaskData... values) {
                }
            });

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // Hide the dialogs
        activity.dismissAllDialogFragments();
    }

    private void onLimitsExtended(boolean jumpToReviewer) {
        AnkiActivity activity = (AnkiActivity) getActivity();
        if (jumpToReviewer) {
            activity.startActivityForResult(new Intent(activity, Reviewer.class), AnkiActivity.REQUEST_REVIEW);
            CollectionHelper.getInstance().getCol(activity).startTimebox();
        } else {
            ((CustomStudyListener) activity).onExtendStudyLimits();
        }
        activity.dismissAllDialogFragments();
    }
}
