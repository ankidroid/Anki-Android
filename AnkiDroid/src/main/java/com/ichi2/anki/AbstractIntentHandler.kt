// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_LOAD_FAILED
import com.ichi2.anki.dialogs.DatabaseErrorDialog.ShowDatabaseErrorDialog
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.themes.Themes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import timber.log.Timber

/**
 * This class is an abstract base class that extends Activity and provides common initialization logic for [IntentHandler] and [IntentHandler2].
 * By centralizing common setup tasks here, it promotes code reuse.
 */
abstract class AbstractIntentHandler : Activity() {
    private val catchingTaskScope = MainScope()

    /** Stop intent dispatch when Android's backup mode has skipped application and provider initialization. */
    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state -> super.onCreate(state) },
        )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AnkiDroidApp.wrapForActiveProfile(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.setTheme(this, savedInstanceState)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.activity_progress_bar)
    }

    override fun finish() {
        catchingTaskScope.cancel()
        super.finish()
    }

    override fun onDestroy() {
        catchingTaskScope.cancel()
        super.onDestroy()
    }

    protected fun launchCatchingTask(block: suspend CoroutineScope.() -> Unit): Job =
        catchingTaskScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showErrorAndFinish(e)
            }
        }

    /** Opens database recovery for a corrupt collection, otherwise shows [exception] and finishes once it is dismissed. */
    private fun showErrorAndFinish(exception: Exception) {
        if (exception is BackendDbCorruptException || exception is SQLiteDatabaseCorruptException) {
            openDatabaseRecovery(exception)
            return
        }
        Timber.w(exception, "%s failed", javaClass.simpleName)
        val dialog = showError(exception.localizedMessage ?: exception.toString(), exception.toCrashReportData(this))
        if (dialog == null) {
            finish()
            return
        }
        dialog.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) finish()
            },
        )
    }

    private fun openDatabaseRecovery(exception: Exception) {
        Timber.e(exception, "Database corruption in %s", javaClass.simpleName)
        DatabaseCorruption.isDetected = true
        storeMessage(
            ShowDatabaseErrorDialog(DIALOG_LOAD_FAILED, CustomExceptionData.fromException(exception)).toMessage(),
        )
        startActivity(DeckPicker.getIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
