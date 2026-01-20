package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MediaButtonReceiver"
        private var clickCount = 0
        private var handler: Handler? = null
        private var runnable: Runnable? = null
        private const val CLICK_DELAY = 300L
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        
        if (Intent.ACTION_MEDIA_BUTTON != intent.action) return
        
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        
        if (event == null) {
            Log.w(TAG, "No KeyEvent found in intent")
            return
        }
        
        Log.d(TAG, "KeyEvent: action=${event.action}, code=${event.keyCode}")
        
        // Only process ACTION_DOWN to avoid duplicate events
        if (event.action != KeyEvent.ACTION_DOWN) return
        
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                Log.d(TAG, "Headset button pressed")
                handleHeadsetClick(context)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                Log.d(TAG, "Next button pressed")
                sendAction(context, PlayerService.ACTION_NEXT)
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                Log.d(TAG, "Previous button pressed")
                sendAction(context, PlayerService.ACTION_PREV)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                Log.d(TAG, "Play button pressed")
                sendAction(context, PlayerService.ACTION_PLAY_PAUSE)
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                Log.d(TAG, "Pause button pressed")
                sendAction(context, PlayerService.ACTION_PLAY_PAUSE)
            }
            else -> {
                Log.d(TAG, "Unhandled key code: ${event.keyCode}")
            }
        }
    }
    
    private fun handleHeadsetClick(context: Context) {
        clickCount++
        Log.d(TAG, "Click count: $clickCount")
        
        // Cancel any pending action
        runnable?.let { 
            handler?.removeCallbacks(it)
            Log.d(TAG, "Cancelled pending action")
        }
        
        // Initialize handler if needed
        if (handler == null) {
            handler = Handler(Looper.getMainLooper())
        }
        
        // Create action to execute after delay
        runnable = Runnable {
            Log.d(TAG, "Executing action for $clickCount clicks")
            when (clickCount) {
                1 -> {
                    Log.d(TAG, "Single click: Play/Pause")
                    sendAction(context, PlayerService.ACTION_PLAY_PAUSE)
                }
                2 -> {
                    Log.d(TAG, "Double click: Next")
                    sendAction(context, PlayerService.ACTION_NEXT)
                }
                3 -> {
                    Log.d(TAG, "Triple click: Previous")
                    sendAction(context, PlayerService.ACTION_PREV)
                }
                else -> {
                    Log.d(TAG, "Too many clicks ($clickCount), treating as Next")
                    sendAction(context, PlayerService.ACTION_NEXT)
                }
            }
            clickCount = 0
        }
        
        // Wait for more clicks
        handler?.postDelayed(runnable!!, CLICK_DELAY)
    }
    
    private fun sendAction(context: Context, action: String) {
        Log.d(TAG, "Sending action: $action")
        
        val serviceIntent = Intent(context, PlayerService::class.java).apply {
            this.action = action
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}", e)
        }
    }
}