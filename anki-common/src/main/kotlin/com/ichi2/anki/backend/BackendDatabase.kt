// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import androidx.annotation.CheckResult
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.DB
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@DuplicatedCode("libanki:createDatabaseUsingRustBackend")
@CheckResult
fun createDatabaseUsingRustBackend(backend: Backend): DB = DB(AnkiSupportSQLiteDatabase.withRustBackend(backend))
