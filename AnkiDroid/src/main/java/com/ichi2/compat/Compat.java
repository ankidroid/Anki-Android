/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.compat;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.widget.TimePicker;

import com.ichi2.async.ProgressSenderAndCancelListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This interface defines a set of functions that are not available on all platforms.
 * <p>
 * A set of implementations for the supported platforms are available.
 * <p>
 * Each implementation ends with a {@code V<n>} prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see {@link CompatV21}.
 * <p>
 * Each implementation {@code CompatVn} should extend the implementation {@code CompatVm} for the greatest m<n such that
 * {@code CompatVm} exists. E.g. as of July 2021 {@code CompatV23} extends {@code CompatV21} because there is no {@code CompatV22}.
 * If {@code CompatV22} were to be created one day, it will extends {@code CompatV22} and be extended by {@code CompatV23}.
 * <p>
 * Each method {@code method} must be implemented in the lowest Compat implementation (right now {@code CompatV21}, but
 * it will change when min sdk change). It must also be implemented in {@code CompatVn} if, in version {@code n} and higher,
 * a different implementation must be used. This can be done either because some method used in the API {@code n} got
 * deprecated, changed its behavior, or because the implementation of {@code method} can be more efficient.
 * <p>
 * When you call method {@code method} from some device with API {@code n}, it will uses the implementation in {@code CompatVm},
 * for {@code m < n} as great as possible. The value of {@code m} being at least the current min SDK. The method may be empty,
 * for example {@code setupNotificationChannel}'s implementation in {@code CompatV21} is empty since
 * notification channels were introduced in API 26.
 * <p>
 * Example: {@code CompatV26} extends {@code CompatV23} which extends {@code CompatV21}. The method {@code vibrate} is
 * defined in {@code CompatV21} where only the number of seconds of vibration is taken into consideration, and is
 * redefined in {@code CompatV26} - using {@code @Override} - where the style of vibration is also taken into
 * consideration. It meas that  on devices using APIs 21 to 25 included, the implementation of {@code CompatV21} is
 * used, and on devices using API 26 and higher, the implementation of {@code CompatV26} is used.
 * On the other hand a method like {@code setTime} that got defined in {@code CompatV21} and redefined in
 * {@code CompatV23} due to a change of API, need not be implemented again in CompatV26.
 */
public interface Compat {

    /* Mock the Intent PROCESS_TEXT constants introduced in API 23. */
    String ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT";
    String EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT";
    void setupNotificationChannel(Context context, String id, String name);
    void setTime(TimePicker picker, int hour, int minute);
    int getHour(TimePicker picker);
    int getMinute(TimePicker picker);
    void vibrate(Context context, long durationMillis);
    void copyFile(String source, String target) throws IOException;
    long copyFile(String source, OutputStream target) throws IOException;
    long copyFile(InputStream source, String target) throws IOException;

    /**
     * Copies the directory represented by srcDir to the directory represented by destDir, and optionally makes a
     * best-effort to delete srcDir if the deleteAfterCopy param is set to <code>true</code>. The source must be a
     * directory. If the destination exists, it must be a directory. If it doesn't exist, then the destination directory
     * and any necessary parent directories are created recursively (see {@link File#mkdirs()} for details).
     * It is assumed that srcDir and destDir contain no symbolic links.
     * <p><br>
     * For instance, if the source directory, sdcard/AnkiDroid directory, contains 3 files, and
     * com.ichi2.Anki/files is the destination directory, the 3 files will be copied inside the com.ichi2.Anki/files
     * directory.
     * <p><br>
     * The destination may be empty or it may contain (partially or completely) content that is <em>probably</em> the
     * same as the source. A file isn't copied to the destination if it already exists at the destination according to a
     * simple heuristic.
     * <p><br>
     * This operation takes linear time - is proportional to the number of files in srcDir and its subdirectories
     * @param srcDir Abstract representation of source file/directory
     * @param destDir Abstract representation of destination directory
     * @param ioTask Listener used to send how many kilobytes of data have been copied since the last update
     * @param deleteAfterCopy If set to <code>true</code>, makes a best-effort to delete srcDir after it has been copied
     * @throws IOException if an error occurs
     */
    void copyDirectory(File srcDir, File destDir, ProgressSenderAndCancelListener<Integer> ioTask, boolean deleteAfterCopy) throws IOException;

    boolean hasVideoThumbnail(@NonNull String path);
    void requestAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener, @Nullable AudioFocusRequest audioFocusRequest);
    void abandonAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener, @Nullable AudioFocusRequest audioFocusRequest);
}

