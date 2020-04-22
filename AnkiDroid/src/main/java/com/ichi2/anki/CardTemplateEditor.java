/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
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
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Models;
import com.ichi2.ui.SlidingTabLayout;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;


/**
 * Allows the user to view the template for the current note type
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes"})
public class CardTemplateEditor extends AnkiActivity {
    private TemplatePagerAdapter mTemplateAdapter;
    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private TemporaryModel mTempModel;
    private long mModelId;
    private long mNoteId;
    private int mOrdId;
    private static final int REQUEST_PREVIEWER = 0;
    private static final int REQUEST_CARD_BROWSER_APPEARANCE = 1;
    private static final String DUMMY_TAG = "DUMMY_NOTE_TO_DELETE_x0-90-fa";


    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------

    /* Used for updating the collection when a reverse card is added or a template is deleted */
    private CollectionTask.TaskListener mAddRemoveTemplateHandler = new CollectionTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }

        @Override
        public void onPostExecute(CollectionTask.TaskData result) {
            hideProgressBar();
            if (result.getBoolean()) {
                // Refresh the GUI -- setting the last template as the active tab
                selectTemplate(getCol().getModels().get(mModelId).getJSONArray("tmpls").length());
            } else if (result.getString() != null && "removeTemplateFailed".equals(result.getString())) {
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
            mTempModel = TemporaryModel.fromBundle(savedInstanceState);
        }

        // Disable the home icon
        enableToolbar();
        startLoadingCollection();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putAll(getTempModel().toBundle());
        outState.putLong("modelId", mModelId);
        outState.putLong("noteId", mNoteId);
        outState.putInt("ordId", mOrdId);
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
        // The first time the activity loads it has a model id but no edits yet, so no edited model
        // take the passed model id load it up for editing
        if (getTempModel() == null) {
            mTempModel = new TemporaryModel(new JSONObject(col.getModels().get(mModelId).toString()));
            //Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
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
            getSupportActionBar().setSubtitle(mTempModel.getModel().optString("name"));
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", mModelId);

        // Set the tab to the current template if an ord id was provided
        if (mOrdId != -1) {
            mViewPager.setCurrentItem(mOrdId);
        }
    }

    public boolean modelHasChanged() {
        JSONObject oldModel = getCol().getModels().get(mModelId);
        return getTempModel() != null && !getTempModel().getModel().toString().equals(oldModel.toString());
    }


    public TemporaryModel getTempModel() {
        return mTempModel;
    }

    @VisibleForTesting
    public MaterialDialog showDiscardChangesDialog() {
        MaterialDialog discardDialog = DiscardChangesDialog.getDefault(this)
                .onPositive((dialog, which) -> {
                    Timber.i("TemplateEditor:: OK button pressed to confirm discard changes");
                    // Clear the edited model from any cache files, and clear it from this objects memory to discard changes
                    TemporaryModel.clearTempModelFiles();
                    mTempModel = null;
                    finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                })
                .build();
        discardDialog.show();
        return discardDialog;
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

    // Testing Android apps is hard, and pager adapters in fragments is nearly impossible.
    // In order to make this object testable we have to allow for some plumbing pass through
    @VisibleForTesting
    protected TemplatePagerAdapter getNewTemplatePagerAdapter(FragmentManager fm) {
        return new TemplatePagerAdapter(fm);
    }


    /**
     * A {@link androidx.fragment.app.FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the tabs.
     */
    public class TemplatePagerAdapter extends FragmentPagerAdapter {
        private long baseId = 0;

        public TemplatePagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        //this is called when notifyDataSetChanged() is called
        @Override
        public int getItemPosition(Object object) {
            // refresh all tabs when data set changed
            return PagerAdapter.POSITION_NONE;
        }

        @Override
        public Fragment getItem(int position) {
            return CardTemplateFragment.newInstance(position, mNoteId);
        }

        @Override
        public long getItemId(int position) {
            // give an ID different from position when position has been changed
            return baseId + position;
        }

        @Override
        public int getCount() {
            return getTempModel().getTemplateCount();
        }


        @Override
        public CharSequence getPageTitle(int position) {
            return getTempModel().getTemplate(position).getString("name");
        }

        /**
         * Notify that the position of a fragment has been changed.
         * Create a new ID for each position to force recreation of the fragment
         * TODO (added years later) examine if this is still needed - may be able to simplify/delete
         * @see <a href="http://stackoverflow.com/questions/10396321/remove-fragment-page-from-viewpager-in-android/26944013#26944013">stackoverflow</a>
         * @param n number of items which have been changed
         */
        public void notifyChangeInPosition(int n) {
            // shift the ID returned by getItemId outside the range of all previous fragments
            baseId += getCount() + n;
        }
    }


    public static class CardTemplateFragment extends Fragment {
        private EditText mFront;
        private EditText mCss;
        private EditText mBack;
        private CardTemplateEditor mTemplateEditor;

        public static CardTemplateFragment newInstance(int position, long noteId) {
            CardTemplateFragment f = new CardTemplateFragment();
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putLong("noteId",noteId);
            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Storing a reference to the templateEditor allows us to use member variables
            mTemplateEditor = (CardTemplateEditor)getActivity();
            View mainView = inflater.inflate(R.layout.card_template_editor_item, container, false);
            final int position = getArguments().getInt("position");
            TemporaryModel tempModel = mTemplateEditor.getTempModel();
            // Load template
            final JSONObject template = tempModel.getTemplate(position);
            // Load EditText Views
            mFront = ((EditText) mainView.findViewById(R.id.front_edit));
            mCss = ((EditText) mainView.findViewById(R.id.styling_edit));
            mBack = ((EditText) mainView.findViewById(R.id.back_edit));
            // Set EditText content
            mFront.setText(template.getString("qfmt"));
            mCss.setText(tempModel.getCss());
            mBack.setText(template.getString("afmt"));
            // Set text change listeners
            TextWatcher templateEditorWatcher = new TextWatcher() {
                @Override
                public void afterTextChanged(Editable arg0) {
                    template.put("qfmt", mFront.getText());
                    template.put("afmt", mBack.getText());
                    mTemplateEditor.getTempModel().updateCss(mCss.getText().toString());
                    mTemplateEditor.getTempModel().updateTemplate(position, template);
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
            return mainView;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            // Clear our activity reference so we don't memory leak
            mTemplateEditor = null;
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        private void updateCss() {
            if (mCss != null && mTemplateEditor.getTempModel() != null) {
                mCss.setText(mTemplateEditor.getTempModel().getCss());
            }
        }

        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.card_template_editor, menu);

            if (mTemplateEditor.getTempModel().getModel().getInt("type") == Consts.MODEL_CLOZE) {
                Timber.d("Editing cloze model, disabling add/delete card template functionality");
                menu.findItem(R.id.action_add).setVisible(false);
            }

            // It is invalid to delete if there is only one card template, remove the option from UI
            if (mTemplateEditor.getTempModel().getTemplateCount() < 2) {
                menu.findItem(R.id.action_delete).setVisible(false);
            }

            super.onCreateOptionsMenu(menu, inflater);
        }


        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            final Collection col = mTemplateEditor.getCol();
            TemporaryModel tempModel = mTemplateEditor.getTempModel();
            switch (item.getItemId()) {
                case R.id.action_add:
                    Timber.i("CardTemplateEditor:: Add template button pressed");
                    // TODO in Anki Desktop, they have a popup first with "This will create %d cards. Proceed?"
                    //      AnkiDroid never had this so it isn't a regression but it is a miss for AnkiDesktop parity
                    addNewTemplateWithCheck(tempModel.getModel());
                    return true;
                case R.id.action_delete: {
                    Timber.i("CardTemplateEditor:: Delete template button pressed");
                    Resources res = getResources();
                    int position = getArguments().getInt("position");
                    final JSONObject template = tempModel.getTemplate(position);
                    // Don't do anything if only one template
                    if (tempModel.getTemplateCount() < 2) {
                        mTemplateEditor.showSimpleMessageDialog(res.getString(R.string.card_template_editor_cant_delete));
                        return true;
                    }

                    if (deletionWouldOrphanNote(col, tempModel, position)) {
                        return true;
                    }

                    // Show confirmation dialog
                    int numAffectedCards = col.getModels().tmplUseCount(tempModel.getModel(), position);
                    confirmDeleteCards(template, tempModel.getModel(), numAffectedCards);
                    return true;
                }
                case R.id.action_preview: {
                    Timber.i("CardTemplateEditor:: Preview model button pressed");
                    // Create intent for the previewer and add some arguments
                    Intent i = new Intent(mTemplateEditor, CardTemplatePreviewer.class);
                    int pos = getArguments().getInt("position");
                    if (getArguments().getLong("noteId") != -1L && pos <
                            col.getNote(getArguments().getLong("noteId")).cards().size()) {
                        // Give the card ID if we started from an actual note and it has a card generated in this pos
                        i.putExtra("cardList", new long[] { col.getNote(getArguments().getLong("noteId")).cards().get(pos).getId() });
                        i.putExtra("index", 0);
                    } else {
                        // Otherwise send the template index but no cardList, and Previewer will show a blank to preview formatting
                        i.putExtra("index", pos);
                    }
                    // Save the model and pass the filename if updated
                    tempModel.setEditedModelFileName(TemporaryModel.saveTempModel(mTemplateEditor, tempModel.getModel()));
                    i.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModel.getEditedModelFileName());
                    startActivityForResult(i, REQUEST_PREVIEWER);
                    return true;
                }
                case R.id.action_confirm:
                    Timber.i("CardTemplateEditor:: Save model button pressed");
                    if (modelHasChanged()) {
                        tempModel.saveToDatabase(mSaveModelAndExitHandler);
                    } else {
                        mTemplateEditor.finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                    }

                    return true;
                case R.id.action_card_browser_appearance:
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed");
                    launchCardBrowserAppearance(getCurrentTemplate());
                default:
                    return super.onOptionsItemSelected(item);
            }
        }


        private void launchCardBrowserAppearance(JSONObject currentTemplate) {
            Context context = AnkiDroidApp.getInstance().getBaseContext();
            if (context == null) {
                //Catch-22, we can't notify failure as there's no context. Shouldn't happen anyway
                Timber.w("Context was null - couldn't launch Card Browser Appearance window");
                return;
            }
            Intent browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate);
            startActivityForResult(browserAppearanceIntent, REQUEST_CARD_BROWSER_APPEARANCE);
        }


        @CheckResult @NonNull
        private JSONObject getCurrentTemplate() {
            int currentCardTemplateIndex = getCurrentCardTemplateIndex();
            return (JSONObject) mTemplateEditor.getTempModel().getModel().getJSONArray("tmpls").get(currentCardTemplateIndex);
        }


        /**
         * @return The index of the card template which is currently referred to by the fragment
         */
        @CheckResult
        private int getCurrentCardTemplateIndex() {
            //COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.
            return getArguments().getInt("position");
        }


        private boolean deletionWouldOrphanNote(Collection col, TemporaryModel tempModel, int position) {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!tempModel.isTemplatePendingAdd(position)) {
                int[] currentDeletes = tempModel.getDeleteDbOrds(position);
                // TODO - this is a SQL query on GUI thread - should see a DeckTask conversion ideally
                if (col.getModels().getCardIdsForModel(tempModel.getModelId(), currentDeletes) == null) {

                    // It is possible but unlikely that a user has an in-memory template addition that would
                    // generate cards making the deletion safe, but we don't handle that. All users who do
                    // not already have cards generated making it safe will see this error message:
                    mTemplateEditor.showSimpleMessageDialog(getResources().getString(R.string.card_template_editor_would_delete_note));
                    return true;
                }
            }
            return false;
        }


        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CARD_BROWSER_APPEARANCE) {
                onCardBrowserAppearanceResult(resultCode, data);
                return;
            }

            if (requestCode == REQUEST_PREVIEWER) {
                TemporaryModel.clearTempModelFiles();
            }
        }


        private void onCardBrowserAppearanceResult(int resultCode, @Nullable Intent data) {
            if (resultCode != RESULT_OK) {
                Timber.i("Activity Cancelled: Card Template Browser Appearance");
                return;
            }

            CardTemplateBrowserAppearanceEditor.Result result = CardTemplateBrowserAppearanceEditor.Result.fromIntent(data);
            if (result == null) {
                Timber.w("Error processing Card Template Browser Appearance result");
                return;
            }

            Timber.i("Applying Card Template Browser Appearance result");

            JSONObject currentTemplate = getCurrentTemplate();
            result.applyTo(currentTemplate);
        }

        /* Used for updating the collection when a model has been edited */
        private CollectionTask.TaskListener mSaveModelAndExitHandler = new CollectionTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mTemplateEditor.showProgressBar();
                final InputMethodManager imm = (InputMethodManager) mTemplateEditor.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }

            @Override
            public void onPostExecute(CollectionTask.TaskData result) {
                mTemplateEditor.mTempModel = null;
                if (result.getBoolean()) {
                    mTemplateEditor.finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                } else {
                    // RuntimeException occurred
                    mTemplateEditor.finishWithoutAnimation();
                }
            }
        };

        private boolean modelHasChanged() {
            return mTemplateEditor.modelHasChanged();
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
            mTemplateEditor.showDialogFragment(d);
        }

        /**
         * Delete tmpl from model, asking user to confirm again if it's going to require a full sync
         *
         * @param tmpl template to remove
         * @param model model to remove from
         */
        private void deleteTemplateWithCheck(final JSONObject tmpl, final JSONObject model) {
            try {
                mTemplateEditor.getCol().modSchema();
                deleteTemplate(tmpl, model);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
                Runnable confirm = () -> deleteTemplate(tmpl, model);
                Runnable cancel = () -> mTemplateEditor.dismissAllDialogFragments();
                d.setConfirm(confirm);
                d.setCancel(cancel);
                mTemplateEditor.showDialogFragment(d);
            }
        }

        /**
         * @param tmpl template to remove
         * @param model model to remove from
         */
        private void deleteTemplate(JSONObject tmpl, JSONObject model) {
            JSONArray oldTemplates = model.getJSONArray("tmpls");
            JSONArray newTemplates = new JSONArray();
            for (int i = 0; i < oldTemplates.length(); i++) {
                JSONObject possibleMatch = oldTemplates.getJSONObject(i);
                if (possibleMatch.getInt("ord") != tmpl.getInt("ord")) {
                    newTemplates.put(possibleMatch);
                } else {
                    Timber.d("deleteTemplate() found match - removing template with ord %s", possibleMatch.getInt("ord"));
                    mTemplateEditor.getTempModel().removeTemplate(possibleMatch.getInt("ord"));
                }
            }
            model.put("tmpls", newTemplates);
            Models._updateTemplOrds(model);
            mTemplateEditor.selectTemplate(model.length());

            if (getActivity() != null) {
                ((CardTemplateEditor) getActivity()).dismissAllDialogFragments();
            }
        }

        /**
         * Add new template to model, asking user to confirm if it's going to require a full sync
         *
         * @param model model to add new template to
         */
        private void addNewTemplateWithCheck(final JSONObject model) {
            try {
                mTemplateEditor.getCol().modSchema();
                Timber.d("addNewTemplateWithCheck() called and no CMSE?");
                addNewTemplate(model);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
                Runnable confirm = () -> addNewTemplate(model);
                d.setConfirm(confirm);
                mTemplateEditor.showDialogFragment(d);
            }
        }


        /**
         * Add new template to a given model
         * @param model model to add new template to
         */
        private void addNewTemplate(JSONObject model) {
            // Build new template
            JSONObject newTemplate;
            int oldPosition = getArguments().getInt("position");
            JSONArray templates = model.getJSONArray("tmpls");
            JSONObject oldTemplate = templates.getJSONObject(oldPosition);
            newTemplate = Models.newTemplate(newCardName(templates));
            // Set up question & answer formats
            newTemplate.put("qfmt", oldTemplate.get("qfmt"));
            newTemplate.put("afmt", oldTemplate.get("afmt"));
            // Reverse the front and back if only one template
            if (templates.length() == 1) {
                flipQA(newTemplate);
            }

            int lastExistingOrd = templates.getJSONObject(templates.length() - 1).getInt("ord");
            Timber.d("addNewTemplate() lastExistingOrd was %s", lastExistingOrd);
            newTemplate.put("ord", lastExistingOrd + 1);
            templates.put(newTemplate);
            mTemplateEditor.getTempModel().addNewTemplate(newTemplate);
            mTemplateEditor.selectTemplate(model.length());
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        private void flipQA (JSONObject template) {
            String qfmt = template.getString("qfmt");
            String afmt = template.getString("afmt");
            Matcher m = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt);
            if (!m.find()) {
                template.put("qfmt", afmt.replace("{{FrontSide}}",""));
            } else {
                template.put("qfmt",m.group(2).trim());
            }
            template.put("afmt","{{FrontSide}}\n\n<hr id=answer>\n\n" + qfmt);
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
                    exists = exists || name.equals(templates.getJSONObject(i).getString("name"));
                }
                if (!exists) {
                    break;
                }
                n+=1;
            }
            return name;
        }
    }
}
