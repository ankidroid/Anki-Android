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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.example.android.common.view.SlidingTabLayout;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Allows the user to view the template for the current note type
 */
public class CardTemplateEditor extends AnkiActivity {
    private TemplatePagerAdapter mTemplateAdapter;
    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private StyledProgressDialog mProgressDialog;
    private long mModelId;
    //private long mCardId;
    private boolean mChanged = false;


    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------

    /* Used for updating the collection when a reverse card is added */
    private DeckTask.TaskListener mUpdateTemplateHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog
                    .show(CardTemplateEditor.this, "", res.getString(R.string.saving_model), true);
        }

        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }

        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            // Clear progress dialog
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (IllegalArgumentException e) {
                    Log.e(AnkiDroidApp.TAG, "Card Template Editor: Error on dismissing progress dialog: " + e);
                }
            }
            if (result.getBoolean()) {
                mChanged = true;
                // Refresh the GUI -- setting the last template as the active tab
                try {
                    selectTemplate(getCol().getModels().get(mModelId).getJSONArray("tmpls").length());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else if (result.getString() != null && result.getString().equals("removeTemplateFailed")) {
                // Failed to remove template
                String message = getResources().getString(R.string.card_template_editor_would_delete_note);
                Themes.showThemedToast(CardTemplateEditor.this, message, false);
            } else {
                // RuntimeException occurred
                setResult(RESULT_CANCELED);
                finishWithoutAnimation();
            }
        }

        @Override
        public void onCancelled() {}
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(AnkiDroidApp.TAG, "CardTemplateEditor:: onCreate");
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_template_editor_activity);
        // get id for currently edited card (optional)
        mModelId = getIntent().getLongExtra("modelId", -1L);
        if (mModelId == -1) {
            Log.e(AnkiDroidApp.TAG, "CardTemplateEditor :: no model ID was provided");
            finishWithoutAnimation();
            return;
        }
        // TODO: Use card ID for preview feature
        //mCardId = getIntent().getLongExtra("cardId", -1L);

        // Disable the home icon
        getSupportActionBar().setDisplayShowHomeEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled (false);

        startLoadingCollection();
    }


    @Override
    protected void onStop() {
        // Save changes
        if (mChanged) {
            // TODO: run as AsyncTask
            getCol().getModels().save(getCol().getModels().get(mModelId), true);
            getCol().reset();
        }
        super.onStop();
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
        mTemplateAdapter = new TemplatePagerAdapter(getSupportFragmentManager());
        mTemplateAdapter.setModel(col.getModels().get(mModelId));
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mTemplateAdapter);
        mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
        // Set activity title
        setTitle(getResources().getString(R.string.title_activity_template_editor, col.getModels().get(mModelId).optString("name")));
        // Close collection opening dialog if needed
        dismissOpeningCollectionDialog();
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

    /**
     * A {@link android.support.v4.app.FragmentPagerAdapter} that returns a fragment corresponding to
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
            try {
                return CardTemplateFragment.newInstance(position, mModel.getLong("id"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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
        public static CardTemplateFragment newInstance(int position, long modelId) {
            CardTemplateFragment f = new CardTemplateFragment();
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putLong("modelId",modelId);
            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View mainView = inflater.inflate(R.layout.card_template_editor_item, container, false);
            int position = getArguments().getInt("position");
            try {
                // Load template
                long mid = getArguments().getLong("modelId");
                JSONObject model = ((AnkiActivity) getActivity()).getCol().getModels().get(mid);
                JSONObject template = model.getJSONArray("tmpls").getJSONObject(position);
                // Load EditText Views
                EditText front = ((EditText) mainView.findViewById(R.id.front_edit));
                EditText css = ((EditText) mainView.findViewById(R.id.styling_edit));
                EditText back = ((EditText) mainView.findViewById(R.id.back_edit));
                // Set EditText content
                front.setText(template.getString("qfmt"));
                css.setText(model.getString("css"));
                back.setText(template.getString("afmt"));
                // TODO: Enable editing if not built-in model
                /*if (!isStandardModel()) {
                    front.setEnabled(true);
                    css.setEnabled(true);
                    back.setEnabled(true);
                }*/
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



        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.card_template_editor, menu);
            if (isStandardModel()) {
                menu.findItem(R.id.action_delete).setVisible(false);
            }
            super.onCreateOptionsMenu(menu, inflater);
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            /*try {
                // only show option to add reverse card if one existing card and not standard model
                if (getModel().getJSONArray("tmpls").length() == 1 && !isStandardModel()) {
                    menu.findItem(R.id.action_add).setVisible(true);
                } else {
                    menu.findItem(R.id.action_add).setVisible(false);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }*/
            // TODO: Expose after making a more user-friendly interface for adding / editing card templates
            menu.findItem(R.id.action_add).setVisible(false);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add:
                    addNewTemplateWithCheck(getModel());
                    return true;
                case R.id.action_delete:
                    Resources res = getResources();
                    final Collection col = ((AnkiActivity) getActivity()).getCol();
                    int position = getArguments().getInt("position");
                    try {
                        // Get model & template
                        final JSONObject model = getModel();
                        JSONArray tmpls = model.getJSONArray("tmpls");
                        final JSONObject template = tmpls.getJSONObject(position);
                        // Don't do anything if only one template
                        if (tmpls.length() < 2) {
                            Themes.showThemedToast(getActivity(), res.getString(R.string.card_template_editor_cant_delete), false);
                            return true;
                        }
                        // Show confirmation dialog
                        int numAffectedCards = col.getModels().tmplUseCount(model, position);
                        confirmDeleteCards(template, model, numAffectedCards);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                //TODO:  Make way to temporarily save cards so that we can expose preview function
                /*case R.id.action_preview: */
                default:
                    return super.onOptionsItemSelected(item);
            }
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
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param model model to remove from
         * @param numAffectedCards number of cards which will be affected
         */
        private void confirmDeleteCards(final JSONObject tmpl, final JSONObject model,  int numAffectedCards) {
            ConfirmationDialog d = new ConfirmationDialog() {
                @Override
                public void confirm() {
                    deleteTemplateWithCheck(tmpl, model);
                }
            };
            Resources res = getResources();
            String msg = String.format(res.getQuantityString(R.plurals.card_template_editor_confirm_delete,
                            numAffectedCards), numAffectedCards, tmpl.optString("name"));
            d.setArgs(msg);
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
                ConfirmationDialog d = new ConfirmationDialog() {
                    @Override
                    public void confirm() {
                        deleteTemplate(tmpl, model);
                    }
                };
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
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
            Object [] args = new Object[] {activity.getCol(), model, tmpl};
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REMOVE_TEMPLATE,
                    activity.mUpdateTemplateHandler,  new DeckTask.TaskData(args));
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
                ConfirmationDialog d = new ConfirmationDialog() {
                    @Override
                    public void confirm() {
                        addNewTemplate(model);
                    }
                };
                d.setArgs(getResources().getString(R.string.full_sync_confirmation));
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
            Object [] args = new Object[] {activity.getCol(), model, newTemplate};
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_TEMPLATE,
                    activity.mUpdateTemplateHandler,  new DeckTask.TaskData(args));
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


        /**
         * Check if the model has the same name as any of the standard models, in which case we prevent
         * editing to protect the user from doing something stupid
         * @return whether or not the current model has same name as built-in model
         */
        public boolean isStandardModel() {
            // TODO :: Also check if the contents of the model are the same as a standard model
            final List<String> readonlyModels = Arrays.asList( "Basic",
                    "Basic (and reversed card)", "Basic (optional reversed card)", "Cloze");
            try {
                String modelName =getModel().getString("name");
                return readonlyModels.contains(modelName);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
