package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var widgetPulsator: WidgetPulsator

    private var isShuffled = false
    private var isIsolateMode = false
    private var isolatedSong: Song? = null
    private var currentSongList: List<Song> = emptyList()
    
    // Visualizer for beat-synced thread animation
    private var visualizer: Visualizer? = null
    
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "PlayerService"
        private const val WAKE_LOCK_TAG = "MusicPlayer:WakeLock"
        const val EXTRA_SONG_URI = "SONG_URI"
        const val EXTRA_SONG_TITLE = "SONG_TITLE"
        const val EXTRA_SONG_ARTIST = "SONG_ARTIST"
        const val ACTION_DISMISS = "DISMISS"

        const val ACTION_PLAY = "PLAY"
        const val ACTION_PLAY_PAUSE = "PLAY_PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREV = "PREV"
        const val ACTION_SHUFFLE = "SHUFFLE"
        const val ACTION_SHUFFLE_TOGGLE = "SHUFFLE_TOGGLE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SEEK = "SEEK"
        const val ACTION_REQUEST_PROGRESS = "REQUEST_PROGRESS"
        
        
        // Visualizer broadcast
        const val ACTION_VISUALIZER_DATA = "VISUALIZER_DATA"
        const val EXTRA_BASS_INTENSITY = "BASS_INTENSITY"      // 0-100, even threads (steady glow on small dB changes)
        const val EXTRA_TREBLE_INTENSITY = "TREBLE_INTENSITY"  // 0-100, odd threads (intense glow on 3+ dB spikes)
        const val EXTRA_BPM = "BPM"                            // 60-200, glow pulse rate
        
        const val EXTRA_INDEX = "INDEX"
        const val EXTRA_ISOLATE_MODE = "ISOLATE_MODE"
        const val EXTRA_SHUFFLE_STATE = "SHUFFLE_STATE"
        const val EXTRA_SEEK_POSITION = "SEEK_POSITION"
        
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
        
        // Initialize wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        )
        wakeLock.setReferenceCounted(false)
        
        createNotificationChannel()
        initializePlayer()
        initializeMediaSessions()
        restoreState()
            widgetPulsator = WidgetPulsator(this)

        Log.d(TAG, "Service initialization complete")
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).apply {
            setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
            setHandleAudioBecomingNoisy(true)
        }.build()
        
        player.addListener(object : PlayerListenerAdapter() {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "▶️ Playback state: ${if (isPlaying) "PLAYING" else "PAUSED"}")
                
                if (isPlaying) {
                    acquireWakeLock()
                    widgetPulsator.startPulsation()
                    startVisualizer()
                } else {
                    stopVisualizer()
                }
                
                updateMediaSessionState()
                updateNotification()
                updateWidget()
                broadcastPlaybackState(isPlaying)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "🎵 Playback state changed: $playbackState")
                
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "⏹️ Track ended")
                        stopVisualizer()
                        if (!isIsolateMode) {
                            acquireWakeLock()
                            handler.post {
                                Log.d(TAG, "🔄 Handler: Playing next song")
                                playNext()
                            }
                        } else {
                            releaseWakeLock()
                        }
                    }
                    Player.STATE_READY -> {
                        Log.d(TAG, "✅ Player ready")
                        if (player.playWhenReady) {
                            acquireWakeLock()
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "⏳ Buffering")
                        acquireWakeLock()
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "💤 Player idle")
                        if (!player.playWhenReady) {
                            releaseWakeLock()
                            widgetPulsator.stopPulsation()
                            stopVisualizer()
                        }
                    }
                }
            }
        })
    }

    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(60000) // 60 second timeout
                Log.d(TAG, "🔒 Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "🔓 Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release wake lock", e)
        }
    }


    // ========== VISUALIZER ==========
    
    private fun startVisualizer() {
        if (visualizer != null) {
            visualizer?.enabled = true
            Log.d(TAG, "🎵 Visualizer resumed")
            return
        }
        
        try {
            val audioSessionId = player.audioSessionId
            
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    private var frameCount = 0
                    
                    // BPM detection
                    private val beatHistory = mutableListOf<Long>()
                    private var lastBeatTime = 0L
                    private var detectedBpm = 120.0 // Default BPM
                    
                    // Energy smoothing for baseline calculation
                    private var previousBassEnergy = 0.0
                    private val bassEnergyHistory = mutableListOf<Double>()
                    private val energyThreshold = 1.3 // Beat detected when energy spikes 30% above average
                    
                    // 5-second average intensity tracking (for new baseline system)
                    private val intensityHistory = mutableListOf<Double>()
                    private val HISTORY_SIZE = 150 // ~5 seconds at ~30 fps capture rate
                    private var fiveSecondAverage = 0.0
                    private var baselineIntensity = 0.0 // -5 less than 5-second average
                    
                    override fun onWaveFormDataCapture(
                        v: Visualizer?, 
                        waveform: ByteArray?, 
                        samplingRate: Int
                    ) {
                        // Not used - we use FFT instead for frequency analysis
                    }
                    
                    override fun onFftDataCapture(
                        v: Visualizer?, 
                        fft: ByteArray?, 
                        samplingRate: Int
                    ) {
                        if (fft == null) return
                        
                        // ========== FREQUENCY ANALYSIS (Hz) ==========
                        var bassEnergy = 0.0    // 20-250 Hz (kick drum, bass) -> EVEN THREADS baseline
                        var midEnergy = 0.0     // 250-2000 Hz (vocals, melody)
                        var trebleEnergy = 0.0  // 2000-8000 Hz (cymbals, hi-hats) -> ODD THREADS spikes
                        
                        val fftSize = fft.size / 2
                        val binSize = samplingRate / 2.0 / fftSize // Hz per bin
                        
                        // Analyze frequency bins
                        for (i in 0 until fftSize step 2) {
                            val magnitude = Math.sqrt(
                                (fft[i].toDouble() * fft[i].toDouble() + 
                                 fft[i + 1].toDouble() * fft[i + 1].toDouble())
                            )
                            
                            val frequency = i * binSize / 2
                            
                            when {
                                frequency < 250 -> bassEnergy += magnitude
                                frequency < 2000 -> midEnergy += magnitude
                                frequency < 8000 -> trebleEnergy += magnitude
                            }
                        }
                        
                        // ========== TRACK AVERAGE BASS ENERGY (EVEN THREAD BASELINE) ==========
                        bassEnergyHistory.add(bassEnergy)
                        if (bassEnergyHistory.size > 30) { // Keep last ~1 second of data
                            bassEnergyHistory.removeAt(0)
                        }
                        val averageBassEnergy = if (bassEnergyHistory.size > 0) {
                            bassEnergyHistory.average()
                        } else {
                            bassEnergy
                        }
                        
                        // ========== BPM DETECTION ==========
                        val currentTime = System.currentTimeMillis()
                        
                        if (bassEnergy > previousBassEnergy * energyThreshold) {
                            if (currentTime - lastBeatTime > 300) { // Minimum 300ms between beats
                                beatHistory.add(currentTime)
                                
                                if (beatHistory.size > 8) {
                                    beatHistory.removeAt(0)
                                }
                                
                                if (beatHistory.size >= 4) {
                                    val intervals = mutableListOf<Long>()
                                    for (i in 1 until beatHistory.size) {
                                        intervals.add(beatHistory[i] - beatHistory[i - 1])
                                    }
                                    val averageInterval = intervals.average()
                                    detectedBpm = (60000.0 / averageInterval).coerceIn(60.0, 200.0)
                                }
                                
                                lastBeatTime = currentTime
                            }
                        }
                        
                        previousBassEnergy = bassEnergy
                        
                        // ========== DECIBEL-BASED INTENSITY CALCULATION ==========
                        // Calculate total magnitude across all frequencies
                        val totalEnergy = bassEnergy + midEnergy + trebleEnergy
                        
                        // Convert to decibels (dB)
                        // dB = 20 * log10(magnitude / reference)
                        // Using a reference value to normalize
                        val referenceLevel = 1.0
                        val currentDb = if (totalEnergy > 0) {
                            20 * Math.log10(totalEnergy / referenceLevel)
                        } else {
                            -96.0 // Silence threshold
                        }
                        
                        // Track previous dB level
                        val previousDb = if (intensityHistory.isNotEmpty()) {
                            intensityHistory.last()
                        } else {
                            currentDb
                        }
                        
                        // Calculate dB change from previous frame
                        val dbChange = Math.abs(currentDb - previousDb)
                        
                        // Store current dB in history (we use this to track previous)
                        intensityHistory.add(currentDb)
                        if (intensityHistory.size > HISTORY_SIZE) {
                            intensityHistory.removeAt(0)
                        }
                        
                        // Calculate 5-second average dB for reference
                        fiveSecondAverage = if (intensityHistory.size >= 30) {
                            intensityHistory.average()
                        } else {
                            currentDb
                        }
                        
                        // Normalize dB to 0-100 range for display
                        // Typical range: -60 dB (quiet) to 0 dB (loud)
                        val normalizedIntensity = ((currentDb + 60.0) / 60.0 * 100.0).coerceIn(0.0, 100.0)
                        
                        // ========== DETERMINE WHICH THREADS GLOW BASED ON dB CHANGE ==========
                        val evenThreadIntensity: Double
                        val oddThreadIntensity: Double
                        
                        if (dbChange >= 3.0) {
                            // LARGE CHANGE (3+ dB): Odd threads glow INTENSELY
                            oddThreadIntensity = (normalizedIntensity * 1.5).coerceIn(50.0, 100.0) // Intense glow
                            evenThreadIntensity = (normalizedIntensity * 0.15).coerceIn(5.0, 15.0) // Very dim
                        } else {
                            // SMALL CHANGE (< 3 dB): Even threads glow, odd threads stay dim/off
                            evenThreadIntensity = (normalizedIntensity * 0.6).coerceIn(20.0, 60.0) // Moderate glow
                            oddThreadIntensity = 0.0 // Odd threads OFF
                        }
                        
                        // Broadcast to UI
                        broadcastVisualizerData(evenThreadIntensity, oddThreadIntensity, detectedBpm)
                        
                        // Debug logging
                        frameCount++
                        if (frameCount % 30 == 0) {
                            val mode = if (dbChange >= 3.0) "ODD (Intense)" else "EVEN (Steady)"
                            Log.d(TAG, "🎵 dB: ${String.format("%.1f", currentDb)} " +
                                       "| ΔdB: ${String.format("%.2f", dbChange)} ($mode) " +
                                       "| Even: ${String.format("%.1f", evenThreadIntensity)}% " +
                                       "| Odd: ${String.format("%.1f", oddThreadIntensity)}% " +
                                       "| BPM: ${String.format("%.0f", detectedBpm)}")
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true) // Enable FFT capture
                
                enabled = true
                Log.d(TAG, "✅ Visualizer started - Session: $audioSessionId, Capture: $captureSize, Rate: ${Visualizer.getMaxCaptureRate() / 2} Hz")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Visualizer failed: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun stopVisualizer() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        Log.d(TAG, "🛑 Visualizer stopped")
    }
    
    private fun broadcastVisualizerData(bassIntensity: Double, trebleIntensity: Double, bpm: Double) {
        val intent = Intent(ACTION_VISUALIZER_DATA).apply {
            putExtra(EXTRA_BASS_INTENSITY, bassIntensity)
            putExtra(EXTRA_TREBLE_INTENSITY, trebleIntensity)
            putExtra(EXTRA_BPM, bpm)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    private fun initializeMediaSessions() {
        session = MediaSession.Builder(this, player).build()
        
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
        super.onStartCommand(intent, flags, startId)
        
        val action = intent?.action
        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔥 ACTION RECEIVED: $action")
        Log.d(TAG, "========================================")
        
        when (action) {
            ACTION_PLAY -> handlePlayAction(intent)
            ACTION_PLAY_PAUSE -> handlePlayPauseAction()
            ACTION_NEXT -> handleNextAction()
            ACTION_PREV -> handlePrevAction()
            ACTION_SHUFFLE_TOGGLE -> handleShuffleToggle()
            ACTION_SHUFFLE -> handleShuffleSet(intent)
            ACTION_SEEK -> handleSeekAction(intent)
            ACTION_REQUEST_PROGRESS -> broadcastProgress()
            ACTION_STOP -> handleStopAction()
            ACTION_DISMISS -> handleDismissAction()  // ✅ NEW

            else -> Log.w(TAG, "⚠️ Unknown action: $action")
        }
        
        return START_STICKY
    }

    // ==================== Action Handlers ====================

  private fun handlePlayAction(intent: Intent) {
    isIsolateMode = intent.getBooleanExtra(EXTRA_ISOLATE_MODE, false)
    
    val songUriString = intent.getStringExtra(EXTRA_SONG_URI)
    val songTitle = intent.getStringExtra(EXTRA_SONG_TITLE)
    val songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST)
    
    Log.d(TAG, "▶️ Play: URI=$songUriString, isolate=$isIsolateMode")
    
    currentSongList = scanSongs(this)
    
    Log.d(TAG, "📚 Total songs available: ${currentSongList.size}")
    
    val requestedSong = if (songUriString != null) {
        // First try to find in scanned songs
        var song = currentSongList.find { it.uri.toString() == songUriString }
        
        // If not found (e.g., SD card file opened from file manager), create Song object from URI
        if (song == null && songTitle != null) {
            Log.d(TAG, "🆕 Song not in library, creating from external file")
            song = Song(
                title = songTitle,
                artist = songArtist ?: "Unknown Artist",
                uri = android.net.Uri.parse(songUriString)
            )
        }
        
        song
    } else {
        val index = intent.getIntExtra(EXTRA_INDEX, 0)
        currentSongList.getOrNull(index)
    }
    
    if (requestedSong == null) {
        Log.w(TAG, "⚠️ Requested song not found")
        return
    }
    
    Log.d(TAG, "🎯 User requested: ${requestedSong.title} by ${requestedSong.artist}")
    Log.d(TAG, "📁 URI: ${requestedSong.uri}")
    
    if (isIsolateMode) {
        isolatedSong = requestedSong
        PlaybackQueue.set(listOf(requestedSong), 0)
        Log.d(TAG, "🎯 Isolate mode: Playing only this song")
        playCurrent()
    } else {
        isolatedSong = null
        setupQueueAndPlaySong(requestedSong)
    }
}

   private fun handlePlayPauseAction() {
    Log.d(TAG, "⏯️ Play/Pause (currently: ${if (player.isPlaying) "playing" else "paused"})")
    
    if (player.isPlaying) {
        player.pause()
        releaseWakeLock()
        
        handler.postDelayed({
            updateNotification()
        }, 100)
        
    } else {
        if (PlaybackQueue.current() == null) {
            Log.d(TAG, "🆕 No song in queue, loading last played or first song")
            playLastOrFirstSong()
        } else {
            Log.d(TAG, "▶️ Resuming playback")
            player.play()
            acquireWakeLock()
            
            handler.postDelayed({
                updateNotification()
            }, 100)
        }
    }
}

    private fun handleDismissAction() {
    Log.d(TAG, "🗑️ Notification dismissed")
    
    // If playing, pause first
    if (player.isPlaying) {
        player.pause()
        releaseWakeLock()
    }
    
    // Remove notification but keep service alive
    stopForeground(STOP_FOREGROUND_REMOVE)
    
    // Broadcast pause state
    broadcastPlaybackState(false)
    updateWidget()
    
    Log.d(TAG, "✅ Notification dismissed, service still running in background")
}
    private fun handleNextAction() {
        Log.d(TAG, "⏭️ Next (isolate=$isIsolateMode)")
        
        if (isIsolateMode) {
            Log.d(TAG, "❌ Ignored in isolate mode")
            return
        }
        
        acquireWakeLock()
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
            acquireWakeLock()
            playPrevious()
        }
    }

    private fun handleShuffleToggle() {
        isShuffled = !isShuffled
        saveShuffleState()
        Log.d(TAG, "🔀 Shuffle toggled: $isShuffled")
        
        if (!isIsolateMode && PlaybackQueue.current() != null) {
            reshuffleQueue()
        }
        
        updateMediaSessionState()
        updateNotification()
        broadcastPlaybackState(player.isPlaying)
    }

    private fun handleShuffleSet(intent: Intent) {
        isShuffled = intent.getBooleanExtra(EXTRA_SHUFFLE_STATE, false)
        saveShuffleState()
        Log.d(TAG, "🔀 Shuffle set: $isShuffled")
        
        if (!isIsolateMode && PlaybackQueue.current() != null) {
            reshuffleQueue()
        }
    }

    private fun handleStopAction() {
        Log.d(TAG, "⏹️ Stopping service")
        player.stop()
        releaseWakeLock()
        mediaSessionCompat.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        broadcastPlaybackState(false)
        updateWidget()
        
        stopSelf()
    }
    
    private fun handleSeekAction(intent: Intent) {
        val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
        Log.d(TAG, "⏩ Seeking to: ${position}ms")
        if (player.duration > 0) {
            player.seekTo(position.toLong())
        }
        broadcastProgress()
    }

    // ==================== Playback Logic ====================
    
    private fun setupQueueAndPlaySong(requestedSong: Song) {
        Log.d(TAG, "🔧 Setting up queue for: ${requestedSong.title}")
        
        val queue = if (isShuffled) {
            Log.d(TAG, "🔀 Shuffling queue...")
            currentSongList.shuffled()
        } else {
            Log.d(TAG, "📋 Sequential queue")
            currentSongList
        }
        
        val songIndex = queue.indexOfFirst { it.uri == requestedSong.uri }
        
        if (songIndex == -1) {
            Log.e(TAG, "❌ ERROR: Requested song not found in queue!")
            Log.e(TAG, "   Looking for URI: ${requestedSong.uri}")
            PlaybackQueue.set(queue, 0)
        } else {
            Log.d(TAG, "✅ Found song at queue position: $songIndex")
            PlaybackQueue.set(queue, songIndex)
        }
        
        Log.d(TAG, "📋 Queue size: ${queue.size}, Starting at: $songIndex")
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
        val song = PlaybackQueue.current()
        
        if (song == null) {
            Log.e(TAG, "❌ ERROR: PlaybackQueue.current() returned null!")
            return
        }
        
        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🎵 NOW PLAYING")
        Log.d(TAG, "   Title: ${song.title}")
        Log.d(TAG, "   Artist: ${song.artist}")
        Log.d(TAG, "   URI: ${song.uri}")
        Log.d(TAG, "========================================")
        
        saveLastPlayedSong(song)
        
        player.stop()
        
        val notification = createNotification(song)
        startForeground(NOTIFICATION_ID, notification)
        
        val mediaItem = MediaItem.fromUri(song.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        
        handler.postDelayed({
            player.playWhenReady = true
            Log.d(TAG, "▶️ Playback started")
            
            updateWidget()
            broadcastPlaybackState(true)
        }, 100)
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
        Log.d(TAG, "🔍 Initializing playback...")
        
        val songs = scanSongs(this)
        if (songs.isEmpty()) {
            Log.w(TAG, "⚠️ No songs found on device")
            return
        }
        
        Log.d(TAG, "📚 Found ${songs.size} songs")
        
        val lastSongUri = prefs.getString(KEY_LAST_SONG_URI, null)
        val songToPlay = if (lastSongUri != null) {
            val found = songs.find { it.uri.toString() == lastSongUri }
            if (found != null) {
                Log.d(TAG, "💿 Restoring last played: ${found.title}")
                found
            } else {
                Log.d(TAG, "⚠️ Last played song not found, using first song")
                songs.first()
            }
        } else {
            Log.d(TAG, "🆕 No previous playback, using first song")
            songs.first()
        }
        
        currentSongList = songs
        
        val queue = if (isShuffled) songs.shuffled() else songs
        val index = queue.indexOfFirst { it.uri == songToPlay.uri }
        
        PlaybackQueue.set(queue, maxOf(0, index))
        
        Log.d(TAG, "✅ Initialized with: ${songToPlay.title} at index $index")
        playCurrent()
    }
    
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
                "Kafka's Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kafka's elegant music playback controls"
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#E84393")
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

   private fun createNotification(song: Song): Notification {
    Log.d(TAG, "🔔 Creating Kafka notification for: ${song.title}")
    
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAppPendingIntent = PendingIntent.getActivity(
        this, 0, openAppIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // ✅ NEW: Separate action for swipe-to-dismiss
    val deleteIntent = Intent(this, PlayerService::class.java).apply {
        action = ACTION_DISMISS  // ✅ New dismiss action
    }
    val deletePendingIntent = PendingIntent.getService(
        this, 1, deleteIntent,  // Different request code (1 instead of 0)
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val playPauseIcon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
    val playPauseText = if (player.isPlaying) "Pause" else "Play"
    
    val kafkaArtwork = if (player.isPlaying) {
        BitmapFactory.decodeResource(resources, R.drawable.kafka_playing)
    } else {
        BitmapFactory.decodeResource(resources, R.drawable.kafka_idle)
    }
    
    val subtitle = when {
        isIsolateMode -> "🎯 Isolated Playback"
        isShuffled -> "🔀 Shuffle Mode"
        else -> "♠️ Sequential"
    }

    val kafkaThreadColor = android.graphics.Color.parseColor("#E84393")
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_play)
        .setLargeIcon(kafkaArtwork)
        .setContentTitle(song.title)
        .setContentText(song.artist)
        .setSubText(subtitle)
        .setContentIntent(openAppPendingIntent)
        .setDeleteIntent(deletePendingIntent)  // ✅ Swipe dismisses notification
        .setOngoing(player.isPlaying)  // ✅ Only dismissible when paused
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setColor(kafkaThreadColor)
        .setColorized(false)
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
        
        if (!player.isPlaying) {
            Log.d(TAG, "⏸️ Paused - notification is dismissible")
            stopForeground(STOP_FOREGROUND_DETACH)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            Log.d(TAG, "▶️ Playing - notification is persistent")
            startForeground(NOTIFICATION_ID, notification)
        }
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
    
    private fun broadcastProgress() {
        val currentPos = if (player.duration > 0) player.currentPosition.toInt() else 0
        val duration = if (player.duration > 0) player.duration.toInt() else 0
        
        val intent = Intent(MainActivity.ACTION_UPDATE_PROGRESS).apply {
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, currentPos)
            putExtra(MainActivity.EXTRA_DURATION, duration)
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

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        
        Log.d(TAG, "📱 Task removed - keeping service alive")
        
        if (player.isPlaying) {
            Log.d(TAG, "▶️ Still playing, service will continue")
            
            val song = PlaybackQueue.current()
            if (song != null) {
                val notification = createNotification(song)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    // ==================== Lifecycle ====================
    
    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        Log.d(TAG, "========== SERVICE DESTROYED ==========")
        releaseWakeLock()
        stopVisualizer()
        handler.removeCallbacksAndMessages(null)
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        player.release()
        session.release()
        super.onDestroy()
        widgetPulsator.stopPulsation()

    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}