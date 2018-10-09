package com.ichi2.anki.analytics;

import android.content.Context;

import com.google.auto.service.AutoService;

import org.acra.config.CoreConfiguration;
import org.acra.interaction.ReportInteraction;
import org.acra.util.Installation;

import java.io.File;

import androidx.annotation.NonNull;
import timber.log.Timber;

@AutoService(ReportInteraction.class)
/**
 * This ACRA Extension sends an analytics hit during crash handling while ACRA is enabled.
 * Questions answered: "Number of ACRA reports sent", "ACRA vs Analytics count differences"
 * See <a href="https://github.com/ACRA/acra/wiki/Custom-Extensions">Custom Extensions</a>
 */
public class AcraAnalyticsInteraction implements ReportInteraction {


    @Override
    public boolean performInteraction(@NonNull Context context, @NonNull CoreConfiguration config, @NonNull File reportFile) {
        // Send an analytics exception hit with a UUID to match
        Timber.e("ACRA handling crash, sending analytics exception report");
        UsageAnalytics.sendAnalyticsEvent("ACRA Crash Handler", "UUID " + Installation.id(context));
        return true;
    }
}
