/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates code from https://github.com/varunjohn/Audio-Recording-Animation
 * under the Apache License, Version 2.0.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.ThemeUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.TypedValueCompat
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.compat.CompatHelper
import com.ichi2.compat.USAGE_TOUCH
import com.ichi2.utils.Permissions
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * A view that can serve as an audio recorder.
 *
 * The main functionalities work around the record button:
 * * Tap to start recording
 * * Long press to record while pressed
 *     * Swipe up to 'lock' and keep recording until the stop button is pressed
 *     * Swipe left to cancel the recording
 * * Check if the microphone permission has been granted before doing any action
 *
 * It also displays a recording icon and time
 */
class AudioRecordView : ConstraintLayout {
    // region Views
    private val recordButton: View
    private val recordButtonIcon: ImageView
    private val lockArrow: View
    private val imageViewLock: View
    private val recordDisplayIcon: AppCompatImageView
    private val layoutSlideCancel: View
    private val layoutLock: View
    private val chronometer: Chronometer
    // endregion

    // region Animations
    private val animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)
    private val animJump = AnimationUtils.loadAnimation(context, R.anim.jump)
    private val animJumpFast = AnimationUtils.loadAnimation(context, R.anim.jump_fast)
    // endregion

    // region State & Logic
    private var state = ViewState.IDLE
    private var stopTrackingAction = false
    private var chronometerBase: Long = 0
    private val recordEnabledColor = context.getColor(R.color.material_red_600)
    private val recordDisabledColor = ThemeUtils.getThemeAttrColor(context, R.attr.editTextDisabled)

    private var firstX = 0f
    private var firstY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val dp = TypedValueCompat.dpToPx(1F, resources.displayMetrics)
    private val cancelOffset: Float
    private val cancelFadeOffset: Float
    private val lockOffset: Float

    private var recordingListener: RecordingListener? = null

    fun setRecordingListener(recordingListener: RecordingListener) {
        this.recordingListener = recordingListener
    }

    private lateinit var gestureDetector: GestureDetector
    // endregion

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        LayoutInflater.from(context).inflate(R.layout.audio_record_view, this, true)
        recordButton = findViewById(R.id.recordButton)
        recordButtonIcon = findViewById(R.id.recordIcon)
        imageViewLock = findViewById(R.id.lock_icon)
        lockArrow = findViewById(R.id.lock_arrow_icon)
        chronometer = findViewById(R.id.chronometer)
        layoutSlideCancel = findViewById(R.id.layout_slide_cancel)
        layoutLock = findViewById(R.id.layoutLock)
        recordDisplayIcon = findViewById(R.id.recording_status_icon)
        cancelOffset = max((resources.displayMetrics.widthPixels * 0.25f), 60f * dp)
        cancelFadeOffset = cancelOffset * 0.8f
        lockOffset = max((resources.displayMetrics.heightPixels * 0.25f), 80f * dp)

        setupTouchListener()
    }

    private enum class ViewState {
        IDLE,
        RECORDING,
        LOCKED,
    }

    enum class RecordingBehavior {
        CANCEL,
        LOCK,
        RELEASE,
    }

    interface RecordingListener {
        fun onRecordingPermissionRequired()

        fun onRecordingStarted()

        fun onRecordingCanceled()

        fun onRecordingCompleted()
    }

    private fun setupTouchListener() {
        gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        if (!Permissions.canRecordAudio(context)) {
                            recordingListener?.onRecordingPermissionRequired()
                            return true
                        }
                        startRecording()
                        recordButton
                            .animate()
                            .scaleX(1.25f)
                            .scaleY(1.25f)
                            .setDuration(150)
                            .start()
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!Permissions.canRecordAudio(context)) return true
                        lock()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!Permissions.canRecordAudio(context)) return
                        CompatHelper.compat.vibrate(context, 50.milliseconds, USAGE_TOUCH)
                        showCancelAndLockSliders()
                        firstX = e.rawX
                        firstY = e.rawY
                    }
                },
            )
        recordButton.setOnTouchListener(gestureListener)
    }

    private val gestureListener =
        OnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent)

            if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                if (state == ViewState.IDLE) {
                    reset(animate = true)
                }
            }

            if (state == ViewState.RECORDING) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_UP -> stopRecording(RecordingBehavior.RELEASE)
                    MotionEvent.ACTION_MOVE -> handleMove(motionEvent)
                }
            }
            true
        }

    private fun handleMove(motionEvent: MotionEvent) {
        if (stopTrackingAction) return

        val behavior = getBehaviorFromDirection(motionEvent.rawX, motionEvent.rawY)
        when (behavior) {
            RecordingBehavior.CANCEL -> translateX(motionEvent.rawX - firstX)
            RecordingBehavior.LOCK -> translateY(motionEvent.rawY - firstY)
            else -> {}
        }

        lastX = motionEvent.rawX
        lastY = motionEvent.rawY
    }

    /**
     * @return the behavior based on the predominant movement, which can be:
     * * [RecordingBehavior.LOCK] if it's a vertical movement to the top
     * * [RecordingBehavior.CANCEL] if it's an horizontal movement to the left
     * * `null` otherwise
     */
    private fun getBehaviorFromDirection(
        currentX: Float,
        currentY: Float,
    ): RecordingBehavior? {
        val motionX = abs(firstX - currentX)
        val motionY = abs(firstY - currentY)

        return when {
            motionY > motionX && currentY < firstY -> RecordingBehavior.LOCK
            motionX > motionY && currentX < firstX -> RecordingBehavior.CANCEL
            else -> null
        }
    }

    /**
     * Moves the record button and lock slider vertically based on [y].
     * If [lockOffset] is reached, the recording is locked and the vertical positions are reset.
     */
    private fun translateY(y: Float) {
        if (y < -lockOffset) {
            lock()
            recordButton.translationY = 0f
            return
        }

        layoutLock.visibility = VISIBLE
        recordButton.translationY = y
        layoutLock.translationY = y / 2
        recordButton.translationX = 0f
    }

    /**
     * Moves the record button and cancel slider horizontally based on [x].
     * If [cancelOffset] is reached, the recording is canceled
     * and the horizontal positions are reset.
     */
    private fun translateX(x: Float) {
        if (x < -cancelOffset) {
            cancel()
            recordButton.translationX = 0f
            layoutSlideCancel.translationX = 0f
            return
        }

        val alpha = (cancelFadeOffset - abs(x)) / cancelFadeOffset
        layoutSlideCancel.alpha = alpha.coerceIn(0f, 1f)

        recordButton.translationX = x
        layoutSlideCancel.translationX = x
        layoutLock.translationY = 0f
        recordButton.translationY = 0f

        if (abs(x) < recordButton.width / 2) {
            layoutLock.visibility = VISIBLE
        } else {
            layoutLock.visibility = GONE
        }
    }

    private fun startRecording() {
        state = ViewState.RECORDING
        stopTrackingAction = false
        recordingListener?.onRecordingStarted()
        displayRunningRecord()
    }

    private fun showCancelAndLockSliders() {
        recordButton
            .animate()
            .scaleX(1.8f)
            .scaleY(1.8f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()

        layoutLock.visibility = VISIBLE
        layoutSlideCancel.visibility = VISIBLE
        lockArrow.startAnimation(animJumpFast)
        imageViewLock.startAnimation(animJump)
    }

    /**
     * Sets the visibility of the record timer and icon to [isVisible]
     */
    fun setRecordDisplayVisibility(isVisible: Boolean) {
        chronometer.isVisible = isVisible
        recordDisplayIcon.isVisible = isVisible
    }

    private fun displayRunningRecord() {
        setRecordDisplayVisibility(true)

        recordDisplayIcon.setColorFilter(recordEnabledColor)
        recordDisplayIcon.startAnimation(animBlink)

        chronometer.base = if (chronometerBase > 0) chronometerBase else SystemClock.elapsedRealtime()
        chronometer.isEnabled = true
        chronometer.start()
    }

    private fun lock() {
        state = ViewState.LOCKED
        stopTrackingAction = true

        recordButtonIcon.setImageResource(R.drawable.ic_stop)
        recordButton.animate().cancel()
        recordButton.scaleX = 1f
        recordButton.scaleY = 1f

        recordButton.setOnTouchListener(null)
        recordButton.setOnClickListener {
            stopRecording(RecordingBehavior.LOCK)
        }
        layoutSlideCancel.visibility = GONE
        layoutLock.visibility = GONE
    }

    private fun cancel() {
        stopTrackingAction = true
        stopRecording(RecordingBehavior.CANCEL)
    }

    private fun stopRecording(outcome: RecordingBehavior) {
        if (state != ViewState.RECORDING && state != ViewState.LOCKED) return

        val animateRelease = outcome == RecordingBehavior.RELEASE
        reset(animate = animateRelease)
        chronometer.stop()

        when (outcome) {
            RecordingBehavior.CANCEL -> {
                recordingListener?.onRecordingCanceled()
            }
            RecordingBehavior.RELEASE, RecordingBehavior.LOCK -> {
                recordingListener?.onRecordingCompleted()
            }
        }
    }

    private fun reset(animate: Boolean) {
        state = ViewState.IDLE
        stopTrackingAction = false
        firstX = 0f
        firstY = 0f
        lastX = 0f
        lastY = 0f
        chronometerBase = 0

        recordButtonIcon.setImageResource(R.drawable.ic_action_mic)
        recordButton.setOnClickListener(null)
        recordButton.setOnTouchListener(gestureListener)

        if (animate) {
            recordButton
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(100)
                .setInterpolator(LinearInterpolator())
                .start()
        } else {
            recordButton.animate().cancel()
            recordButton.scaleX = 1f
            recordButton.scaleY = 1f
            recordButton.translationX = 0f
            recordButton.translationY = 0f
        }

        layoutSlideCancel.visibility = GONE
        layoutLock.visibility = GONE
        chronometer.visibility = INVISIBLE
        recordDisplayIcon.visibility = INVISIBLE

        // Reset the translation of the sliders to ensure they start from the correct position next time
        layoutLock.translationY = 0f
        layoutSlideCancel.translationX = 0f
        layoutSlideCancel.alpha = 1f

        setRecordDisplayVisibility(true)
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.isEnabled = false
        recordDisplayIcon.setColorFilter(recordDisabledColor)
        recordDisplayIcon.clearAnimation()
        lockArrow.clearAnimation()
        imageViewLock.clearAnimation()
    }

    /**
     * Immediately stops all actions and animations, and returns the view to its initial state.
     */
    fun forceReset() {
        chronometer.stop()
        recordButton.clearAnimation()
        recordDisplayIcon.clearAnimation()
        reset(animate = false)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.state = state
        savedState.chronometerBase = chronometer.base
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            this.state = state.state
            this.chronometerBase = state.chronometerBase
            when (this.state) {
                ViewState.LOCKED -> {
                    displayRunningRecord()
                    lock()
                }
                else -> reset(false)
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        var state: ViewState = ViewState.IDLE
        var chronometerBase: Long = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            state = ViewState.valueOf(source.readString() ?: ViewState.IDLE.name)
            chronometerBase = source.readLong()
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeString(state.name)
            out.writeLong(chronometerBase)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
