package com.ichi2.anki.dialogs.customstudy;

import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener;
import com.ichi2.async.TaskListenerWithContext;

import androidx.annotation.NonNull;

import static com.ichi2.anki.dialogs.customstudy.CreateCustomStudySessionListener.*;

class CreateCustomStudySessionListener extends TaskListenerWithContext<Callback, Void, StudyOptionsFragment.DeckStudyData> {

    public interface Callback {
        void hideProgressBar();
        void onCreateCustomStudySession();
        void showProgressBar();
    }

    public CreateCustomStudySessionListener(Callback callback) {
        super(callback);
    }


    @Override
    public void actualOnPreExecute(@NonNull Callback callback) {
        callback.showProgressBar();
    }


    @Override
    public void actualOnPostExecute(@NonNull Callback callback, StudyOptionsFragment.DeckStudyData v) {
        callback.hideProgressBar();
        callback.onCreateCustomStudySession();
    }
}
