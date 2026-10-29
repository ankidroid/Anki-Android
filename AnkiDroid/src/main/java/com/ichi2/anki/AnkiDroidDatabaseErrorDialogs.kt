// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.ContextWrapper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialogs

/** Shows [DatabaseErrorDialogs] through the [AnkiActivity] of the [Context]. */
internal object AnkiDroidDatabaseErrorDialogs : DatabaseErrorDialogs {
    override fun showLoadFailed(
        context: Context,
        exception: Exception,
    ) {
        (context as? AnkiActivity)
            ?.showDatabaseErrorDialog(
                errorDialogType = DatabaseErrorDialogType.DIALOG_LOAD_FAILED,
                exceptionData = DatabaseErrorDialog.CustomExceptionData.fromException(exception),
            )
    }

    override fun canConfirmDatabaseCheck(context: Context): Boolean = context.findAnkiActivity() != null

    override fun confirmDatabaseCheck(context: Context) {
        context.findAnkiActivity()!!.showDatabaseErrorDialog(
            errorDialogType = DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK,
        )
    }
}

/** Wires up [AnkiDroidDatabaseErrorDialogs] as the global [DatabaseErrorDialogs]. */
fun initializeDatabaseErrorDialogs() {
    DatabaseErrorDialogs.register(AnkiDroidDatabaseErrorDialogs)
}

/** The dialog's [Context] is wrapped (e.g. ContextThemeWrapper); walk the chain to find the activity. */
internal tailrec fun Context.findAnkiActivity(): AnkiActivity? =
    when (this) {
        is AnkiActivity -> this
        is ContextWrapper -> baseContext.findAnkiActivity()
        else -> null
    }
