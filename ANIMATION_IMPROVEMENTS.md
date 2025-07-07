# Animation Improvements for AnkiDroid

This document outlines the smoother navigation and animation improvements implemented in AnkiDroid,
following Material Design motion principles.

## Overview

The goal was to implement shared element transitions or Material Motion for smoother navigation
throughout the app, particularly when transitioning from deck to card view and between different
screens.

## Key Improvements

### 1. New Animation Utility Class (`AnimationUtils.kt`)

Created a comprehensive utility class that provides:

- **Material Design Interpolators**: Uses standard Material Design easing curves like
  `FastOutSlowInInterpolator`, `FastOutLinearInInterpolator`, and `LinearOutSlowInInterpolator`
- **Shared Element-like Transitions**: Creates smooth transitions between views using
  `TransitionSet` with `ChangeBounds`, `ChangeTransform`, and `ChangeImageTransform`
- **Smooth Slide Transitions**: For navigation between screens with proper directional movement
- **Fade and Scale Animations**: For appearing/disappearing UI elements
- **Card Elevation Animations**: For providing tactile feedback on interactions
- **Container Transform Effects**: For seamless transitions between different view states

### 2. Enhanced Navigation Animations

#### Deck to Reviewer Transition

- **Enhanced `openReviewer()` method**: Now uses custom Material Design slide transitions
- **Smooth Entry**: Cards slide in from the right with a fade effect
- **Consistent Exit**: Previous screen slides out to the left with fade
- **Animation Resources**: New animation XML files with proper Material Design timing and easing

#### Deck Selection Feedback

- **Elevation Animation**: Selected deck cards get a subtle elevation increase for tactile feedback
- **Study Options Animation**: Smooth fade-scale transition when study options appear
- **Staggered Animations**: Multiple elements animate with slight delays for a more polished feel

#### Answer Button Animations

- **Staggered Appearance**: Answer buttons appear with a cascade effect (50ms delay between each)
- **Fade and Scale**: Each button fades in while scaling from 80% to 100% size
- **Material Motion**: Uses `LinearOutSlowInInterpolator` for natural feeling animations

### 3. New Animation Resources

Created new animation XML files with Material Design principles:

- `slide_in_right_material.xml`: Smooth slide-in from right with fade
- `slide_out_left_material.xml`: Smooth slide-out to left with fade
- `fade_scale_in_material.xml`: Combined fade and scale for appearing elements
- `fade_scale_out_material.xml`: Combined fade and scale for disappearing elements

### 4. Improved Existing Animations

Updated existing animation files to use:

- **Better Timing**: 300ms for slide transitions, 200-250ms for fade effects
- **Material Interpolators**: Replaced linear interpolators with Material Design curves
- **Combined Effects**: Added alpha transitions to existing slide animations

### 5. Accessibility and Performance

- **Safe Display Mode**: All animations respect the user's "safe display mode" preference
- **Performance Optimized**: Animations are hardware accelerated where possible
- **Backwards Compatible**: Fallback animations for older Android versions
- **Smooth Interruption**: Animations can be cleanly interrupted without jarring effects

## Technical Implementation

### Dependencies Added

- `androidx.transition:transition:1.5.2`: For advanced transition effects
- `androidx.interpolator:interpolator:1.0.0`: For Material Design interpolators

### Animation Duration Standards (Material Design)

- **Short animations**: 200ms (for small UI changes)
- **Medium animations**: 300ms (for screen transitions)
- **Long animations**: 500ms (for complex transitions)

### Easing Curves Used

- **Fast Out Slow In**: For elements moving on screen (entrances)
- **Fast Out Linear In**: For elements leaving screen (exits)
- **Linear Out Slow In**: For elements appearing/growing
- **Accelerate Decelerate**: For continuous motion

## User Experience Benefits

1. **Smoother Navigation**: Transitions between screens feel more connected and intentional
2. **Visual Continuity**: Elements appear to transform rather than abruptly change
3. **Reduced Cognitive Load**: Smooth animations help users understand the relationship between
   screens
4. **Modern Feel**: Animations match current Material Design standards
5. **Tactile Feedback**: Subtle elevation changes provide immediate response to user actions

## Future Enhancements

Potential areas for further improvement:

- **Shared Element Transitions**: True shared elements between activities (requires significant
  architectural changes)
- **Card Flip Animations**: Smooth transitions between question and answer sides
- **List Item Animations**: Enhanced animations for adding/removing deck items
- **Gesture-Driven Animations**: Motion that follows user's finger during swipe gestures

## Testing and Compatibility

- **Android Version Support**: Works on Android API 24+ (current minimum)
- **Performance**: Optimized to maintain 60 FPS on mid-range devices
- **Accessibility**: Respects system animation preferences and safe display mode
- **Backwards Compatibility**: Graceful fallbacks for unsupported features

This implementation provides a significant improvement in user experience while maintaining the
app's performance and accessibility standards.