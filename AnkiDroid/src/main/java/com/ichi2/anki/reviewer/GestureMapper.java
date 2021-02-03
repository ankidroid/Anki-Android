package com.ichi2.anki.reviewer;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.cardviewer.Gesture;

import timber.log.Timber;

/**
 * Map taps and flings to {@link Gesture}s.
 */
public class GestureMapper {

    private boolean mUseCornerTouch = true;

    private int mSwipeMinDistance = -1;
    private int mSwipeThresholdVelocity = -1;

    public void init(int sensitivity, boolean useCornerTouch) {
        if (sensitivity != 100) {
            float sens = 100.0f/sensitivity;
            mSwipeMinDistance = (int) (AnkiDroidApp.DEFAULT_SWIPE_MIN_DISTANCE * sens + 0.5f);
            mSwipeThresholdVelocity = (int) (AnkiDroidApp.DEFAULT_SWIPE_THRESHOLD_VELOCITY * sens  + 0.5f);
        } else {
            mSwipeMinDistance = AnkiDroidApp.DEFAULT_SWIPE_MIN_DISTANCE;
            mSwipeThresholdVelocity = AnkiDroidApp.DEFAULT_SWIPE_THRESHOLD_VELOCITY;
        }

        mUseCornerTouch = useCornerTouch;
    }


    /**
     * Get gesture for a fling.
     */
    public Gesture gesture(float dx, float dy, float velocityX, float velocityY,
                           boolean isSelecting, boolean isXScrolling, boolean isYScrolling) {
        try {
            if (Math.abs(dx) > Math.abs(dy)) {
                // horizontal swipe if moved further in x direction than y direction
                if (dx > mSwipeMinDistance
                        && Math.abs(velocityX) > mSwipeThresholdVelocity
                        && !isXScrolling && !isSelecting) {
                    return Gesture.SWIPE_RIGHT;
                } else if (dx < -mSwipeMinDistance
                        && Math.abs(velocityX) > mSwipeThresholdVelocity
                        && !isXScrolling && !isSelecting) {
                    return Gesture.SWIPE_LEFT;
                }
            } else {
                // otherwise vertical swipe
                if (dy > mSwipeMinDistance
                        && Math.abs(velocityY) > mSwipeThresholdVelocity
                        && !isYScrolling) {
                    return Gesture.SWIPE_DOWN;
                } else if (dy < -mSwipeMinDistance
                        && Math.abs(velocityY) > mSwipeThresholdVelocity
                        && !isYScrolling) {
                    return Gesture.SWIPE_UP;
                }
            }
        } catch (Exception e) {
            Timber.e(e, "onFling Exception");
        }

        return null;
    }

    /**
     * Get gesture for a tap.
     */
    public Gesture gesture(int height, int width, float posX, float posY) {
        if (width == 0 || height == 0) {
            return null;
        }

        Gesture gesture;
        if (mUseCornerTouch) {
            gesture = fromTapCorners(height, width, posX, posY);
        } else {
            gesture = fromTap(height, width, posX, posY);
        }

        return gesture;
    }

    private static Gesture fromTap(int height, int width, float posX, float posY) {
        boolean gestureIsRight = posY > height * (1 - posX / width);
        if (posX > posY / height * width) {
            if (gestureIsRight) {
                return Gesture.TAP_RIGHT;
            } else {
                return Gesture.TAP_TOP;
            }
        } else {
            if (gestureIsRight) {
                return Gesture.TAP_BOTTOM;
            } else {
                return Gesture.TAP_LEFT;
            }
        }
    }

    private static Gesture fromTapCorners(int height, int width, float posX, float posY) {

        double heightSegment = height / 3d;
        double widthSegment = width / 3d;

        TriState wSector = clamp(posX / widthSegment);
        TriState hSector = clamp(posY / heightSegment);

        switch (wSector) {
            case LOW:
                //left
                switch (hSector) {
                    case LOW:
                        return Gesture.TAP_TOP_LEFT;
                    case MID:
                        return Gesture.TAP_LEFT;
                    case HIGH:
                        return Gesture.TAP_BOTTOM_LEFT;
                }
                break;
            case MID:
                //center
                switch (hSector) {
                    case LOW:
                        return Gesture.TAP_TOP;
                    case MID:
                        return Gesture.TAP_CENTER;
                    case HIGH:
                        return Gesture.TAP_BOTTOM;
                }
                break;
            case HIGH:
                //Right
                switch (hSector) {
                    case LOW:
                        return Gesture.TAP_TOP_RIGHT;
                    case MID:
                        return Gesture.TAP_RIGHT;
                    case HIGH:
                        return Gesture.TAP_BOTTOM_RIGHT;
                }
                break;
            default:
                throw new IllegalArgumentException("illegal switch state");
        }
        throw new IllegalArgumentException("illegal switch state");
    }

    // clamps the value from LOW-MID-HIGH
    private static TriState clamp(double value) {
        double val = Math.floor(value);
        if (val >= 2) {
            return TriState.HIGH;
        }
        if (val < 1) {
            return TriState.LOW;
        }

        return TriState.MID;
    }

    private enum TriState {
        LOW,
        MID,
        HIGH
    }
}
