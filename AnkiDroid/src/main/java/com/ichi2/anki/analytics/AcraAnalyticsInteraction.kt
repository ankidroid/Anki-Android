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

package com.ichi2.anki.analytics;

import android.content.Context;

import com.google.auto.service.AutoService;

import org.acra.config.CoreConfiguration;
import org.acra.interaction.ReportInteraction;
import org.acra.util.Installation;

import java.io.File;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * This ACRA Extension sends an analytics hit during crash handling while ACRA is enabled.
 * Questions answered: "Number of ACRA reports sent", "ACRA vs Analytics count differences"
 * See <a href="https://github.com/ACRA/acra/wiki/Custom-Extensions">Custom Extensions</a>
 */
@AutoService(ReportInteraction.class)
public class AcraAnalyticsInteraction implements ReportInteraction {


    @Override
    public boolean performInteraction(@NonNull Context context, @NonNull CoreConfiguration config, @NonNull File reportFile) {
        // Send an analytics exception hit with a UUID to match
        Timber.e("ACRA handling crash, sending analytics exception report");
        UsageAnalytics.sendAnalyticsEvent("ACRA Crash Handler", "UUID " + Installation.id(context));
        return true;
    }
}
