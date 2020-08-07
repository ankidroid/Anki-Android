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
        StringBuffer buf = new StringBuffer();
        for (long i = 0L; i < 400_000L; ++i ) {
            buf.append("abcdefghijkl");
        }
        model.put("test", buf.toString());
        // Buf should be more than 4MB, so at least two chunks from database.
        models.flush();
        // Reload models
        testCol.load();
        Model newModel = models.all().get(0);
        assertEquals(newModel, model);
    }
}