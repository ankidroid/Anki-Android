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
package com.ichi2.anki.dialogs.customstudy;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import timber.log.Timber;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckOptions;
import com.ichi2.anki.R;
import com.ichi2.anki.Reviewer;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.anki.dialogs.ContextMenuHelper;
import com.ichi2.anki.dialogs.tags.TagsDialog;
import com.ichi2.anki.dialogs.tags.TagsDialogListener;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckManager;
import com.ichi2.libanki.backend.exception.DeckRenameException;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class CustomStudyDialog extends AnalyticsDialogFragment implements
        TagsDialogListener {
    // Different configurations for the context menu
    public static final int CONTEXT_MENU_STANDARD = 0;
    public static final int CONTEXT_MENU_LIMITS = 1;
    public static final int CONTEXT_MENU_EMPTY_SCHEDULE = 2;
    // Standard custom study options to show in the context menu
    private static final int CUSTOM_STUDY_NEW = 100;
    private static final int CUSTOM_STUDY_REV = 101;
    private static final int CUSTOM_STUDY_FORGOT = 102;
    @VisibleForTesting
    public static final int CUSTOM_STUDY_AHEAD = 103;
    private static final int CUSTOM_STUDY_RANDOM = 104;
    private static final int CUSTOM_STUDY_PREVIEW = 105;
    private static final int CUSTOM_STUDY_TAGS = 106;
    // Special items to put in the context menu
    private static final int DECK_OPTIONS = 107;
    private static final int MORE_OPTIONS = 108;


    private final CustomStudyListener mCustomStudyListener;
    private final Collection mCollection;


    public CustomStudyDialog(Collection collection, CustomStudyListener customStudyListener) {
        this.mCollection = collection;
        this.mCustomStudyListener = customStudyListener;
    }


    public interface CustomStudyListener extends CreateCustomStudySessionListener.Callback {
        void onExtendStudyLimits();
        void showDialogFragment(DialogFragment newFragment);
        void dismissAllDialogFragments();
        void startActivityForResultWithoutAnimation(Intent intent, int requestCode);
    }


    public CustomStudyDialog withArguments(int id, long did) {
        return withArguments(id, did, false);
    }


    public CustomStudyDialog withArguments(int id, long did, boolean jumpToReviewer) {
        Bundle args = this.getArguments();
        if (args == null) {
            args = new Bundle();
        }
        args.putInt("id", id);
        args.putLong("did", did);
        args.putBoolean("jumpToReviewer", jumpToReviewer);
        this.setArguments(args);
        return this;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerFragmentResultReceiver(this);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int dialogId = requireArguments().getInt("id");
        if (dialogId < 100) {
            // Select the specified deck
            mCollection.getDecks().select(requireArguments().getLong("did"));
            return buildContextMenu(dialogId);
        } else {
            return buildInputDialog(dialogId);
        }
    }

    /**
     * Build a context menu for custom study
     * @param id the id type of the dialog
     */
    private MaterialDialog buildContextMenu(int id) {
        int[] listIds = getListIds(id);
        final boolean jumpToReviewer = requireArguments().getBoolean("jumpToReviewer");
        return new MaterialDialog.Builder(this.requireActivity())
                .title(R.string.custom_study)
                .cancelable(true)
                .itemsIds(listIds)
                .items(ContextMenuHelper.getValuesFromKeys(getKeyValueMap(), listIds))
                .itemsCallback((materialDialog, view, which, charSequence) -> {
                    switch (view.getId()) {
                        case DECK_OPTIONS: {
                            // User asked to permanently change the deck options
                            Intent i = new Intent(requireContext(), DeckOptions.class);
                            i.putExtra("did", requireArguments().getLong("did"));
                            requireActivity().startActivity(i);
                            break;
                        }
                        case MORE_OPTIONS: {
                            // User asked to see all custom study options
                            final CustomStudyDialog d = new CustomStudyDialog(mCollection, mCustomStudyListener)
                                    .withArguments(
                                            CONTEXT_MENU_STANDARD,
                                            requireArguments().getLong("did"),
                                            jumpToReviewer
                                    );
                            mCustomStudyListener.showDialogFragment(d);
                            break;
                        }
                        case CUSTOM_STUDY_TAGS: {
                            /*
                             * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                             * number, it is necessary to collect a list of tags. This case handles the creation
                             * of that Dialog.
                             */
                            long currentDeck = requireArguments().getLong("did");
                            TagsDialog dialogFragment = new TagsDialog().withArguments(
                                    TagsDialog.DialogType.CUSTOM_STUDY_TAGS, new ArrayList<>(),
                                    new ArrayList<>(mCollection.getTags().byDeck(currentDeck, true)));
                            mCustomStudyListener.showDialogFragment(dialogFragment);
                            break;
                        }
                        default: {
                            // User asked for a standard custom study option
                            final CustomStudyDialog d = new CustomStudyDialog(mCollection, mCustomStudyListener)
                                    .withArguments(
                                            view.getId(),
                                            requireArguments().getLong("did"),
                                            jumpToReviewer
                                    );
                            mCustomStudyListener.showDialogFragment(d);
                        }
                    }
                }).build();
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param dialogId the id type of the dialog
     */
    private MaterialDialog buildInputDialog(final int dialogId) {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
        */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        @SuppressLint("InflateParams")
        View v = requireActivity().getLayoutInflater().inflate(R.layout.styled_custom_study_details_dialog, null);
        TextView textView1 = v.findViewById(R.id.custom_study_details_text1);
        TextView textView2 = v.findViewById(R.id.custom_study_details_text2);
        final EditText editText = v.findViewById(R.id.custom_study_details_edittext2);
        // Set the text
        textView1.setText(getText1());
        textView2.setText(getText2());
        editText.setText(getDefaultValue());
        // Give EditText focus and show keyboard
        editText.setSelectAllOnFocus(true);
        editText.requestFocus();
        if (dialogId == CUSTOM_STUDY_NEW || dialogId == CUSTOM_STUDY_REV) {
            editText.setInputType(EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_SIGNED);
        }
        // deck id
        final long did = requireArguments().getLong("did");
        // Whether or not to jump straight to the reviewer
        final boolean jumpToReviewer = requireArguments().getBoolean("jumpToReviewer");
        // Set builder parameters
        MaterialDialog.Builder builder = new MaterialDialog.Builder(requireActivity())
                .customView(v, true)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> {
                    // Get the value selected by user
                    int n;
                    try {
                        n = Integer.parseInt(editText.getText().toString());
                    } catch (Exception e) {
                        Timber.w(e);
                        // This should never happen because we disable positive button for non-parsable inputs
                        return;
                    }

                    // Set behavior when clicking OK button
                    switch (dialogId) {
                        case CUSTOM_STUDY_NEW: {
                            AnkiDroidApp.getSharedPrefs(requireActivity()).edit().putInt("extendNew", n).apply();
                            Deck deck = mCollection.getDecks().get(did);
                            deck.put("extendNew", n);
                            mCollection.getDecks().save(deck);
                            mCollection.getSched().extendLimits(n, 0);
                            onLimitsExtended(jumpToReviewer);
                            break;
                        }
                        case CUSTOM_STUDY_REV: {
                            AnkiDroidApp.getSharedPrefs(requireActivity()).edit().putInt("extendRev", n).apply();
                            Deck deck = mCollection.getDecks().get(did);
                            deck.put("extendRev", n);
                            mCollection.getDecks().save(deck);
                            mCollection.getSched().extendLimits(0, n);
                            onLimitsExtended(jumpToReviewer);
                            break;
                        }
                        case CUSTOM_STUDY_FORGOT: {
                            JSONArray ar = new JSONArray();
                            ar.put(0, 1);
                            createCustomStudySession(ar, new Object[] {String.format(Locale.US,
                                    "rated:%d:1", n), Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, false);
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
                                    n, Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST}, false);
                            break;
                        }
                        default:
                            break;
                    }
                })
                .onNegative((dialog, which) -> mCustomStudyListener.dismissAllDialogFragments());
        final MaterialDialog dialog = builder.build();
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                try {
                    Integer.parseInt(editText.getText().toString());
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                } catch (Exception e) {
                    Timber.w(e);
                    dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
                }
            }
        });

        // Show soft keyboard
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    private HashMap<Integer, String> getKeyValueMap() {
        Resources res = getResources();
        HashMap<Integer, String> keyValueMap = HashUtil.HashMapInit(10);
        keyValueMap.put(CONTEXT_MENU_STANDARD, res.getString(R.string.custom_study));
        keyValueMap.put(CUSTOM_STUDY_NEW, res.getString(R.string.custom_study_increase_new_limit));
        keyValueMap.put(CUSTOM_STUDY_REV, res.getString(R.string.custom_study_increase_review_limit));
        keyValueMap.put(CUSTOM_STUDY_FORGOT, res.getString(R.string.custom_study_review_forgotten));
        keyValueMap.put(CUSTOM_STUDY_AHEAD, res.getString(R.string.custom_study_review_ahead));
        keyValueMap.put(CUSTOM_STUDY_RANDOM, res.getString(R.string.custom_study_random_selection));
        keyValueMap.put(CUSTOM_STUDY_PREVIEW, res.getString(R.string.custom_study_preview_new));
        keyValueMap.put(CUSTOM_STUDY_TAGS, res.getString(R.string.custom_study_limit_tags));
        keyValueMap.put(DECK_OPTIONS, res.getString(R.string.menu__deck_options));
        keyValueMap.put(MORE_OPTIONS, res.getString(R.string.more_options));
        return keyValueMap;
    }

    /**
     * Gathers the final selection of tags and type of cards,
     * Generates the search screen for the custom study deck.
     */
    @Override
    public void onSelectedTags(List<String> selectedTags, List<String> indeterminateTags, int option) {
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
        List<String> arr = new ArrayList<>(selectedTags.size());
        if (!selectedTags.isEmpty()) {
            for (String tag : selectedTags) {
                arr.add(String.format("tag:'%s'", tag));
            }
            sb.append("(").append(TextUtils.join(" or ", arr)).append(")");
        }
        createCustomStudySession(new JSONArray(), new Object[] {sb.toString(),
                Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM}, true);
    }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @param dialogId option to specify which tasks are shown in the list
     * @return the ids of which values to show
     */
    private int[] getListIds(int dialogId) {
        switch (dialogId) {
            case CONTEXT_MENU_STANDARD:
                // Standard context menu
                ArrayList<Integer> dialogOptions = new ArrayList<>();
                dialogOptions.add(CUSTOM_STUDY_NEW);
                dialogOptions.add(CUSTOM_STUDY_REV);
                dialogOptions.add(CUSTOM_STUDY_FORGOT);
                dialogOptions.add(CUSTOM_STUDY_AHEAD);
                dialogOptions.add(CUSTOM_STUDY_RANDOM);
                dialogOptions.add(CUSTOM_STUDY_PREVIEW);
                dialogOptions.add(CUSTOM_STUDY_TAGS);
                if (mCollection.getSched().totalNewForCurrentDeck() == 0) {
                    // If no new cards we wont show CUSTOM_STUDY_NEW
                    dialogOptions.remove(Integer.valueOf(CUSTOM_STUDY_NEW));
                }
                return ContextMenuHelper.integerListToArray(dialogOptions);
            case CONTEXT_MENU_LIMITS:
                // Special custom study options to show when the daily study limit has been reached
                if (mCollection.getSched().newDue() && mCollection.getSched().revDue()) {
                    return new int[] {CUSTOM_STUDY_NEW, CUSTOM_STUDY_REV, DECK_OPTIONS, MORE_OPTIONS};
                } else {
                    if (mCollection.getSched().newDue()) {
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
        switch (requireArguments().getInt("id")) {
            case CUSTOM_STUDY_NEW:
                return res.getString(R.string.custom_study_new_total_new, mCollection.getSched().totalNewForCurrentDeck());
            case CUSTOM_STUDY_REV:
                return res.getString(R.string.custom_study_rev_total_rev, mCollection.getSched().totalRevForCurrentDeck());
            default:
                return "";
        }
    }

    private String getText2() {
        Resources res = AnkiDroidApp.getAppResources();
        switch (requireArguments().getInt("id")) {
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
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(requireActivity());
        switch (requireArguments().getInt("id")) {
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
        Deck dyn;
        long did = requireArguments().getLong("did");
        DeckManager decks = mCollection.getDecks();
        String deckToStudyName = decks.get(did).getString("name");
        String customStudyDeck = getResources().getString(R.string.custom_study_deck_name);
        Deck cur = decks.byName(customStudyDeck);
        if (cur != null) {
            Timber.i("Found deck: '%s'", customStudyDeck);
            if (cur.isStd()) {
                Timber.w("Deck: '%s' was non-dynamic", customStudyDeck);
                UIUtils.showThemedToast(requireContext(), getString(R.string.custom_study_deck_exists), true);
                return;
            } else {
                Timber.i("Emptying dynamic deck '%s' for custom study", customStudyDeck);
                // safe to empty
                mCollection.getSched().emptyDyn(cur.getLong("id"));
                // reuse; don't delete as it may have children
                dyn = cur;
                decks.select(cur.getLong("id"));
            }
        } else {
            Timber.i("Creating Dynamic Deck '%s' for custom study", customStudyDeck);
            try {
                dyn = decks.get(decks.newDyn(customStudyDeck));
            } catch (DeckRenameException ex) {
                UIUtils.showThemedToast(requireActivity(), ex.getLocalizedMessage(this.getResources()), true);
                return;
            }
        }
        if (!dyn.has("terms")) {
            //#5959 - temp code to diagnose why terms doesn't exist.
            // normally we wouldn't want to log this much, but we need to know how deep the corruption is to fix the
            // issue
            Timber.w("Invalid Dynamic Deck: %s", dyn);
            AnkiDroidApp.sendExceptionReport("Custom Study Deck had no terms", "CustomStudyDialog - createCustomStudySession");
            UIUtils.showThemedToast(this.getContext(), getString(R.string.custom_study_rebuild_deck_corrupt), false);
            return;
        }
        // and then set various options
        if (delays.length() > 0) {
            dyn.put("delays", delays);
        } else {
            dyn.put("delays", JSONObject.NULL);
        }
        JSONArray ar = dyn.getJSONArray("terms");
        ar.getJSONArray(0).put(0, "deck:\"" + deckToStudyName + "\" " + terms[0]);
        ar.getJSONArray(0).put(1, terms[1]);
        @Consts.DYN_PRIORITY int priority = (Integer) terms[2];
        ar.getJSONArray(0).put(2, priority);
        dyn.put("resched", resched);
        // Rebuild the filtered deck
        Timber.i("Rebuilding Custom Study Deck");
        // PERF: Should be in background
        mCollection.getDecks().save(dyn);
        TaskManager.launchCollectionTask(new CollectionTask.RebuildCram(), createCustomStudySessionListener());

        // Hide the dialogs
        mCustomStudyListener.dismissAllDialogFragments();
    }

    private void onLimitsExtended(boolean jumpToReviewer) {
        if (jumpToReviewer) {
            mCustomStudyListener.startActivityForResultWithoutAnimation(new Intent(requireContext(), Reviewer.class), AnkiActivity.REQUEST_REVIEW);
        } else {
            mCustomStudyListener.onExtendStudyLimits();
        }
        mCustomStudyListener.dismissAllDialogFragments();
    }

    private CreateCustomStudySessionListener createCustomStudySessionListener(){
        return new CreateCustomStudySessionListener(mCustomStudyListener);
    }
}
