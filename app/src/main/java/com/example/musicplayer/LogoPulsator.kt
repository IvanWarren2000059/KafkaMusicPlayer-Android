package com.example.musicplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Handles continuous slow brightness animation for threads around logo
 * Logo itself remains STATIC - only threads animate
 * Animation runs continuously, not tied to music playback
 */
class LogoPulsator(
    private val logoView: View,
    private val threadsView: View
) {
    private var animator: ObjectAnimator? = null
    
    init {
        // Start continuous animation immediately
        startContinuousAnimation()
    }
    
    /**
     * Start continuous thread brightness animation (slow ambient effect)
     */
    private fun startContinuousAnimation() {
        stop() // Stop any existing animation
        
        // Slow continuous pulsing - 3 seconds per cycle
        val animationDuration = 3000L
        
        // Animate threads alpha (brightness) continuously - dim to bright and back
        animator = ObjectAnimator.ofFloat(threadsView, "alpha", 0.2f, 0.5f, 0.2f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    
    /**
     * Start pulsation animation at the given BPM
     * This overrides the continuous animation with BPM-synced animation
     */
    fun start(bpm: Float = 120f, playing: Boolean = true) {
        if (!playing) {
            // When not playing, revert to continuous slow animation
            startContinuousAnimation()
            return
        }
        
        stop() // Stop any existing animation
        
        val beatDuration = BpmDetector.bpmToAnimationDuration(bpm)
        
        // BPM-synced thread animation
        animator = ObjectAnimator.ofFloat(threadsView, "alpha", 0.2f, 0.6f, 0.2f).apply {
            duration = beatDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    
    /**
     * Stop all animations and reset to base state
     */
    fun stop() {
        animator?.cancel()
        animator = null
        
        // Reset to base state
        logoView.alpha = 0.25f
        threadsView.alpha = 0.3f
    }
    
    /**
     * Update BPM and restart animation if different
     */
    fun updateBpm(newBpm: Float, playing: Boolean = true) {
        start(newBpm, playing)
    }
}