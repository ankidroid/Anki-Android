/****************************************************************************************
 * Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
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

package com.ichi2.anki.multimediacard

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import com.ichi2.anki.multimediacard.MediaPlayer.MediaPlayerState.*
import java.io.FileDescriptor
import java.net.HttpCookie

/**
 * A MediaPlayer that extends android.media.MediaPlayer
 *    It functions exactly the same with the addition of a state parameter
 *    because android doesn't have a non-private getter for its MediaPlayer state
 */
class MediaPlayer :
    MediaPlayer(),
    OnPreparedListener,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnCompletionListener {
    // MediaPlayer state that changes based on https://developer.android.com/reference/android/media/MediaPlayer#state-diagram
    var state: MediaPlayerState = IDLE
        set(value) {
            field = value
            stateListener?.onChanged(value)
        }
    var stateListener: MediaPlayerStateListener? = null

    init {
        state = IDLE
        setOnErrorListener(this)
        setOnPreparedListener(this)
        setOnCompletionListener(this)
    }

    override fun setDataSource(context: Context, uri: Uri) {
        super.setDataSource(context, uri)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(
        context: Context,
        uri: Uri,
        headers: MutableMap<String, String>?,
        cookies: MutableList<HttpCookie>?
    ) {
        super.setDataSource(context, uri, headers, cookies)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(context: Context, uri: Uri, headers: MutableMap<String, String>?) {
        super.setDataSource(context, uri, headers)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(path: String?) {
        super.setDataSource(path)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(afd: AssetFileDescriptor) {
        super.setDataSource(afd)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(fd: FileDescriptor?) {
        super.setDataSource(fd)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(fd: FileDescriptor?, offset: Long, length: Long) {
        super.setDataSource(fd, offset, length)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun setDataSource(dataSource: MediaDataSource?) {
        super.setDataSource(dataSource)
        if (state == IDLE) {
            state = INITIALIZED
        }
    }

    override fun reset() {
        super.reset()
        when (state) {
            IDLE, INITIALIZED, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETE, ERROR ->
                state = IDLE
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun release() {
        super.release()
        state = END
    }

    override fun prepareAsync() {
        super.prepareAsync()
        when (state) {
            INITIALIZED, STOPPED -> state = PREPARING
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun prepare() {
        super.prepare()
        when (state) {
            INITIALIZED, STOPPED -> state = PREPARED
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun seekTo(msec: Int) {
        super.seekTo(msec)
        when (state) {
            PREPARED, STARTED, PAUSED, PLAYBACK_COMPLETE -> {}
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun seekTo(msec: Long, mode: Int) {
        super.seekTo(msec, mode)
        when (state) {
            PREPARED, STARTED, PAUSED, PLAYBACK_COMPLETE -> {}
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun stop() {
        super.stop()
        when (state) {
            PREPARED, STARTED, STOPPED, PAUSED, PLAYBACK_COMPLETE -> state = STOPPED
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun start() {
        super.start()
        when (state) {
            PREPARED, STARTED, PAUSED, PLAYBACK_COMPLETE -> state = STARTED
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun pause() {
        super.pause()
        when (state) {
            STARTED, PAUSED, PLAYBACK_COMPLETE -> state = PAUSED
            else -> throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer?) {
        setStateOnPrepared()
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        setStateOnError()
        return false
    }

    override fun onCompletion(p0: MediaPlayer?) {
        setStateOnCompletion()
    }

    private fun setStateOnPrepared() {
        if (state == PREPARING) {
            state = PREPARED
        } else {
            throw IllegalStateException("Invalid MediaPlayerState $state")
        }
    }

    private fun setStateOnError() {
        state = ERROR
    }

    private fun setStateOnCompletion() {
        if (state == STARTED && !isLooping) {
            state = PLAYBACK_COMPLETE
        }
    }

    override fun setOnCompletionListener(listener: OnCompletionListener?) {
        super.setOnCompletionListener { mediaPlayer ->
            setStateOnCompletion()
            listener?.onCompletion(mediaPlayer)
        }
    }

    override fun setOnPreparedListener(listener: OnPreparedListener?) {
        super.setOnPreparedListener {
            OnPreparedListener { mediaPlayer ->
                setStateOnPrepared()
                listener?.onPrepared(mediaPlayer)
            }
        }
    }

    enum class MediaPlayerState {
        ERROR,
        IDLE,
        INITIALIZED,
        PREPARING,
        PREPARED,
        STARTED,
        PAUSED,
        STOPPED,
        PLAYBACK_COMPLETE,
        END
    }

    interface MediaPlayerStateListener {
        fun onChanged(value: MediaPlayerState)
    }
}
