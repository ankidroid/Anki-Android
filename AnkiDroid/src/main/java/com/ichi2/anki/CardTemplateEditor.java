/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.ui.SlidingTabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import timber.log.Timber;


/**
 * Allows the user to view the template for the current note type
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes"})
public class CardTemplateEditor extends AnkiActivity {
    private TemplatePagerAdapter mTemplateAdapter;
    private JSONObject mModelBackup = null;
    public static String INTENT_MODEL_FILENAME = "editedModelFilename";
    private ArrayList<Object[]> mTemplateChanges = new ArrayList<>();
    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private long mModelId;
    private long mNoteId;
    private int mOrdId;
    private static final int REQUEST_PREVIEWER = 0;
    private static final String DUMMY_TAG = "DUMMY_NOTE_TO_DELETE_x0-90-fa";


    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------

    /* Used for updating the collection when a reverse card is added or a template is deleted */
    private DeckTask.TaskListener mAddRemoveTemplateHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }

        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            hideProgressBar();
            if (result.getBoolean()) {
                // Refresh the GUI -- setting the last template as the active tab
                try {
                    selectTemplate(getCol().getModels().get(mModelId).getJSONArray("tmpls").length());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else if (result.getString() != null && result.getString().equals("removeTemplateFailed")) {
                // Failed to remove template
                String message = getResources().getString(R.string.card_template_editor_would_delete_note);
                UIUtils.showThemedToast(CardTemplateEditor.this, message, false);
            } else {
                // RuntimeException occurred
                setResult(RESULT_CANCELED);
                finishWithoutAnimation();
            }
        }

        @Override
        public void onCancelled() {
            hideProgressBar();
        }
    };

    public enum ChangeType { ADD, DELETE }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_template_editor_activity);
        // Load the args either from the intent or savedInstanceState bundle
        if (savedInstanceState == null) {
            // get model id
            mModelId = getIntent().getLongExtra("modelId", -1L);
            mTemplateChanges = null;
            if (mModelId == -1) {
                Timber.e("CardTemplateEditor :: no model ID was provided");
                finishWithoutAnimation();
                return;
            }
            // get id for currently edited note (optional)
            mNoteId = getIntent().getLongExtra("noteId", -1L);
            // get id for currently edited template (optional)
            mOrdId = getIntent().getIntExtra("ordId", -1);
        } else {
            mModelId = savedInstanceState.getLong("modelId");
            mNoteId = savedInstanceState.getLong("noteId");
            mOrdId = savedInstanceState.getInt("ordId");
            try {
                mModelBackup = new JSONObject(savedInstanceState.getString("modelBackup"));
            } catch (JSONException e) {
                Timber.e(e, "Malformed model in savedInstanceState");
            }
        }

        // Disable the home icon
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        startLoadingCollection();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mModelBackup != null) {
            outState.putString("modelBackup", mModelBackup.toString());
        }
        outState.putLong("modelId", mModelId);
        outState.putLong("noteId", mNoteId);
        outState.putLong("ordId", mOrdId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (modelHasChanged()) {
            showDiscardChangesDialog();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showProgressBar() {
        super.showProgressBar();
        findViewById(R.id.progress_description).setVisibility(View.VISIBLE);
        findViewById(R.id.fragment_parent).setVisibility(View.INVISIBLE);
    }

    @Override
    public void hideProgressBar() {
        super.hideProgressBar();
        findViewById(R.id.progress_description).setVisibility(View.INVISIBLE);
        findViewById(R.id.fragment_parent).setVisibility(View.VISIBLE);
    }

    /**
     * Callback used to finish initializing the activity after the collection has been correctly loaded
     * @param col Collection which has been loaded
     */
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mTemplateAdapter = getNewTemplatePagerAdapter(getSupportFragmentManager());
        mTemplateAdapter.setModel(col.getModels().get(mModelId));
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mTemplateAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(final int position, final float v, final int i2) { /* do nothing */ }

            @Override
            public void onPageSelected(final int position) {
                CardTemplateFragment fragment = (CardTemplateFragment) mTemplateAdapter.instantiateItem(mViewPager, position);
                if (fragment != null) {
                    fragment.updateCss();
                }
            }

            @Override
            public void onPageScrollStateChanged(final int position) { /* do nothing */ }
        });
        mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
        // Set activity title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_activity_template_editor);
            getSupportActionBar().setSubtitle(col.getModels().get(mModelId).optString("name"));
        }
        // Make backup of the model for cancellation purposes
        try {
            if (mModelBackup == null) {
                mModelBackup = new JSONObject(col.getModels().get(mModelId).toString());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", mModelId);

        // Set the tab to the current template if an ord id was provided
        if (mOrdId != -1) {
            mViewPager.setCurrentItem(mOrdId);
        }
    }

    public boolean modelHasChanged() {
        JSONObject newModel = getCol().getModels().get(mModelId);
        return mModelBackup != null && !mModelBackup.toString().equals(newModel.toString());
    }

    private void showDiscardChangesDialog() {
        new MaterialDialog.Builder(this)
                .content(R.string.discard_unsaved_changes)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> {
                    Timber.i("TemplateEditor:: OK button pressed to confirm discard changes");
                    getCol().getModels().update(CardTemplateEditor.this.mModelBackup);
                    getCol().getModels().flush();
                    getCol().reset();
                    finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                })
                .build().show();
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Refresh list of templates and select position
     * @param idx index of template to select
     */
    public void selectTemplate(int idx) {
        // invalidate all existing fragments
        mTemplateAdapter.notifyChangeInPosition(1);
        // notify of new data set
        mTemplateAdapter.notifyDataSetChanged();
        // reload the list of tabs
        mSlidingTabLayout.setViewPager(mViewPager);
        // select specified tab
        mViewPager.setCurrentItem(idx);
    }


    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------


    protected TemplatePagerAdapter getNewTemplatePagerAdapter(FragmentManager fm) {
        return new TemplatePagerAdapter(fm);
    }


    /**
     * A {@link androidx.fragment.app.FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the tabs.
     */
    public class TemplatePagerAdapter extends FragmentPagerAdapter {
        private JSONObject mModel;
        private long baseId = 0;

        public TemplatePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        //this is called when notifyDataSetChanged() is called
        @Override
        public int getItemPosition(Object object) {
            // refresh all tabs when data set changed
            return PagerAdapter.POSITION_NONE;
        }

        @Override
        public Fragment getItem(int position) {
            return CardTemplateFragment.newInstance(position, mModelId, mNoteId);
        }

        @Override
        public long getItemId(int position) {
            // give an ID different from position when position has been changed
            return baseId + position;
        }

        @Override
        public int getCount() {
            try {
                return mModel.getJSONArray("tmpls").length();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }


        @Override
        public CharSequence getPageTitle(int position) {
            try {
                return mModel.getJSONArray("tmpls").getJSONObject(position).getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Notify that the position of a fragment has been changed.
         * Create a new ID for each position to force recreation of the fragment
         * @see <a href="http://stackoverflow.com/questions/10396321/remove-fragment-page-from-viewpager-in-android/26944013#26944013">stackoverflow</a>
         * @param n number of items which have been changed
         */
        public void notifyChangeInPosition(int n) {
            // shift the ID returned by getItemId outside the range of all previous fragments
            baseId += getCount() + n;
        }

        public void setModel(JSONObject model) {
            mModel = model;
        }
    }


    public static class CardTemplateFragment extends Fragment{
        private EditText mFront;
        private EditText mCss;
        private EditText mBack;
        private JSONObject mModel;
        public static CardTemplateFragment newInstance(int position, long modelId, long noteId) {
            CardTemplateFragment f = new CardTemplateFragment();
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putLong("modelId",modelId);
            args.putLong("noteId",noteId);
            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View mainView = inflater.inflate(R.layout.card_template_editor_item, container, false);
            final int position = getArguments().getInt("position");
            try {
                // Load template
                long mid = getArguments().getLong("modelId");
                mModel = ((AnkiActivity) getActivity()).getCol().getModels().get(mid);
                final JSONArray tmpls = mModel.getJSONArray("tmpls");
                final JSONObject template = tmpls.getJSONObject(position);
                // Load EditText Views
                mFront = ((EditText) mainView.findViewById(R.id.front_edit));
                mCss = ((EditText) mainView.findViewById(R.id.styling_edit));
                mBack = ((EditText) mainView.findViewById(R.id.back_edit));
                // Set EditText content
                mFront.setText(template.getString("qfmt"));
                mCss.setText(mModel.getString("css"));
                mBack.setText(template.getString("afmt"));
                // Set text change listeners
                TextWatcher templateEditorWatcher = new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable arg0) {
                        try {
                            template.put("qfmt", mFront.getText());
                            template.put("afmt", mBack.getText());
                            mModel.put("css", mCss.getText());
                            tmpls.put(position, template);
                            mModel.put("tmpls", tmpls);
                        } catch (JSONException e) {
                            Timber.e(e, "Could not update card template");
                        }
                    }
                    @Override
                    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { /* do nothing */ }
                    @Override
                    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { /* do nothing */ }
                };
                mFront.addTextChangedListener(templateEditorWatcher);
                mCss.addTextChangedListener(templateEditorWatcher);
                mBack.addTextChangedListener(templateEditorWatcher);
                // Enable menu
                setHasOptionsMenu(true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return mainView;
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        private void updateCss() {
            if (mCss != null && mModel!= null) {
                try {
                    mCss.setText(mModel.getString("css"));
                } catch (JSONException e) {
                    // do nothing
                }
            }
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.card_template_editor, menu);
            super.onCreateOptionsMenu(menu, inflater);
        }


        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            final Collection col = ((AnkiActivity) getActivity()).getCol();
            final JSONObject model = getModel();
            switch (item.getItemId()) {
                case R.id.action_add:
                    Timber.i("CardTemplateEditor:: Add template button pressed");
                    addNewTemplateWithCheck(getModel());
                    return true;
                case R.id.action_delete: {
                    Timber.i("CardTemplateEditor:: Delete template button pressed");
                    Resources res = getResources();
                    int position = getArguments().getInt("position");
                    try {
                        JSONArray tmpls = model.getJSONArray("tmpls");
                        final JSONObject template = tmpls.getJSONObject(position);
                        // Don't do anything if only one template
                        if (tmpls.length() < 2) {
                            UIUtils.showThemedToast(getActivity(), res.getString(R.string.card_template_editor_cant_delete), false);
                            return true;
                        }
                        // Show confirmation dialog
                        int numAffectedCards = col.getModels().tmplUseCount(model, position);
                        confirmDeleteCards(template, model, numAffectedCards);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }
                case R.id.action_preview: {
                    Timber.i("CardTemplateEditor:: Preview model button pressed");
                    // Save the model if necessary
                    if (modelHasChanged()) {
                        col.getModels().save(model, false);
                    }
                    // Create intent for the previewer and add some arguments
                    Intent i = new Intent(getActivity(), Previewer.class);
                    int pos = getArguments().getInt("position");
                    long cid;
                    if (getArguments().getLong("noteId") != -1L && pos <
                            col.getNote(getArguments().getLong("noteId")).cards().size()) {
                        // Give the card ID if we started from an actual note and it has a card generated in this pos
                        cid = col.getNote(getArguments().getLong("noteId")).cards().get(pos).getId();
                    } else {
                        // Otherwise create a dummy card to show the effect of formatting
                        Card dummyCard = getDummyCard();
                        if (dummyCard != null) {
                            cid = dummyCard.getId();
                        } else {
                            UIUtils.showSimpleSnackbar(getActivity(), R.string.invalid_template, false);
                            return true;
                        }
                    }
                    // Launch intent
                    i.putExtra("cardList", new long[] {cid});
                    i.putExtra("index", 0);
                    startActivityForResult(i, REQUEST_PREVIEWER);
                    return true;
                }
                case R.id.action_confirm:
                    Timber.i("CardTemplateEditor:: Save model button pressed");
                    if (modelHasChanged()) {
                        // regenerate the cards of the model
                        DeckTask.TaskData args = new DeckTask.TaskData(new Object[] {model});
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_MODEL, mSaveModelAndExitHandler, args);
                    } else {
                        ((AnkiActivity) getActivity()).finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                    }

                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        /**
         * Get a dummy card
         * @return
         */
        private Card getDummyCard() {
            Timber.d("Creating dummy note");
            JSONObject model = getCol().getModels().get(getArguments().getLong("modelId"));
            Note n =getCol().newNote(model);
            ArrayList<String> fieldNames = getCol().getModels().fieldNames(model);
            for (int i = 0; i < fieldNames.size(); i++) {
                n.setField(i, fieldNames.get(i));
            }
            n.addTag(DUMMY_TAG);
            getCol().addNote(n);
            if (n.cards().size() <= getArguments().getInt("position")) {
                return null;
            }
            return getCol().getCard(n.cards().get(getArguments().getInt("position")).getId());
        }

        private void deleteDummyCards() {
            // TODO: make into an async task
            List<Long> remnantNotes = getCol().findNotes("tag:" + DUMMY_TAG);
            if (remnantNotes.size() > 0) {
                long[] nids = new long[remnantNotes.size()];
                for (int i = 0; i < remnantNotes.size(); i++) {
                    nids[i] = remnantNotes.get(i);
                }
                getCol().remNotes(nids);
                getCol().save();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            deleteDummyCards();
        }

        /* Used for updating the collection when a model has been edited */
        private DeckTask.TaskListener mSaveModelAndExitHandler = new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                ((AnkiActivity) getActivity()).showProgressBar();
                final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }

            @Override
            public void onPostExecute(DeckTask.TaskData result) {
                if (result.getBoolean()) {
                    getActivity().setResult(RESULT_OK);
                    ((AnkiActivity) getActivity()).finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                } else {
                    // RuntimeException occurred
                    getActivity().setResult(RESULT_CANCELED);
                    ((AnkiActivity) getActivity()).finishWithoutAnimation();
                }
            }
        };

        private boolean modelHasChanged() {
            return ((CardTemplateEditor) getActivity()).modelHasChanged();
        }

        /**
         * Load the model from the collection
         * @return the model we are editing
         */
        private JSONObject getModel() {
            long mid = getArguments().getLong("modelId");
            return ((AnkiActivity) getActivity()).getCol().getModels().get(mid);
        }

        /**
         * Get the collection
         * @return
         */
        private Collection getCol() {
            return ((AnkiActivity) getActivity()).getCol();
        }

        /**
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param model model to remove from
         * @param numAffectedCards number of cards which will be affected
         */
        private void confirmDeleteCards(final JSONObject tmpl, final JSONObject model,  int numAffectedCards) {
            ConfirmationDialog d = new ConfirmationDialog();
            Resources res = getResources();
            String msg = String.format(res.getQuantityString(R.plurals.card_template_editor_confirm_delete,
                            numAffectedCards), numAffectedCards, tmpl.optString("name"));
            d.setArgs(msg);
            Runnable confirm = new Runnable() {
                @Override
                public void run() {
                    deleteTemplateWithCheck(tmpl, model);
                }
            };
            d.setConfirm(confirm);
            ((AnkiActivity) getActivity()).showDialogFragment(d);
        }

        /**
         * Delete tmpl from model, asking user to confirm again if it's going to require a full sync
         *
         * @param tmpl template to remove
         * @param model model to remove from
         */
        private void deleteTemplateWithCheck(final JSONObject tmpl, final JSONObject model) {
            try {
                ((CardTemplateEditor) getActivity()).getCol().modSchema(true);
                deleteTemplate(tmpl, model);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
                Runnable confirm = new Runnable() {
                    @Override
                    public void run() {
                        deleteTemplate(tmpl, model);
                    }
                };
                d.setConfirm(confirm);
                ((AnkiActivity) getActivity()).showDialogFragment(d);
            }
        }

        /**
         * Launch background task to delete tmpl from model
         * @param tmpl template to remove
         * @param model model to remove from
         */
        private void deleteTemplate(JSONObject tmpl, JSONObject model) {
            CardTemplateEditor activity = ((CardTemplateEditor) getActivity());
            activity.getCol().modSchemaNoCheck();
            Object [] args = new Object[] {model, tmpl};
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REMOVE_TEMPLATE,
                    activity.mAddRemoveTemplateHandler,  new DeckTask.TaskData(args));
            activity.dismissAllDialogFragments();
        }

        /**
         * Add new template to model, asking user to confirm if it's going to require a full sync
         *
         * @param model model to add new template to
         */
        private void addNewTemplateWithCheck(final JSONObject model) {
            try {
                ((CardTemplateEditor) getActivity()).getCol().modSchema(true);
                addNewTemplate(model);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
                Runnable confirm = new Runnable() {
                    @Override
                    public void run() {
                        addNewTemplate(model);
                    }
                };
                d.setConfirm(confirm);
                ((AnkiActivity) getActivity()).showDialogFragment(d);
            }
        }


        /**
         * Launch background task to add new template to model
         * @param model model to add new template to
         */
        private void addNewTemplate(JSONObject model) {
            CardTemplateEditor activity = ((CardTemplateEditor) getActivity());
            activity.getCol().modSchemaNoCheck();
            Models mm = activity.getCol().getModels();
            // Build new template
            JSONObject newTemplate;
            try {
                int oldPosition = getArguments().getInt("position");
                JSONArray templates = model.getJSONArray("tmpls");
                JSONObject oldTemplate = templates.getJSONObject(oldPosition);
                newTemplate = mm.newTemplate(newCardName(templates));
                // Set up question & answer formats
                newTemplate.put("qfmt", oldTemplate.get("qfmt"));
                newTemplate.put("afmt", oldTemplate.get("afmt"));
                // Reverse the front and back if only one template
                if (templates.length() == 1) {
                    flipQA(newTemplate);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            // Add new template to the current model via AsyncTask
            Object [] args = new Object[] {model, newTemplate};
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_TEMPLATE,
                    activity.mAddRemoveTemplateHandler,  new DeckTask.TaskData(args));
            activity.dismissAllDialogFragments();
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        private void flipQA (JSONObject template) {
            try {
                String qfmt = template.getString("qfmt");
                String afmt = template.getString("afmt");
                Matcher m = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt);
                if (!m.find()) {
                    template.put("qfmt", afmt.replace("{{FrontSide}}",""));
                } else {
                    template.put("qfmt",m.group(2).trim());
                }
                template.put("afmt","{{FrontSide}}\n\n<hr id=answer>\n\n" + qfmt);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Get name for new template
         * @param templates array of templates which is being added to
         * @return name for new template
         */
        private String newCardName(JSONArray templates) {
            String name;
            // Start by trying to set the name to "Card n" where n is the new num of templates
            int n = templates.length() + 1;
            // If the starting point for name already exists, iteratively increase n until we find a unique name
            while (true) {
                // Get new name
                name = "Card " + Integer.toString(n);
                // Cycle through all templates checking if new name exists
                boolean exists = false;
                for (int i = 0; i < templates.length(); i++) {
                    try {
                        exists = exists || name.equals(templates.getJSONObject(i).getString("name"));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!exists) {
                    break;
                }
                n+=1;
            }
            return name;
        }
    }


    /**
     * Template deletes shift card ordinals in the database. To operate without saving, we must keep track to apply in order.
     * In addition, we don't want to persist a template add just to delete it later, so we combine those if they happen
     */
    public void addTemplateChange(ChangeType type, int ordinal) {
        Timber.d("addTemplateChange() type %s for ordinal %s", type, ordinal);
        if (mTemplateChanges == null) {
            mTemplateChanges = new ArrayList<>();
        }
        Object[] change = new Object[] {ordinal, type};

        // If we are deleting something we added but have not saved, edit it out of the change list
        if (type == ChangeType.DELETE) {
            int ordinalAdjustment = 0;
            for (int i = mTemplateChanges.size() - 1; i >= 0; i--) {
                Object[] oldChange = mTemplateChanges.get(i );
                switch ((ChangeType)oldChange[1]) {
                    case DELETE: {
                        // Deleting an ordinal at or below us? Adjust our comparison basis...
                        if ((Integer)oldChange[0] - ordinalAdjustment <= ordinal) {
                            ordinalAdjustment++;
                            continue;
                        }
                        break;
                    }
                    case ADD:
                        if (ordinal == (Integer)oldChange[0] - ordinalAdjustment) {
                            // Deleting something we added this session? Edit it out via compaction
                            compactTemplateChanges((Integer)oldChange[0]);
                            return;
                        }
                        break;
                    default:
                        break;

                }
            }
        }

        Timber.d("addTemplateChange() added ord/type: %s/%s", change[0], change[1]);
        mTemplateChanges.add(change);
    }


    public @NonNull ArrayList<Object[]> getTemplateChanges() {
        if (mTemplateChanges == null) {
            mTemplateChanges = new ArrayList<>();
        }
        return mTemplateChanges;
    }


    /**
     * Scan the sequence of template add/deletes, looking for the given ordinal.
     * When found, purge that ordinal and shift future changes down if they had ordinals higher than the one purged
     */
    private void compactTemplateChanges(int addedOrdinalToDelete) {

        Timber.d("compactTemplateChanges() merge/purge add/delete ordinal added as %s", addedOrdinalToDelete);
        boolean postChange = false;
        int ordinalAdjustment = 0;
        for (int i = 0; i < mTemplateChanges.size(); i++) {
            Object[] change = mTemplateChanges.get(i);
            int ordinal = (Integer)change[0];
            ChangeType changeType = (ChangeType)change[1];
            Timber.d("compactTemplateChanges() examining change entry %s / %s", ordinal, changeType);

            // Only make adjustments after the ordinal we want to delete was added
            if (!postChange) {
                if (ordinal == addedOrdinalToDelete && changeType == ChangeType.ADD) {
                    Timber.d("compactTemplateChanges() found our entry at index %s", i);
                    // Remove this entry to start compaction, then fix up the loop counter since we altered size
                    postChange = true;
                    mTemplateChanges.remove(i);
                    i--;
                }
                continue;
            }

            // We compact all deletes with higher ordinals, so any delete is below us: shift our comparison basis
            if (changeType == ChangeType.DELETE) {
                ordinalAdjustment++;
                Timber.d("compactTemplateChanges() delete affecting purged template, shifting basis, adj: %s", ordinalAdjustment);
            }

            // If following ordinals were higher, we move them as part of compaction
            if ((ordinal + ordinalAdjustment) > addedOrdinalToDelete) {
                Timber.d("compactTemplateChanges() shifting later/higher ordinal down");
                change[0] = --ordinal;
            }
        }
    }


    /** Clear any temp model files saved into internal cache directory */
    public static int clearTempModelFiles(@NonNull Context context) {
        int deleteCount = 0;
        for (File c : context.getCacheDir().listFiles()) {
            if (c.getAbsolutePath().endsWith("json") && c.getAbsolutePath().contains("editedTemplate")) {
                if (!c.delete()) {
                    Timber.w("Unable to delete temp file %s", c.getAbsolutePath());
                } else {
                    deleteCount++;
                    Timber.d("Deleted temp model file %s", c.getAbsolutePath());
                }
            }
        }
        return deleteCount;
    }


    /**
     * Save the current model to a temp file in the application internal cache directory
     * @return String representing the absolute path of the saved file, or null if there was a problem
     */
    public static @Nullable String saveTempModel(@NonNull Context context, @NonNull JSONObject tempModel) {
        Timber.d("saveTempModel() saving tempModel");
        File tempModelFile;
        try (ByteArrayInputStream source = new ByteArrayInputStream(tempModel.toString().getBytes())) {
            tempModelFile = File.createTempFile("editedTemplate", ".json", context.getCacheDir());
            CompatHelper.getCompat().copyFile(source, tempModelFile.getAbsolutePath());
        } catch (IOException ioe) {
            Timber.e(ioe, "Unable to create+write temp file for model");
            return null;
        }
        return tempModelFile.getAbsolutePath();
    }


    /**
     * Get the model temporarily saved into the file represented by the given path
     * @return JSONObject holding the model, or null if there was a problem
     */
    public static @Nullable JSONObject getTempModel(@NonNull String tempModelFileName) {
        Timber.d("getTempModel() fetching tempModel %s", tempModelFileName);
        try (ByteArrayOutputStream target = new ByteArrayOutputStream()) {
            CompatHelper.getCompat().copyFile(tempModelFileName, target);
            return new JSONObject(target.toString());
        } catch (IOException | JSONException e) {
            Timber.e(e, "Unable to read+parse tempModel from file %s", tempModelFileName);
            return null;
        }
    }
}
