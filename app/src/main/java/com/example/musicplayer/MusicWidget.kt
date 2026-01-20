package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class MusicWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.WIDGET_NEXT"
        const val ACTION_PREV = "com.example.musicplayer.WIDGET_PREV"
        const val ACTION_SHUFFLE = "com.example.musicplayer.WIDGET_SHUFFLE"
        
        fun updateWidget(context: Context, songTitle: String, artist: String, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            appWidgetIds.forEach { widgetId ->
                updateAppWidget(context, appWidgetManager, widgetId, songTitle, artist, isPlaying)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, 
                "No song playing", "Unknown Artist", false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PLAY_PAUSE
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_NEXT -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_NEXT
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_PREV -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PREV
                }
                startPlayerService(context, serviceIntent)
            }
            ACTION_SHUFFLE -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_SHUFFLE_TOGGLE
                }
                startPlayerService(context, serviceIntent)
            }
        }
    }
    
    private fun startPlayerService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

private fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    songTitle: String,
    artist: String,
    isPlaying: Boolean
) {
    val views = RemoteViews(context.packageName, R.layout.kafka_widget)
    
    // Update song info
    views.setTextViewText(R.id.widgetSongTitle, songTitle)
    views.setTextViewText(R.id.widgetArtist, artist)
    
    // Update play/pause button icon
    views.setImageViewResource(
        R.id.widgetPlayPauseBtn,
        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
    )
    
    // Set up button click intents
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
    
    // Click on widget title opens app
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAppPendingIntent = PendingIntent.getActivity(
        context, 0, openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetTitle, openAppPendingIntent)
    
    appWidgetManager.updateAppWidget(appWidgetId, views)
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