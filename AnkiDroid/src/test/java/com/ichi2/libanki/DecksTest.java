package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.utils.Assert;
import com.ichi2.utils.JSONObject;

import org.apache.http.util.Asserts;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class DecksTest extends RobolectricTest {
    // Used in other class to populate decks.
    public static final String[] TEST_DECKS = {
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS",
    };

    @Test
    public void ensureDeckList() {
        Decks decks = getCol().getDecks();
        for (String deckName: TEST_DECKS) {
            addDeck(deckName);
        }
        JSONObject brokenDeck = decks.byName("cmxieunwoogyxsctnjmv::INSBGDS");
        Asserts.notNull(brokenDeck,"We should get deck with given name");
        // Changing the case. That could exists in an old collection or during sync.
        brokenDeck.put("name", "CMXIEUNWOOGYXSCTNJMV::INSBGDS");
        decks.save(brokenDeck);

        decks.childMap();
        for (JSONObject deck: decks.all()) {
            long did = deck.getLong("id");
            for (JSONObject parent: decks.parents(did)) {
                Asserts.notNull(parent, "Parent should not be null");
            }
        }
    }
}
