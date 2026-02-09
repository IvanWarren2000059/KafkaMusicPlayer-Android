package com.example.musicplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews

class MusicWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidget"
        
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.WIDGET_NEXT"
        const val ACTION_PREV = "com.example.musicplayer.WIDGET_PREV"
        const val ACTION_SHUFFLE = "com.example.musicplayer.WIDGET_SHUFFLE"
        
        fun updateWidget(context: Context, songTitle: String, artist: String, isPlaying: Boolean, bpm: Float = 120f) {
            Log.d(TAG, "🔄 Updating widget: $songTitle - $artist (playing: $isPlaying, BPM: $bpm)")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            Log.d(TAG, "📱 Found ${appWidgetIds.size} widget(s)")
            
            appWidgetIds.forEach { widgetId ->
                updateAppWidget(context, appWidgetManager, widgetId, songTitle, artist, isPlaying, bpm)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "🆕 onUpdate called for ${appWidgetIds.size} widget(s)")
        Log.d(TAG, "Widget IDs: ${appWidgetIds.joinToString()}")
        
        appWidgetIds.forEach { appWidgetId ->
            Log.d(TAG, "📱 Updating widget ID: $appWidgetId")
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId, 
                    "No song playing", "Unknown Artist", false)
                Log.d(TAG, "✅ Successfully updated widget $appWidgetId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to update widget $appWidgetId", e)
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "✅ Widget ENABLED - First widget added to home screen")
        Log.d(TAG, "Package: ${context.packageName}")
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "❌ Widget DISABLED - Last widget removed")
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "🗑️ Widget deleted: ${appWidgetIds.joinToString()}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📩 onReceive called with action: ${intent.action}")
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "▶️ Play/Pause from widget")
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PLAY_PAUSE
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_NEXT -> {
                Log.d(TAG, "⏭️ Next from widget")
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_NEXT
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_PREV -> {
                Log.d(TAG, "⏮️ Previous from widget")
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PREV
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_SHUFFLE -> {
                Log.d(TAG, "🔀 Shuffle from widget")
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_SHUFFLE_TOGGLE
                }
                startPlayerService(context, serviceIntent)
            }
        }
    }
    
    private fun startPlayerService(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "✅ Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start service: ${e.message}", e)
        }
    }
}

private fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    songTitle: String,
    artist: String,
    isPlaying: Boolean,
    bpm: Float = 120f
) {
    Log.d("MusicWidget", "🎨 Creating RemoteViews for widget $appWidgetId")
    
    try {
        val views = RemoteViews(context.packageName, R.layout.kafka_widget)
        
        // Set text
        views.setTextViewText(R.id.widgetSongTitle, songTitle)
        views.setTextViewText(R.id.widgetArtist, artist)
        
        // Set play/pause icon
        views.setImageViewResource(
            R.id.widgetPlayPauseBtn,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        
        // FIXED: Proper Kafka chibi visibility handling
        if (isPlaying) {
            // Show PLAYING Kafka chibi (visible)
            views.setViewVisibility(R.id.widgetKafkaIdle, View.GONE)
            views.setViewVisibility(R.id.widgetKafkaPlaying, View.VISIBLE)
            
            // Note: Widget pulsation is handled in MainActivity via WidgetPulsator
            // RemoteViews doesn't support complex animations, so we rely on the service
            
        } else {
            // Show IDLE Kafka chibi (visible)
            views.setViewVisibility(R.id.widgetKafkaIdle, View.VISIBLE)
            views.setViewVisibility(R.id.widgetKafkaPlaying, View.GONE)
        }
        
        // Background logo stays simple - just kafka_logo_2 PNG
        // Alpha is set in XML (0.40 for good visibility)
        
        // Set click listeners
        views.setOnClickPendingIntent(
            R.id.widgetPlayPauseBtn, 
            getPendingIntent(context, MusicWidget.ACTION_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widgetNextBtn,
            getPendingIntent(context, MusicWidget.ACTION_NEXT)
        )
        views.setOnClickPendingIntent(
            R.id.widgetPrevBtn,
            getPendingIntent(context, MusicWidget.ACTION_PREV)
        )
        views.setOnClickPendingIntent(
            R.id.widgetShuffleBtn,
            getPendingIntent(context, MusicWidget.ACTION_SHUFFLE)
        )
        
        // Set title click to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetTitle, openAppPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d("MusicWidget", "✅ Widget updated successfully")
        
    } catch (e: Exception) {
        Log.e("MusicWidget", "❌ Error updating widget: ${e.message}", e)
    }
}

private fun getPendingIntent(context: Context, action: String): PendingIntent {
    val intent = Intent(context, MusicWidget::class.java).apply {
        this.action = action
    }
    return PendingIntent.getBroadcast(
        context, 
        action.hashCode(), 
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
