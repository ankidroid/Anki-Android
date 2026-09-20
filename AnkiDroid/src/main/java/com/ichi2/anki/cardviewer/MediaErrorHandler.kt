// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import androidx.annotation.VisibleForTesting
import androidx.core.net.toFile
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import timber.log.Timber
import java.io.File

/** Handles logic for displaying help for missing media files
 *
 * The rules are:
 * * Between each notification, the user must have either changed the card side or loaded a new note.
 *   Note that if issue occurs simultaneously, only the first one is shown to the user.
 * * Two TTS notifications max and two missing media notification max on each run of ankidroid, to avoid overloading the user with notification.
 *   After all, the snackbar is quickly annoying, the reviewer easily shows when medias are missing.
 * * If the user explicitly require TTS, and it fails, we always notify.
 * */
class MediaErrorHandler : MediaErrorListener {
    companion object {
        /** Specify a maximum number of times to display during one run of AnkiDroid, as it's somewhat annoying  */
        const val MAX_DISPLAY_TIMES = 2
    }

    constructor()
    constructor(onMediaError: ((String) -> Unit), onTtsError: ((TtsPlayer.TtsError) -> Unit)) {
        this.onMediaError = onMediaError
        this.onTtsError = onTtsError
    }

    /**
     * Callback to execute when there is an error playing a media.
     */
    // TODO turn into `val` once the legacy study screen is removed
    @VisibleForTesting
    var onMediaError: ((String) -> Unit)? = null

    /**
     * Callback to execute when there is an error playing a TTS.
     */
    @VisibleForTesting
    var onTtsError: ((TtsPlayer.TtsError) -> Unit)? = null

    /**
     * The number of times an error was displayed for a missing media since AnkiDroid was started.
     */
    private var missingMediaCount = 0

    /**
     * Whether an alert has been displayed since this particular card side was loaded.
     */
    private var warningDisplayedOnThisCardSide = false

    /**
     * The number of time an error was displayed for failing to play a TTS (not counting when the user explicitly request the TTS to be played.)
     */
    private var automaticTtsFailureCount = 0

    override fun onError(uri: Uri): MediaErrorBehavior {
        if (uri.scheme != "file") {
            return MediaErrorBehavior.CONTINUE_MEDIA
        }

        val file = uri.toFile()
        // There is a multitude of transient issues with the MediaPlayer.
        // Retrying fixes most of these
        if (file.exists()) return MediaErrorBehavior.RETRY_MEDIA

        onMediaError?.let { callback ->
            processMissingMedia(file, callback)
        }
        return MediaErrorBehavior.CONTINUE_MEDIA
    }

    override fun onMediaPlayerError(
        mp: MediaPlayer?,
        which: Int,
        extra: Int,
        uri: Uri,
    ): MediaErrorBehavior {
        Timber.w("Media Error: (%d, %d)", which, extra)
        return onError(uri)
    }

    override fun onTtsError(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
    ) {
        onTtsError?.let { callback ->
            processTtsFailure(error, isAutomaticPlayback, callback)
        }
    }

    override fun onMediaNotFoundError(
        request: WebResourceRequest,
        onFailure: (String) -> Unit,
    ) {
        // We do not want this to trigger more than once on the same side of the card as the UI will flicker.
        if (warningDisplayedOnThisCardSide) return

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= MAX_DISPLAY_TIMES) return

        val url = request.url
        // We could do better here (external images failing due to no HTTPS), but failures can occur due to no network.
        // As we don't yet check the error data, we don't know.
        // Therefore, limit this feature to the common case of local files, which should always work.
        if (url.host != LOCALHOST) return

        try {
            val filename = URLUtil.guessFileName(url.toString(), null, null)
            onFailure.invoke(filename)
            missingMediaCount++
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            warningDisplayedOnThisCardSide = true
        }
    }

    /**
     * Maybe informs the user that media [file] is missing.
     */
    fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) {
        // We want this to trigger more than once on the same side - as the user is in control of pressing "play"
        // and we want to provide feedback
        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= MAX_DISPLAY_TIMES) return

        try {
            val fileName = file.name
            onFailure.invoke(fileName)
            if (!warningDisplayedOnThisCardSide) {
                missingMediaCount++
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            warningDisplayedOnThisCardSide = true
        }
    }

    override fun onCardSideChange() {
        warningDisplayedOnThisCardSide = false
    }

    /**
     * Informs the user that there was an error playing a TTS through [errorHandler] if either the user requested the TTS explicitly or it's at most the second time it occurred since AnkiDroid started.
     *
     * @param error The error that occurred while trying to play a TTS.
     * @param isAutomaticPlayback Whether the playback started due to ankidroid settings and not due to an explicit user request.
     * @param errorHandler The callback to execute to inform the user of the failure.
     */
    fun processTtsFailure(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
        errorHandler: (TtsPlayer.TtsError) -> Unit,
    ) {
        if (warningDisplayedOnThisCardSide) {
            return
        }
        // if the user is playing a single sound explicitly, we want to provide feedback
        if (isAutomaticPlayback) {
            if (automaticTtsFailureCount >= MAX_DISPLAY_TIMES) {
                Timber.v("Ignoring TTS Error: %s. failure limit exceeded", error)
                return
            }
            automaticTtsFailureCount++
        }

        Timber.w("displaying error for %s", error)
        // Maybe specifically check for APP_TTS_INIT_TIMEOUT

        errorHandler.invoke(error)
        warningDisplayedOnThisCardSide = true
    }
}
