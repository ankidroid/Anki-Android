package com.ichi2.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.reviewer.Binding;
import com.ichi2.anki.reviewer.GestureMapper;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class BindingPreference extends android.preference.ListPreference {

    @SuppressWarnings("unused")
    public BindingPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressWarnings("unused")
    public BindingPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("unused")
    public BindingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressWarnings("unused")
    public BindingPreference(Context context) {
        super(context);
    }

    @Override
    public CharSequence getSummary() {
        String value = getValue();

        StringBuilder summary = new StringBuilder();

        for (Binding binding : fromString(value)) {
            if (summary.length() > 0) {
                summary.append(", ");
            }

            summary.append(binding.toDisplayString(getContext()));
        }

        return summary.toString();
    }

    protected boolean callChangeListener(Object newValue) {
        int index = Integer.parseInt((String) newValue);

        if (index == -2) {
            showAddDialog("Add key", new KeyRecorder(getContext()));
        } else if (index == -1) {
            showAddDialog("Add gesture", new GestureRecorder(getContext()));
        } else {
            List<Binding> bindings = fromString(getValue());
            bindings.remove(index);

            setValue(toString(bindings));
        }

        return false;
    }

    private class KeyRecorder extends TextView implements DialogInterface.OnClickListener, DialogInterface.OnShowListener {

        private Binding binding;

        KeyRecorder(Context context) {
            super(context);

            setFocusable(true);
            setFocusableInTouchMode(true);
            setText("Press a key");
            setTextSize(24);
            setTextAlignment(TEXT_ALIGNMENT_CENTER);
            setGravity(Gravity.CENTER);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {

                binding = Binding.key(event);

                setText(binding.toDisplayString(getContext()));

                checkExistingBinding(binding);
            }

            return true;
        }


        @Override
        public void onClick(DialogInterface dialog, int which) {
            addBinding(binding);
        }

        @Override
        public void onShow(DialogInterface dialog) {
            setHeight(getWidth());
            requestFocus();
        }
    }

    private class GestureRecorder extends TextView implements DialogInterface.OnClickListener, DialogInterface.OnShowListener {

        private final Drawable background;

        private final GestureMapper gestureMapper;

        private final GestureDetector detector;

        private Binding binding;

        GestureRecorder(Context context) {
            super(context);

            background = context.getDrawable(R.drawable.ic_gestures);
            background.setLevel(1000);
            setBackground(background);
            setTextSize(24);
            setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);

            gestureMapper = new GestureMapper();
            gestureMapper.init(100, true);

            detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    detected(Binding.gesture(Gesture.DOUBLE_TAP));

                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    detected(Binding.gesture(Gesture.LONG_TAP));
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float dx = e2.getX() - e1.getX();
                    float dy = e2.getY() - e1.getY();

                    Gesture gesture = gestureMapper.gesture(dx, dy, velocityX, velocityY, false, false, false);
                    if (gesture != null) {
                        detected(Binding.gesture(gesture));
                    }

                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    int height = getHeight();
                    int width = getWidth();

                    Gesture gesture = gestureMapper.gesture(height, width, e.getX(), e.getY());
                    if (gesture != null) {
                        detected(Binding.gesture(gesture));
                    }

                    return true;
                }
            });
        }

        private void detected(Binding binding) {
            this.binding = binding;
            setText(binding.toDisplayString(getContext()));
            background.setLevel(binding.getGesture().ordinal());

            checkExistingBinding(binding);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            addBinding(binding);
        }

        @Override
        public void onShow(DialogInterface dialog) {
            setHeight(getWidth());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (detector.onTouchEvent(event)) {
                return true;
            }

            return super.onTouchEvent(event);
        }
    }

    private void showAddDialog(String dialogTitle, View recorder) {

        AlertDialog.Builder mBuilder = new AlertDialog.Builder(getContext())
                .setTitle(dialogTitle)
                .setPositiveButton("OK", (DialogInterface.OnClickListener) recorder)
                .setNegativeButton(null, null)
                .setView(recorder);

        final Dialog dialog = mBuilder.create();
        dialog.setOnShowListener((DialogInterface.OnShowListener) recorder);
        dialog.show();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> entryValues = new ArrayList<>();

        entries.add("Add key");
        entryValues.add("-2");

        entries.add("Add gesture");
        entryValues.add("-1");

        int i = 0;
        for (Binding binding : fromString(getValue())) {
            entries.add("Remove " + binding.toDisplayString(getContext()));
            entryValues.add("" + i++);
        }

        setEntries(entries.toArray(new CharSequence[entries.size()]));
        setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));

        super.onPrepareDialogBuilder(builder);
    }

    private void checkExistingBinding(Binding binding) {
        List<String> existingCommands = new ArrayList<>();

        if (binding != null) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getContext());

            for (ViewerCommand command : ViewerCommand.values()) {
                String key = command.getPreferenceKey();

                if (key.equals(getKey()) == false) {
                    String value = preferences.getString(key, "");
                    if (value != null) {
                        for (String split : value.split(" ")) {
                            Binding candidate = Binding.fromString(split);

                            if (candidate.equals(binding)) {
                                existingCommands.add(getContext().getString(command.getResourceId()));
                            }
                        }
                    }
                }
            }
        }

        if (!existingCommands.isEmpty()) {
            String text = String.format("Already bound to %s", TextUtils.join(", ", existingCommands));
            Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private void addBinding(Binding binding) {
        if (binding != null) {
            List<Binding> bindings = fromString(getValue());

            bindings.remove(binding);
            bindings.add(0, binding);

            String value = toString(bindings);

            setValue(value);
        }
    }

    public static String toString(List<Binding> bindings) {
        StringBuilder builder = new StringBuilder();

        for (Binding binding : bindings) {
            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(binding.toString());
        }

        return builder.toString();
    }

    public static List<Binding> fromString(String string) {
        List<Binding> bindings = new ArrayList<>();

        if (!TextUtils.isEmpty(string)) {
            for (String split : string.split(" ")) {
                bindings.add(Binding.fromString(split));
            }
        }

        return bindings;
    }
}
