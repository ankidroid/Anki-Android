package com.ichi2.anki.tests;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;

@RunWith(Parameterized.class)
public class LayoutValidationTest extends InstrumentedTest {

    @Parameterized.Parameter
    public int mResourceId;

    @Parameterized.Parameter(1)
    public String mName;

    @Parameterized.Parameters(name = "{1}")
    public static java.util.Collection<Object[]> initParameters() throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> ctor = com.ichi2.anki.R.layout.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true); // Required for at least API 16, maybe later.
        Object layout = ctor.newInstance();

        // There are hidden public fields: abc_list_menu_item_layout for example
        HashSet<String> nonAnkiFieldNames = new HashSet<>();
        nonAnkiFieldNames.addAll(getFieldNames(androidx.appcompat.resources.R.layout.class));
        nonAnkiFieldNames.addAll(getFieldNames(com.google.android.material.R.layout.class));
        nonAnkiFieldNames.addAll(getFieldNames(com.afollestad.materialdialogs.R.layout.class));

        HashSet<String> invalidFieldNames = new HashSet<>();
        /*
        We need both a host activity and a fragment factory.
        We can use TextImporter as a host activity, but it didn't seem worth the performance hit for a screen that we know works
        android.view.InflateException: Binary XML file line #126 in com.ichi2.anki:layout/import_csv_option_selection: Binary XML file line #126 in com.ichi2.anki:layout/import_csv_option_selection: Error inflating class androidx.fragment.app.FragmentContainerView
        Caused by: android.view.InflateException: Binary XML file line #126 in com.ichi2.anki:layout/import_csv_option_selection: Error inflating class androidx.fragment.app.FragmentContainerView
        Caused by: androidx.fragment.app.Fragment$InstantiationException: Unable to instantiate fragment com.ichi2.anki.importer.FieldMappingFragment: could not find Fragment constructor
         */
        invalidFieldNames.add("import_csv_option_selection");

        List<Object[]> layouts = new ArrayList<>();
        for (Field f : R.layout.class.getFields()) {
            if (nonAnkiFieldNames.contains(f.getName())) {
                continue;
            }
            if (invalidFieldNames.contains(f.getName())) {
                continue;
            }
            layouts.add(new Object[] {f.getInt(layout), f.getName() });
        }

        return layouts;
    }

    @Test
    public void ensureLayout() throws Exception {
        // This should be fine to run on a device - but WebViews may be instantiated.
        // TODO: GestureDisplay.kt - why was mSwipeView.drawable null

        Context targetContext = getTestContext();
        Themes.setTheme(targetContext);
        LayoutInflater li = LayoutInflater.from(targetContext);
        ViewGroup root = new LinearLayout(targetContext);

        ensureNoCrashOnUiThread(() -> li.inflate(mResourceId, root, true));
    }

    /** Crashing on the UI thread takes down the process */
    private void ensureNoCrashOnUiThread(Runnable runnable) throws Exception {
        AtomicReference<Exception> failed = new AtomicReference<>();
        AtomicBoolean hasRun = new AtomicBoolean(false);
        runOnUiThread(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                failed.set(e);
            } finally {
                hasRun.set(true);
            }
        });

        //noinspection StatementWithEmptyBody
        while (!hasRun.get()) {
            // spin
        }

        if (failed.get() != null) {
            throw failed.get();
        }
    }


    @NonNull
    private static <T> HashSet<String> getFieldNames(Class<T> clazz) {
        Field[] badFields = clazz.getFields();
        HashSet<String> badFieldNames = new HashSet<>();
        for (Field f : badFields) {
            badFieldNames.add(f.getName());
        }
        return badFieldNames;
    }


    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
