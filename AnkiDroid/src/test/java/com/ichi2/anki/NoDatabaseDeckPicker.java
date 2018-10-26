package com.ichi2.anki;

import android.view.Menu;

// A testable deck-picker that avoids touching the database since requery isn't unit-testable
// see: https://github.com/requery/sqlite-android/issues/22
// but it possible to workaround if really wanted: https://rocko.xyz/2016/11/27/Android-Robolectric-%E5%8A%A0%E8%BD%BD%E8%BF%90%E8%A1%8C%E6%9C%AC%E5%9C%B0-so-%E5%8A%A8%E6%80%81%E5%BA%93/
public class NoDatabaseDeckPicker extends DeckPicker {


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return false;
    }
}
