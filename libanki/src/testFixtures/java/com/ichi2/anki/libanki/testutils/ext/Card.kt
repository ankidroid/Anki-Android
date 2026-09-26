// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.testutils.ext

import android.annotation.SuppressLint
import com.ichi2.anki.libanki.Card

@SuppressLint("VisibleForTests")
fun Card.setFlag(flag: Int) {
    flags = flag
}
