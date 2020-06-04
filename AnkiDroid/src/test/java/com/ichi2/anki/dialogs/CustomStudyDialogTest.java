/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs;

import android.content.Intent;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.dialogs.CustomStudyDialog.CustomStudyListener;
import com.ichi2.utils.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(AndroidJUnit4.class)
public class CustomStudyDialogTest extends RobolectricTest {

    @Test
    public void learnAheadCardsRegressionTest() {
        // #6289 - Regression Test
        CustomStudyDialog args = CustomStudyDialog.newInstance(CustomStudyDialog.CUSTOM_STUDY_AHEAD, 1);

        FragmentScenario<CustomStudyDialogForTesting> scenario = getDialogScenario(args);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment(f -> {
            MaterialDialog dialog = (MaterialDialog) f.getDialog();
            assertThat(dialog, notNullValue());
            dialog.getActionButton(DialogAction.POSITIVE).callOnClick();
        });


        JSONObject customStudy = getCol().getDecks().current();
        assertThat("Custom Study should be dynamic", customStudy.getInt("dyn") == 1);
        assertThat("could not find deck: Custom study session", customStudy, notNullValue());
        customStudy.remove("id");
        customStudy.remove("mod");
        customStudy.remove("name");

        String expected = "{\"newToday\":[0,0],\"revToday\":[0,0],\"lrnToday\":[0,0],\"timeToday\":[0,0],\"collapsed\":false,\"dyn\":1,\"desc\":\"\",\"usn\":-1,\"delays\":null,\"separate\":true,\"terms\":[[\"deck:\\\"Default\\\" prop:due<=1\",99999,6]],\"resched\":true,\"return\":true}";
        assertThat(customStudy.toString(), is(expected));
    }


    @NonNull
    private FragmentScenario<CustomStudyDialogForTesting> getDialogScenario(CustomStudyDialog args) {
        FragmentScenario<CustomStudyDialogForTesting> scenario = FragmentScenario.launch(CustomStudyDialogForTesting.class, args.getArguments());

        // Pick an arbitrary easy activity
        CustomStudyActivity d = super.startActivityNormallyOpenCollectionWithIntent(CustomStudyActivity.class, new Intent());

        scenario.onFragment(f -> f.setActivity(d));
        return scenario;
    }

    private static class CustomStudyActivity extends CardTemplateBrowserAppearanceEditor implements CustomStudyListener {

        @Override
        public void onCreateCustomStudySession() {
            // Intentionally blank
        }


        @Override
        public void onExtendStudyLimits() {
            // Intentionally blank
        }
    }


    public static class CustomStudyDialogForTesting extends CustomStudyDialog {
        private AnkiActivity mAnkiActivity;

        @SuppressWarnings("WeakerAccess")
        public <T extends AnkiActivity & CustomStudyListener> void setActivity(T ankiActivity) {
            mAnkiActivity = ankiActivity;
        }


        @Override
        protected AnkiActivity getAnkiActivity() {
            return mAnkiActivity;
        }
    }
}
