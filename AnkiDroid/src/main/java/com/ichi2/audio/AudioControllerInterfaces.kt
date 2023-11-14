package com.ichi2.audio

interface AudioControllerInterfaces {
    fun onStartStopRecording()
    fun onPlayPauseRecording()
    fun onCancelRecording()
    fun inReviewer(): Boolean
}
