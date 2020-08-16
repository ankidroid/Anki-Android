package com.ichi2.anki.tests.libanki;

import com.ichi2.anki.tests.Shared;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import androidx.test.platform.app.InstrumentationRegistry;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
@RunWith(androidx.test.runner.AndroidJUnit4.class)
public class ModelTest {

    private Collection testCol;

    @Before
    public void setUp() throws IOException {
        testCol = Shared.getEmptyCol(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void tearDown() {
        testCol.close();
    }

    @Test
    public void bigQuery() throws IOException {
        Models models = testCol.getModels();
        Model model = models.all().get(0);
        final String testString = "test";
        final int size = testString.length() * 1024 * 1024;
        StringBuilder buf = new StringBuilder((int) (size * 1.01));
        // * 1.01 for padding
        for (int i = 0; i < 1024 * 1024 ; ++i ) {
            buf.append(testString);
        }
        model.put(testString, buf.toString());
        // Buf should be more than 4MB, so at least two chunks from database.
        models.flush();
        // Reload models
        testCol.load();
        Model newModel = models.all().get(0);
        assertEquals(newModel, model);
    }
}
