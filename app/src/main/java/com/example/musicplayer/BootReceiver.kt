package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver that starts the music service when device boots up
 * This ensures music can resume if it was playing before reboot
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "📱 Device booted - checking if should restore music service")
            
            // Check if there was music playing before reboot
            val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            val lastSongUri = prefs.getString("last_song_uri", null)
            
            if (lastSongUri != null) {
                Log.d(TAG, "🎵 Last song found, starting service in background")
                
                // Start the service (it will restore its state automatically)
                val serviceIntent = Intent(context, PlayerService::class.java)
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "✅ Service started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start service on boot", e)
                }
            } else {
                Log.d(TAG, "ℹ️ No previous playback, not starting service")
            }
        }
    }
}