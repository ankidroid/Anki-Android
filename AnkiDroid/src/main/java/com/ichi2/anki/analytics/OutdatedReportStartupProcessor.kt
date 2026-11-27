// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.LimiterData
import org.acra.file.CrashReportFileNameParser
import org.acra.file.CrashReportPersister
import org.acra.prefs.SharedPreferencesFactory
import org.acra.startup.Report
import org.acra.startup.StartupProcessor
import org.acra.util.PackageManagerWrapper
import timber.log.Timber
import java.io.File
import java.io.IOException

class OutdatedReportStartupProcessor : StartupProcessor {
    override fun processReports(
        context: Context,
        config: CoreConfiguration,
        reports: List<Report>,
    ) {
        val packageInfo = PackageManagerWrapper(context).getPackageInfo() ?: return
        resetLimitsAfterUpdate(context, config, PackageInfoCompat.getLongVersionCode(packageInfo))
        context.filesDir.parentFile?.let { File(it, PROFILE_APPROVED_REPORTS).deleteRecursively() }
        for (report in reports) {
            if (!report.file.isOutdated(packageInfo)) continue
            report.delete = true
            report.file.delete()
        }
    }

    private fun resetLimitsAfterUpdate(
        context: Context,
        config: CoreConfiguration,
        appVersion: Long,
    ) {
        val prefs = SharedPreferencesFactory(context, config).create()
        val lastVersion =
            runCatching { prefs.getLong(ACRA.PREF_LAST_VERSION_NR, 0) }
                .recoverCatching { prefs.getInt(ACRA.PREF_LAST_VERSION_NR, 0).toLong() }
                .getOrDefault(0)
        if (appVersion <= lastVersion) return
        prefs.edit { putLong(ACRA.PREF_LAST_VERSION_NR, appVersion) }
        try {
            LimiterData().store(context)
        } catch (e: IOException) {
            Timber.w(e, "Failed to reset ACRA limiter data")
        }
    }

    private fun File.isOutdated(packageInfo: PackageInfo): Boolean {
        val reportVersion =
            runCatching {
                CrashReportPersister().load(this).getString(ReportField.APP_VERSION_CODE)?.toLongOrNull()
            }.getOrNull()
        if (reportVersion != null) return reportVersion < PackageInfoCompat.getLongVersionCode(packageInfo)
        return CrashReportFileNameParser().getTimestamp(name).timeInMillis < packageInfo.lastUpdateTime
    }

    companion object {
        private const val PROFILE_APPROVED_REPORTS = "ACRA-approved"
    }
}
