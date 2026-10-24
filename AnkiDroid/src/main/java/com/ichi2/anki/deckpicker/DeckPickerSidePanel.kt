// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.fragment.app.Fragment
import com.ichi2.anki.R

/** Owns pane navigation so removing the pane also discards its history, without popping activity dialogs. */
class DeckPickerSidePanel : Fragment(R.layout.fragment_deck_picker_side_panel)
