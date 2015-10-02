
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.KeyEvent;
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
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.Reviewer;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
     * Context menu entries
     */
    private ArrayList<Integer> mEntries = new ArrayList<>();

    /**
     * Instance factories
     */
    public static CustomStudyDialog newInstance(int id) {
        return newInstance(id, false);
    }

    public static CustomStudyDialog newInstance(int id, boolean jumpToReviewer) {
        CustomStudyDialog f = new CustomStudyDialog();
        Bundle args = new Bundle();
        args.putInt("id", id);
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
        String[] entries = getListEntries(id);
        final boolean jumpToReviewer = getArguments ().getBoolean("jumpToReviewer");
        return new MaterialDialog.Builder(this.getActivity())
                .title(R.string.custom_study)
                .cancelable(true)
                .items(entries)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int which,
                                            CharSequence charSequence) {
                        AnkiActivity activity = (AnkiActivity) getActivity();
                        if (mEntries.get(which) == DECK_OPTIONS) {
                            // User asked to permanently change the deck options
                            Intent i = new Intent(activity, DeckOptions.class);
                            i.putExtra("did", activity.getCol().getDecks().selected());
                            getActivity().startActivity(i);
                        } else if (mEntries.get(which) == MORE_OPTIONS) {
                            // User asked to see all custom study options
                            CustomStudyDialog d = CustomStudyDialog.newInstance(CONTEXT_MENU_STANDARD, jumpToReviewer);
                            activity.showDialogFragment(d);
                        } else if (mEntries.get(which) == CUSTOM_STUDY_TAGS) {
                            /*
                             * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                             * number, it is necessary to collect a list of tags. This case handles the creation
                             * of that Dialog.
                             */
                            TagsDialog dialogFragment = TagsDialog.newInstance(
                                    TagsDialog.TYPE_CUSTOM_STUDY_TAGS, new ArrayList<String>(),
                                    new ArrayList<>(activity.getCol().getTags().all()));
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
                                            Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, false);
                                }
                            });
                            activity.showDialogFragment(dialogFragment);
                        } else {
                            // User asked for a standard custom study option
                            CustomStudyDialog d = CustomStudyDialog.newInstance(mEntries.get(which), jumpToReviewer);
                            ((AnkiActivity) getActivity()).showDialogFragment(d);
                        }
                    }
                })
                .build();
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
        // Whether or not to jump straight to the reviewer
        final boolean jumpToReviewer = getArguments ().getBoolean("jumpToReviewer");
        // Set builder parameters
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .customView(v, true)
                .positiveText(res.getString(R.string.dialog_ok))
                .negativeText(res.getString(R.string.dialog_cancel))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        Collection col = CollectionHelper.getInstance().getCol(getActivity());
                        // Get the value selected by user
                        int n = Integer.parseInt(mEditText.getText().toString());
                        // Set behavior when clicking OK button
                        switch (dialogId) {
                            case CUSTOM_STUDY_NEW:
                                try {
                                    AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendNew", n).commit();
                                    JSONObject deck = col.getDecks().current();
                                    deck.put("extendNew", n);
                                    col.getDecks().save(deck);
                                    col.getSched().extendLimits(n, 0);
                                    onLimitsExtended(jumpToReviewer);
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                break;
                            case CUSTOM_STUDY_REV:
                                try {
                                    AnkiDroidApp.getSharedPrefs(getActivity()).edit().putInt("extendRev", n).commit();
                                    JSONObject deck = col.getDecks().current();
                                    deck.put("extendRev", n);
                                    col.getDecks().save(deck);
                                    col.getSched().extendLimits(0, n);
                                    onLimitsExtended(jumpToReviewer);
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                break;
                            case CUSTOM_STUDY_FORGOT:
                                JSONArray ar = new JSONArray();
                                try {
                                    ar.put(0, 1);
                                    createCustomStudySession(ar, new Object[]{String.format(Locale.US,
                                            "rated:%d:1", n), Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, false);
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                break;
                            case CUSTOM_STUDY_AHEAD:
                                createCustomStudySession(new JSONArray(), new Object[]{String.format(Locale.US,
                                        "prop:due<=%d", n), Consts.DYN_MAX_SIZE, Consts.DYN_DUE}, true);
                                break;
                            case CUSTOM_STUDY_RANDOM:
                                createCustomStudySession(new JSONArray(),
                                        new Object[]{"", n, Consts.DYN_RANDOM}, true);
                                break;
                            case CUSTOM_STUDY_PREVIEW:
                                createCustomStudySession(new JSONArray(), new Object[]{"is:new added:" +
                                        Integer.toString(n), Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST}, false);
                                break;
                            default:
                                break;
                        }
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        ((AnkiActivity) getActivity()).dismissAllDialogFragments();
                    }
                });
        final MaterialDialog dialog = builder.build();
        mEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (((EditText) view).getText().length() == 0) {
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
                } else {
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                }
                return false;
            }
        });
        // Show soft keyboard
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    /**
     * Build the list of options to show in the custom study dialog, and the map between position and task name
     * @param idx option to specify which tasks are shown in the list
     * @return the strings to show in the list
     */
    private String[] getListEntries(int idx) {
        mEntries.clear();
        Resources res = getResources();
        String[] entries;
        Collection col = ((AnkiActivity) getActivity()).getCol();
        switch (idx) {
            case CONTEXT_MENU_STANDARD:
                // Standard custom study options
                mEntries.add(CUSTOM_STUDY_NEW);
                mEntries.add(CUSTOM_STUDY_REV);
                mEntries.add(CUSTOM_STUDY_FORGOT);
                mEntries.add(CUSTOM_STUDY_AHEAD);
                mEntries.add(CUSTOM_STUDY_RANDOM);
                mEntries.add(CUSTOM_STUDY_PREVIEW);
                mEntries.add(CUSTOM_STUDY_TAGS);
                entries = new String[7];
                entries[0] = res.getString(R.string.custom_study_increase_new_limit);
                entries[1] = res.getString(R.string.custom_study_increase_review_limit);
                entries[2] = res.getString(R.string.custom_study_review_forgotten);
                entries[3] = res.getString(R.string.custom_study_review_ahead);
                entries[4] = res.getString(R.string.custom_study_random_selection);
                entries[5] = res.getString(R.string.custom_study_preview_new);
                entries[6] = res.getString(R.string.custom_study_limit_tags);
                break;
            case CONTEXT_MENU_LIMITS:
                // Special custom study options to show when the daily study limit has been reached
                if (col.getSched().newDue() && col.getSched().revDue()) {
                    // Both new and due limits have been reached
                    entries = new String[4];
                    entries[0] = res.getString(R.string.custom_study_increase_new_limit);
                    entries[1] = res.getString(R.string.custom_study_increase_review_limit);
                    entries[2] = res.getString(R.string.study_options);
                    entries[3] = res.getString(R.string.custom_study);
                    mEntries.add(CUSTOM_STUDY_NEW);
                    mEntries.add(CUSTOM_STUDY_REV);
                } else {
                    // One and only one of the limits has been reached -- don't show both
                    entries = new String[3];
                    if (col.getSched().newDue()) {
                        entries[0] = res.getString(R.string.custom_study_increase_new_limit);
                        mEntries.add(CUSTOM_STUDY_NEW);
                    } else {
                        entries[0] = res.getString(R.string.custom_study_increase_review_limit);
                        mEntries.add(CUSTOM_STUDY_REV);
                    }
                    entries[1] = res.getString(R.string.study_options);
                    entries[2] = res.getString(R.string.more_options);
                }
                mEntries.add(DECK_OPTIONS);
                mEntries.add(MORE_OPTIONS);
                break;
            case CONTEXT_MENU_EMPTY_SCHEDULE:
                // Special custom study options to show when extending the daily study limits is not applicable
                mEntries.add(CUSTOM_STUDY_FORGOT);
                mEntries.add(CUSTOM_STUDY_AHEAD);
                mEntries.add(CUSTOM_STUDY_RANDOM);
                mEntries.add(CUSTOM_STUDY_PREVIEW);
                mEntries.add(CUSTOM_STUDY_TAGS);
                mEntries.add(DECK_OPTIONS);
                entries = new String[6];
                entries[0] = res.getString(R.string.custom_study_review_forgotten);
                entries[1] = res.getString(R.string.custom_study_review_ahead);
                entries[2] = res.getString(R.string.custom_study_random_selection);
                entries[3] = res.getString(R.string.custom_study_preview_new);
                entries[4] = res.getString(R.string.custom_study_limit_tags);
                entries[5] = res.getString(R.string.study_options);
                break;
            default:
                entries = null;
        }
        return entries;
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
            String deckName = col.getDecks().current().getString("name");
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
                long did = col.getDecks().newDyn(customStudyDeck);
                dyn = col.getDecks().get(did);
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
