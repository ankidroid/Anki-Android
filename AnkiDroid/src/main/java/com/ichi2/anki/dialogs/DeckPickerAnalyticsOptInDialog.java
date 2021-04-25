/****************************************************************************************
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

package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.anki.analytics.UsageAnalytics;

import androidx.annotation.NonNull;

public class DeckPickerAnalyticsOptInDialog extends AnalyticsDialogFragment {
    public static DeckPickerAnalyticsOptInDialog newInstance() {
        return new DeckPickerAnalyticsOptInDialog();
    }

    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.analytics_dialog_title))
                .content(res.getString(R.string.analytics_summ))
                .checkBoxPrompt(res.getString(R.string.analytics_title), true, null)
                .positiveText(R.string.dialog_continue)
                .onPositive((dialog, which) -> {
                    AnkiDroidApp.getSharedPrefs(getContext()).edit()
                            .putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, dialog.isPromptCheckBoxChecked())
                            .apply();
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                })
                .cancelable(true)
                .cancelListener(dialog -> ((DeckPicker) getActivity()).dismissAllDialogFragments())
                .show();
    }
}
