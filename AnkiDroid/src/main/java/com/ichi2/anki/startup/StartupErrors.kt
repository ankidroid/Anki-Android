// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

/** Errors recorded during startup, owned by a single application instance. */
internal class StartupErrors {
    /** Set if an unrecoverable error occurs during application startup. */
    var fatalError: FatalInitializationError? = null
}
