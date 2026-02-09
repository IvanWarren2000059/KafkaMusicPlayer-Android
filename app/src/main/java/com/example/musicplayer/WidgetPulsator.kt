package com.example.musicplayer

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews

/**
 * Handles pulsating animation for Kafka chibi in widget
 * Since RemoteViews doesn't support complex animations,
 * we simulate pulsation by toggling alpha values
 */
class WidgetPulsator(private val context: Context) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var pulsationRunnable: Runnable? = null
    private var isAnimating = false
    private var currentAlpha = 1.0f
    private var increasing = false
    
    companion object {
        private const val TAG = "WidgetPulsator"
        private const val ANIMATION_INTERVAL = 100L // Update every 100ms
        private const val ALPHA_STEP = 0.05f // Smooth steps
        private const val MIN_ALPHA = 0.85f
        private const val MAX_ALPHA = 1.0f
    }
    
    /**
     * Start pulsating animation for widget chibi
     */
    fun startPulsation() {
        if (isAnimating) return
        
        Log.d(TAG, "▶️ Starting widget chibi pulsation")
        isAnimating = true
        currentAlpha = MAX_ALPHA
        increasing = false
        
        pulsationRunnable = object : Runnable {
            override fun run() {
                if (!isAnimating) return
                
                // Calculate next alpha
                if (increasing) {
                    currentAlpha += ALPHA_STEP
                    if (currentAlpha >= MAX_ALPHA) {
                        currentAlpha = MAX_ALPHA
                        increasing = false
                    }
                } else {
                    currentAlpha -= ALPHA_STEP
                    if (currentAlpha <= MIN_ALPHA) {
                        currentAlpha = MIN_ALPHA
                        increasing = true
                    }
                }
                
                // Update widget
                updateWidgetAlpha(currentAlpha)
                
                // Schedule next update
                handler.postDelayed(this, ANIMATION_INTERVAL)
            }
        }
        
        handler.post(pulsationRunnable!!)
    }
    
    /**
     * Stop pulsating animation
     */
    fun stopPulsation() {
        if (!isAnimating) return
        
        Log.d(TAG, "⏸️ Stopping widget chibi pulsation")
        isAnimating = false
        pulsationRunnable?.let { handler.removeCallbacks(it) }
        pulsationRunnable = null
        
        // Reset to full alpha
        updateWidgetAlpha(1.0f)
    }
    
    /**
     * Update widget Kafka chibi alpha
     */
    private fun updateWidgetAlpha(alpha: Float) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.kafka_widget)
                
                // Set alpha for playing Kafka (this only affects if it's visible)
                views.setInt(R.id.widgetKafkaPlaying, "setAlpha", (alpha * 255).toInt())
                
                // Partial update - only update the alpha, keep everything else
                appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating widget alpha: ${e.message}")
        }
    }
}
