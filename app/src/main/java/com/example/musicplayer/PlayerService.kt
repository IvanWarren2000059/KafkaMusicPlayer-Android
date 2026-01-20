package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var mediaSessionCompat: MediaSessionCompat
    private lateinit var prefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    
    private var isShuffled = false
    private var isIsolateMode = false
    private var isolatedSong: Song? = null
    private var currentSongList: List<Song> = emptyList()

    companion object {
        private const val TAG = "PlayerService"
        
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PLAY_PAUSE = "PLAY_PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREV = "PREV"
        const val ACTION_SHUFFLE = "SHUFFLE"
        const val ACTION_SHUFFLE_TOGGLE = "SHUFFLE_TOGGLE"
        const val ACTION_STOP = "STOP"
        
        const val EXTRA_INDEX = "INDEX"
        const val EXTRA_ISOLATE_MODE = "ISOLATE_MODE"
        const val EXTRA_SHUFFLE_STATE = "SHUFFLE_STATE"
        
        private const val CHANNEL_ID = "kafka_player_channel"
        private const val NOTIFICATION_ID = 1
        
        private const val PREFS_NAME = "player_prefs"
        private const val KEY_LAST_SONG_URI = "last_song_uri"
        private const val KEY_LAST_SONG_INDEX = "last_song_index"
        private const val KEY_SHUFFLE_STATE = "shuffle_state"
        
        private const val RESTART_THRESHOLD_MS = 3000L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== SERVICE CREATED ==========")
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = getSystemService(NotificationManager::class.java)
        
        createNotificationChannel()
        initializePlayer()
        initializeMediaSessions()
        restoreState()
        
        Log.d(TAG, "Service initialization complete")
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        
        player.addListener(object : PlayerListenerAdapter() {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "▶️ Playback state: ${if (isPlaying) "PLAYING" else "PAUSED"}")
                updateMediaSessionState()
                updateNotification()
                updateWidget()
                broadcastPlaybackState(isPlaying)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "⏹️ Track ended")
                        if (!isIsolateMode) playNext()
                    }
                }
            }
        })
    }

    private fun initializeMediaSessions() {
        // MediaSession for modern Android
        session = MediaSession.Builder(this, player).build()
        
        // MediaSessionCompat for notification controls
        mediaSessionCompat = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "📱 MediaSession: Play")
                    handlePlayPauseAction()
                }
                
                override fun onPause() {
                    Log.d(TAG, "📱 MediaSession: Pause")
                    handlePlayPauseAction()
                }
                
                override fun onSkipToNext() {
                    Log.d(TAG, "📱 MediaSession: Next")
                    handleNextAction()
                }
                
                override fun onSkipToPrevious() {
                    Log.d(TAG, "📱 MediaSession: Previous")
                    handlePrevAction()
                }
                
                override fun onStop() {
                    Log.d(TAG, "📱 MediaSession: Stop")
                    handleStopAction()
                }
            })
            isActive = true
        }
        
        updateMediaSessionState()
        Log.d(TAG, "Media sessions initialized")
    }

    private fun updateMediaSessionState() {
        val state = if (player.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .build()
        
        mediaSessionCompat.setPlaybackState(playbackState)
    }

    private fun restoreState() {
        isShuffled = prefs.getBoolean(KEY_SHUFFLE_STATE, false)
        Log.d(TAG, "🔀 Restored shuffle state: $isShuffled")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "📥 ACTION RECEIVED: $action")
        Log.d(TAG, "========================================")
        
        when (action) {
            ACTION_PLAY -> handlePlayAction(intent)
            ACTION_PLAY_PAUSE -> handlePlayPauseAction()
            ACTION_NEXT -> handleNextAction()
            ACTION_PREV -> handlePrevAction()
            ACTION_SHUFFLE_TOGGLE -> handleShuffleToggle()
            ACTION_SHUFFLE -> handleShuffleSet(intent)
            ACTION_STOP -> handleStopAction()
            else -> Log.w(TAG, "⚠️ Unknown action: $action")
        }
        
        return START_STICKY
    }

    // ==================== Action Handlers ====================
    
    private fun handlePlayAction(intent: Intent) {
        val index = intent.getIntExtra(EXTRA_INDEX, 0)
        isIsolateMode = intent.getBooleanExtra(EXTRA_ISOLATE_MODE, false)
        
        Log.d(TAG, "▶️ Play: index=$index, isolate=$isIsolateMode")
        
        currentSongList = scanSongs(this)
        
        if (currentSongList.isEmpty()) {
            Log.w(TAG, "⚠️ No songs found")
            return
        }
        
        if (isIsolateMode) {
            isolatedSong = currentSongList.getOrNull(index)
            isolatedSong?.let {
                PlaybackQueue.set(listOf(it), 0)
                playCurrent()
            }
        } else {
            isolatedSong = null
            setupQueueAndPlay(index)
        }
    }

    private fun handlePlayPauseAction() {
        Log.d(TAG, "⏯️ Play/Pause (currently: ${if (player.isPlaying) "playing" else "paused"})")
        
        if (player.isPlaying) {
            player.pause()
        } else {
            if (PlaybackQueue.current() == null) {
                playLastOrFirstSong()
            } else {
                player.play()
            }
        }
    }

    private fun handleNextAction() {
        Log.d(TAG, "⏭️ Next (isolate=$isIsolateMode)")
        
        if (isIsolateMode) {
            Log.d(TAG, "❌ Ignored in isolate mode")
            return
        }
        
        playNext()
    }

    private fun handlePrevAction() {
        val pos = player.currentPosition
        Log.d(TAG, "⏮️ Previous (position=${pos}ms, isolate=$isIsolateMode)")
        
        if (isIsolateMode) {
            Log.d(TAG, "🔄 Isolate mode: restarting")
            player.seekTo(0)
            player.play()
            return
        }
        
        if (pos > RESTART_THRESHOLD_MS) {
            Log.d(TAG, "🔄 >3s: Restarting current track")
            player.seekTo(0)
            player.play()
        } else {
            Log.d(TAG, "⬅️ <3s: Going to previous track")
            playPrevious()
        }
    }

    private fun handleShuffleToggle() {
        isShuffled = !isShuffled
        saveShuffleState()
        Log.d(TAG, "🔀 Shuffle toggled: $isShuffled")
        
        if (!isIsolateMode) reshuffleQueue()
        
        updateMediaSessionState()
        updateNotification()
        broadcastPlaybackState(player.isPlaying)
    }

    private fun handleShuffleSet(intent: Intent) {
        isShuffled = intent.getBooleanExtra(EXTRA_SHUFFLE_STATE, false)
        saveShuffleState()
        Log.d(TAG, "🔀 Shuffle set: $isShuffled")
        
        if (!isIsolateMode) reshuffleQueue()
    }

    private fun handleStopAction() {
        Log.d(TAG, "⏹️ Stopping service")
        player.stop()
        mediaSessionCompat.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ==================== Playback Logic ====================
    
    private fun setupQueueAndPlay(startIndex: Int) {
        val queue = if (isShuffled) currentSongList.shuffled() else currentSongList
        val adjustedIndex = if (isShuffled) 0 else startIndex.coerceIn(0, queue.size - 1)
        
        PlaybackQueue.set(queue, adjustedIndex)
        playCurrent()
    }

    private fun reshuffleQueue() {
        if (currentSongList.isEmpty()) {
            currentSongList = scanSongs(this)
        }
        
        val currentSong = PlaybackQueue.current()
        val newQueue = if (isShuffled) currentSongList.shuffled() else currentSongList
        val newIndex = currentSong?.let { song ->
            newQueue.indexOfFirst { it.uri == song.uri }
        } ?: 0
        
        PlaybackQueue.set(newQueue, maxOf(0, newIndex))
        Log.d(TAG, "🔀 Queue reshuffled: ${newQueue.size} songs")
    }

    private fun playCurrent() {
        val song = PlaybackQueue.current() ?: return
        
        Log.d(TAG, "🎵 Now playing: ${song.title} - ${song.artist}")
        
        saveLastPlayedSong(song)
        
        val mediaItem = MediaItem.fromUri(song.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        
        startForeground(NOTIFICATION_ID, createNotification(song))
        updateWidget()
    }

    private fun playNext() {
        PlaybackQueue.next()?.let {
            Log.d(TAG, "⏭️ Next: ${it.title}")
            playCurrent()
        }
    }

    private fun playPrevious() {
        PlaybackQueue.previous()?.let {
            Log.d(TAG, "⏮️ Previous: ${it.title}")
            playCurrent()
        }
    }

    private fun playLastOrFirstSong() {
        val songs = scanSongs(this)
        if (songs.isEmpty()) return
        
        val lastSongUri = prefs.getString(KEY_LAST_SONG_URI, null)
        val songToPlay = lastSongUri?.let { uri ->
            songs.find { it.uri.toString() == uri }
        } ?: songs.first()
        
        val index = songs.indexOf(songToPlay)
        currentSongList = songs
        PlaybackQueue.set(songs, index)
        playCurrent()
    }

    // ==================== State Management ====================
    
    private fun saveLastPlayedSong(song: Song) {
        prefs.edit()
            .putString(KEY_LAST_SONG_URI, song.uri.toString())
            .putInt(KEY_LAST_SONG_INDEX, currentSongList.indexOf(song))
            .apply()
    }

    private fun saveShuffleState() {
        prefs.edit().putBoolean(KEY_SHUFFLE_STATE, isShuffled).apply()
    }

    // ==================== Notifications ====================
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kafka Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(song: Song): Notification {
        Log.d(TAG, "🔔 Creating notification")
        
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (player.isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(if (isShuffled) "🔀 Shuffle" else "Kafka Player")
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_previous,
                    "Previous",
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    playPauseIcon,
                    playPauseText,
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_next,
                    "Next",
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                ).build()
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSessionCompat.sessionToken)
            )
            .build()
    }

    private fun updateNotification() {
        val song = PlaybackQueue.current() ?: return
        val notification = createNotification(song)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // ==================== Broadcasting & Widget ====================
    
    private fun broadcastPlaybackState(isPlaying: Boolean) {
        val song = PlaybackQueue.current()
        val intent = Intent(MainActivity.ACTION_UPDATE_UI).apply {
            putExtra(MainActivity.EXTRA_SONG_TITLE, song?.title)
            putExtra(MainActivity.EXTRA_SONG_ARTIST, song?.artist)
            putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateWidget() {
        val song = PlaybackQueue.current()
        MusicWidget.updateWidget(
            this,
            song?.title ?: "No song playing",
            song?.artist ?: "Unknown Artist",
            player.isPlaying
        )
    }

    // ==================== Lifecycle ====================
    
    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        Log.d(TAG, "========== SERVICE DESTROYED ==========")
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        player.release()
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}