package com.example.musicplayer
import android.media.audiofx.Visualizer
import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator


class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var nowPlaying: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var shuffleBtn: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var fabAddPlaylist: FloatingActionButton
    
    private lateinit var kafkaIdle: ImageView
    private lateinit var kafkaPlaying: ImageView
    private lateinit var spiderWebBg: ImageView
    
    private var currentSongUri: Uri? = null
    private var songList: List<Song> = emptyList()
    private var isShuffled = false
    private var isPlaying = false
    private var currentSong: Song? = null
    
    // Idle breathing animation
    private var idleGlowAnimator: ValueAnimator? = null
    
    // Kafka pulsating animations
    private var idlePulseAnimator: ValueAnimator? = null
    private var playingPulseAnimator: ValueAnimator? = null
    
    private val seekBarHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    companion object {
        private const val REQUEST_PERMISSION = 101
        private const val REQUEST_RECORD_AUDIO = 102
        const val DELETE_REQUEST_CODE = 1001
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
        
        const val ACTION_UPDATE_UI = "UPDATE_UI"
        const val ACTION_UPDATE_PROGRESS = "UPDATE_PROGRESS"
        const val EXTRA_SONG_TITLE = "SONG_TITLE"
        const val EXTRA_SONG_ARTIST = "SONG_ARTIST"
        const val EXTRA_IS_PLAYING = "IS_PLAYING"
        const val EXTRA_CURRENT_POSITION = "CURRENT_POSITION"
        const val EXTRA_DURATION = "DURATION"
        
        private const val KAFKA_ANIM_DURATION = 300L
        
        // Kafka pulsating
        private const val PULSE_DURATION = 2000L
        private const val PULSE_SCALE_MIN = 0.95f
        private const val PULSE_SCALE_MAX = 1.05f
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_UI -> {
                    val title = intent.getStringExtra(EXTRA_SONG_TITLE)
                    val artist = intent.getStringExtra(EXTRA_SONG_ARTIST)
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    
                    updateNowPlaying(title, artist)
                    updatePlayPauseButton()
                    updateKafkaAnimation(isPlaying)
                    updateThreadVisualization(isPlaying)
                }
                ACTION_UPDATE_PROGRESS -> {
                    val position = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
                    val duration = intent.getIntExtra(EXTRA_DURATION, 0)
                    
                    if (!isUserSeeking && duration > 0) {
                        seekBar.max = duration
                        seekBar.progress = position
                    }
                }
                PlayerService.ACTION_VISUALIZER_DATA -> {
                    val evenThreadIntensity = intent.getDoubleExtra(PlayerService.EXTRA_BASS_INTENSITY, 0.0)
                    val oddThreadIntensity = intent.getDoubleExtra(PlayerService.EXTRA_TREBLE_INTENSITY, 0.0)
                    val bpm = intent.getDoubleExtra(PlayerService.EXTRA_BPM, 120.0)
                    updateThreadsWithBeat(evenThreadIntensity, oddThreadIntensity, bpm)
                }
            }
        }
    }

    private val seekBarRunnable = object : Runnable {
        override fun run() {
            val intent = Intent(this@MainActivity, PlayerService::class.java).apply {
                action = PlayerService.ACTION_REQUEST_PROGRESS
            }
            startService(intent)
            
            seekBarHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        requestBatteryOptimizationIfNeeded()
        setupListeners()

        // Start idle breathing glow immediately
        startIdleGlow()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE_UI)
            addAction(ACTION_UPDATE_PROGRESS)
            addAction(PlayerService.ACTION_VISUALIZER_DATA)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)

        if (hasStoragePermission()) {
            initApp()
            handleIncomingIntent(intent)
            // Request RECORD_AUDIO for visualizer
            requestRecordAudioPermissionIfNeeded()
        } else {
            requestStoragePermission()
        }
        
        seekBarHandler.post(seekBarRunnable)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }
    
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    playExternalAudioFile(uri)
                }
            }
        }
    }
    
    private fun playExternalAudioFile(uri: Uri) {
        try {
            val song = getSongFromUri(uri)
            
            if (song != null) {
                Toast.makeText(
                    this,
                    "Playing: ${song.title}",
                    Toast.LENGTH_SHORT
                ).show()
                
                playSong(song, isolateMode = true)
            } else {
                Toast.makeText(
                    this,
                    "Could not play this file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing external file", e)
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Enhanced getSongFromUri with multiple fallback methods
     * Handles files from SD card, external storage, and various file managers
     */
    private fun getSongFromUri(uri: Uri): Song? {
        return try {
            // Method 1: Try MediaStore query first (works for files in MediaStore)
            val mediaStoreSong = getSongFromMediaStore(uri)
            if (mediaStoreSong != null) {
                Log.d("MainActivity", "Got song from MediaStore: ${mediaStoreSong.title}")
                return mediaStoreSong
            }
            
            // Method 2: Try MediaMetadataRetriever (works for any accessible file)
            val metadataSong = getSongFromMetadata(uri)
            if (metadataSong != null) {
                Log.d("MainActivity", "Got song from metadata: ${metadataSong.title}")
                return metadataSong
            }
            
            // Method 3: Fallback to filename
            val filenameSong = getSongFromFilename(uri)
            Log.d("MainActivity", "Got song from filename: ${filenameSong.title}")
            filenameSong
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting song from URI", e)
            // Last resort: use URI path as title
            val filename = uri.lastPathSegment ?: "Unknown"
            Song(filename, "Unknown Artist", uri)
        }
    }
    
    /**
     * Try to get song info from MediaStore
     */
    private fun getSongFromMediaStore(uri: Uri): Song? {
        return try {
            val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    
                    val title = if (titleIndex >= 0) {
                        cursor.getString(titleIndex)?.takeIf { it.isNotEmpty() }
                    } else null
                    
                    val artist = if (artistIndex >= 0) {
                        cursor.getString(artistIndex)?.takeIf { it.isNotEmpty() }
                    } else null
                    
                    // Only return if we got actual metadata (not just "Unknown Title")
                    if (title != null && title != "Unknown Title") {
                        Song(title, artist ?: "Unknown Artist", uri)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "MediaStore query failed", e)
            null
        }
    }
    
    /**
     * Try to extract metadata using MediaMetadataRetriever
     * This works for files on SD card and external storage
     */
    private fun getSongFromMetadata(uri: Uri): Song? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotEmpty() && it != "Unknown Title" }
            
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotEmpty() && it != "<unknown>" }
            
            retriever.release()
            
            // Only return if we got actual metadata
            if (title != null) {
                Song(title, artist ?: "Unknown Artist", uri)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "MediaMetadataRetriever failed", e)
            null
        }
    }
    
    /**
     * Extract song info from filename as last resort
     */
    private fun getSongFromFilename(uri: Uri): Song {
        // Try different methods to get filename
        val filename = when {
            // Try lastPathSegment first
            uri.lastPathSegment != null -> uri.lastPathSegment!!
            // Try path
            uri.path != null -> uri.path!!.substringAfterLast('/')
            // Last resort
            else -> "Unknown"
        }
        
        // Clean up the filename
        val cleanName = filename
            .replace(Regex("\\.[^.]+$"), "") // Remove extension
            .replace(Regex("[_-]+"), " ") // Replace underscores/dashes with spaces
            .trim()
        
        return Song(
            title = cleanName.ifEmpty { "Unknown Title" },
            artist = "Unknown Artist",
            uri = uri
        )
    }

    private fun requestBatteryOptimizationIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasShownPrompt = prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)
        
        if (hasShownPrompt) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_battery_optimization, null)
                
                val dialog = AlertDialog.Builder(this, R.style.KafkaDialog)
                    .setView(dialogView)
                    .create()
                
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                
                dialogView.findViewById<TextView>(R.id.openSettingsButton).setOnClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Could not open battery settings",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                dialogView.findViewById<TextView>(R.id.maybeLaterButton).setOnClickListener {
                    dialog.dismiss()
                }
                
                dialog.setOnDismissListener {
                    prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply()
                }
                
                dialog.show()
            }
        }
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        nowPlaying = findViewById(R.id.nowPlaying)
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist)
        playPauseBtn = findViewById(R.id.playPauseBtn)
        prevBtn = findViewById(R.id.prevBtn)
        nextBtn = findViewById(R.id.nextBtn)
        shuffleBtn = findViewById(R.id.shuffleBtn)
        seekBar = findViewById(R.id.seekBar)
        fabAddPlaylist = findViewById(R.id.fabAddPlaylist)
        
        kafkaIdle = findViewById(R.id.kafkaIdle)
        kafkaPlaying = findViewById(R.id.kafkaPlaying)
        spiderWebBg = findViewById(R.id.spiderWebBg)
        
        startIdlePulse()
    }

    private fun setupListeners() {
        playPauseBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PLAY_PAUSE
            }
            startService(intent)
        }

        prevBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PREV
            }
            startService(intent)
        }

        nextBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_NEXT
            }
            startService(intent)
        }

        shuffleBtn.setOnClickListener {
            isShuffled = !isShuffled
            updateShuffleButton()
            
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_SHUFFLE_TOGGLE
                putExtra(PlayerService.EXTRA_SHUFFLE_STATE, isShuffled)
            }
            startService(intent)
            
            Toast.makeText(
                this, 
                if (isShuffled) "🔀 Shuffle ON" else "▶️ Shuffle OFF",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Do nothing during drag
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.let {
                    val intent = Intent(this@MainActivity, PlayerService::class.java).apply {
                        action = PlayerService.ACTION_SEEK
                        putExtra(PlayerService.EXTRA_SEEK_POSITION, it.progress)
                    }
                    startService(intent)
                }
            }
        })
        
        fabAddPlaylist.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentByTag("f1")
            if (fragment is PlaylistsFragment) {
                fragment.showCreatePlaylistDialog()
            }
        }
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1) {
                    fabAddPlaylist.show()
                } else {
                    fabAddPlaylist.hide()
                }
            }
        })
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION)
    }

    private fun requestRecordAudioPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("KafkaThreads", "🎤 Requesting RECORD_AUDIO permission for visualizer")
            ActivityCompat.requestPermissions(
                this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initApp()
                    requestRecordAudioPermissionIfNeeded()
                } else {
                    nowPlaying.text = "Storage permission required"
                }
            }
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("KafkaThreads", "✅ RECORD_AUDIO permission granted")
                    Toast.makeText(this, "🎵 Visualizer enabled!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("KafkaThreads", "❌ RECORD_AUDIO permission denied - visualizer disabled")
                    Toast.makeText(this, "Visualizer requires microphone permission", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Handle delete permission callback
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d("MainActivity", "========================================")
        Log.d("MainActivity", "onActivityResult called")
        Log.d("MainActivity", "requestCode: $requestCode")
        Log.d("MainActivity", "resultCode: $resultCode")
        Log.d("MainActivity", "DELETE_REQUEST_CODE: $DELETE_REQUEST_CODE")
        Log.d("MainActivity", "RESULT_OK: ${Activity.RESULT_OK}")
        Log.d("MainActivity", "RESULT_CANCELED: ${Activity.RESULT_CANCELED}")
        Log.d("MainActivity", "========================================")
        
        if (requestCode == DELETE_REQUEST_CODE) {
            Log.d("MainActivity", "✅ Request code matches DELETE_REQUEST_CODE")
            
            if (resultCode == Activity.RESULT_OK) {
                Log.d("MainActivity", "✅ User approved deletion")
                
                // Get the URI that was pending deletion
                val deletedUri = SongAdapter.pendingDeletionUri
                
                if (deletedUri != null) {
                    Log.d("MainActivity", "📦 Pending deletion URI: $deletedUri")
                    
                    Toast.makeText(this, "🗑️ Song deleted successfully", Toast.LENGTH_SHORT).show()
                    
                    // Update the main song list
                    songList = songList.filter { it.uri.toString() != deletedUri }
                    Log.d("MainActivity", "✅ Song removed from main list. New count: ${songList.size}")
                    
                    // Broadcast deletion so all fragments/adapters can update
                    val intent = Intent(SongAdapter.ACTION_SONG_DELETED).apply {
                        putExtra(SongAdapter.EXTRA_DELETED_URI, deletedUri)
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    Log.d("MainActivity", "📡 Broadcast sent to all adapters")
                    
                    // Also directly update all currently active fragments
                    updateFragmentsAfterDeletion(deletedUri)
                    
                    // Clear the pending deletion
                    SongAdapter.pendingDeletionUri = null
                } else {
                    Log.w("MainActivity", "⚠️ No pending deletion URI found")
                }
            } else {
                Log.d("MainActivity", "❌ User cancelled deletion (resultCode: $resultCode)")
                Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show()
                // Clear pending deletion on cancel
                SongAdapter.pendingDeletionUri = null
            }
        } else {
            Log.d("MainActivity", "❌ Request code does NOT match (got $requestCode, expected $DELETE_REQUEST_CODE)")
        }
    }
    
    /**
     * Directly update the current visible fragment after a song is deleted
     */
    private fun updateFragmentsAfterDeletion(deletedUri: String) {
        Log.d("MainActivity", "🔄 Updating current fragment after deletion")
        
        // ViewPager2 manages fragments internally, so we use a tag-based approach
        val currentFragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        
        Log.d("MainActivity", "Current item: ${viewPager.currentItem}")
        Log.d("MainActivity", "Fragment tag: f${viewPager.currentItem}")
        Log.d("MainActivity", "Found fragment: ${currentFragment?.javaClass?.simpleName}")
        
        when (currentFragment) {
            is SongsFragment -> {
                Log.d("MainActivity", "📝 Updating SongsFragment")
                currentFragment.removeSongFromAdapter(deletedUri)
            }
            else -> {
                Log.d("MainActivity", "ℹ️ Not a SongsFragment, no update needed")
            }
        }
    }

    private fun initApp() {
        val serviceIntent = Intent(this, PlayerService::class.java)
        startService(serviceIntent)

        songList = scanSongs(this)
        
        val adapter = ViewPagerAdapter(this, songList)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "All Songs"
                1 -> "Playlists"
                2 -> "Folders"
                else -> "Tab $position"
            }
        }.attach()
        
        fabAddPlaylist.hide()
    }

    fun playSong(song: Song, isolateMode: Boolean = false) {
        currentSong = song
        currentSongUri = song.uri
        
        Log.d("MainActivity", "Playing: ${song.title}")
        
        val playIntent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY
            putExtra(PlayerService.EXTRA_SONG_URI, song.uri.toString())
            putExtra(PlayerService.EXTRA_SONG_TITLE, song.title)
            putExtra(PlayerService.EXTRA_SONG_ARTIST, song.artist)
            putExtra(PlayerService.EXTRA_ISOLATE_MODE, isolateMode)
        }
        startService(playIntent)
        
        updateNowPlaying(song.title, song.artist)
        isPlaying = true
        updatePlayPauseButton()
        updateKafkaAnimation(true)
        updateThreadVisualization(true)
    }
    
    private fun updateNowPlaying(title: String?, artist: String?) {
        nowPlaying.text = title ?: "Select a song..."
        nowPlayingArtist.text = artist ?: "Unknown Artist"
    }

    private fun updatePlayPauseButton() {
        playPauseBtn.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateShuffleButton() {
        shuffleBtn.alpha = if (isShuffled) 1.0f else 0.5f
    }
    
    private fun updateKafkaAnimation(playing: Boolean) {
        if (playing) {
            animateKafkaToPlaying()
        } else {
            animateKafkaToIdle()
        }
    }
    
    private fun animateKafkaToPlaying() {
        kafkaIdle.animate()
            .alpha(0f)
            .setDuration(KAFKA_ANIM_DURATION)
            .withEndAction {
                kafkaIdle.visibility = View.GONE
                stopIdlePulse()
            }
            .start()
        
        kafkaPlaying.visibility = View.VISIBLE
        kafkaPlaying.animate()
            .alpha(1f)
            .setDuration(KAFKA_ANIM_DURATION)
            .withEndAction {
                startPlayingPulse()
            }
            .start()
    }
    
    private fun animateKafkaToIdle() {
        kafkaPlaying.animate()
            .alpha(0f)
            .setDuration(KAFKA_ANIM_DURATION)
            .withEndAction {
                kafkaPlaying.visibility = View.GONE
                stopPlayingPulse()
            }
            .start()
        
        kafkaIdle.visibility = View.VISIBLE
        kafkaIdle.animate()
            .alpha(1f)
            .setDuration(KAFKA_ANIM_DURATION)
            .withEndAction {
                startIdlePulse()
            }
            .start()
    }
    
    // ========== KAFKA PULSATING ANIMATIONS ==========
    
    private fun startIdlePulse() {
        stopIdlePulse()
        
        idlePulseAnimator = ValueAnimator.ofFloat(PULSE_SCALE_MIN, PULSE_SCALE_MAX).apply {
            duration = PULSE_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                kafkaIdle.scaleX = scale
                kafkaIdle.scaleY = scale
            }
            
            start()
        }
    }
    
    private fun stopIdlePulse() {
        idlePulseAnimator?.cancel()
        idlePulseAnimator = null
        kafkaIdle.scaleX = 1f
        kafkaIdle.scaleY = 1f
    }
    
    private fun startPlayingPulse() {
        stopPlayingPulse()
        
        playingPulseAnimator = ValueAnimator.ofFloat(PULSE_SCALE_MIN, PULSE_SCALE_MAX).apply {
            duration = PULSE_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                kafkaPlaying.scaleX = scale
                kafkaPlaying.scaleY = scale
            }
            
            start()
        }
    }
    
    private fun stopPlayingPulse() {
        playingPulseAnimator?.cancel()
        playingPulseAnimator = null
        kafkaPlaying.scaleX = 1f
        kafkaPlaying.scaleY = 1f
    }
    
    // ========== THREAD VISUALIZATION ==========
    
    /**
     * Main thread visualization switcher
     * When nothing playing: slow idle glow
     * When playing: beat-synced reactive glow (data from PlayerService)
     */
    private fun updateThreadVisualization(playing: Boolean) {
        if (playing) {
            stopIdleGlow()
            // Visualizer runs in PlayerService and sends data here
        } else {
            // Reset to neutral state before starting idle glow
            spiderWebBg.scaleX = 1f
            spiderWebBg.scaleY = 1f
            startIdleGlow()
        }
    }
    
    /**
     * Update threads with even/odd alternating glow pattern
     * - EVEN THREADS (baseline): Constant glow following average intensity
     * - ODD THREADS (peaks): Only glow when intensity spikes above baseline
     * - BPM: Controls speed of glow transitions
     * - NO MOVEMENT: Only opacity/alpha changes
     */
    private var bpmPulseAnimator: ValueAnimator? = null
    private var lastPulseTime = 0L
    private var evenThreadAlpha = 0.4f  // Current even thread brightness
    private var oddThreadAlpha = 0.0f   // Current odd thread brightness
    
    private fun updateThreadsWithBeat(evenThreadIntensity: Double, oddThreadIntensity: Double, bpm: Double) {
        // ========== SMOOTH TRANSITIONS TO PREVENT EPILEPSY ==========
        // Use longer transition durations and gradual interpolation
        
        // ========== EVEN THREADS = STEADY/MODERATE GLOW ==========
        // Even threads glow when dB change is small (< 3 dB)
        val targetEvenAlpha = (evenThreadIntensity / 100.0 * 0.6 + 0.15).toFloat().coerceIn(0.15f, 0.7f)
        
        // ========== ODD THREADS = INTENSE GLOW ON SPIKES ==========
        // Odd threads glow when dB change is large (3+ dB)
        val targetOddAlpha = if (oddThreadIntensity > 0.0) {
            (oddThreadIntensity / 100.0 * 0.8 + 0.2).toFloat().coerceIn(0.2f, 1.0f)
        } else {
            0.0f // Off when no spike
        }
        
        // ========== SMOOTH BPM-BASED TRANSITIONS ==========
        val currentTime = System.currentTimeMillis()
        val beatInterval = (60000.0 / bpm).toLong().coerceIn(400L, 1500L) // Slightly longer minimum
        
        // Update with smooth, gradual transitions (longer duration to prevent flashing)
        if (currentTime - lastPulseTime >= beatInterval * 0.6) { // Slower update rate
            lastPulseTime = currentTime
            animateGlowTransition(targetEvenAlpha, targetOddAlpha, beatInterval)
        }
        
        // Debug logging
        if (Math.random() < 0.05) {
            Log.d("KafkaThreads", 
                "🎵 Even: ${String.format("%.1f", evenThreadIntensity)}% (Alpha: ${String.format("%.2f", targetEvenAlpha)}) " +
                "| Odd: ${String.format("%.1f", oddThreadIntensity)}% (Alpha: ${String.format("%.2f", targetOddAlpha)}) " +
                "| BPM: ${String.format("%.0f", bpm)}")
        }
    }
    
    /**
     * Smoothly transition glow using BPM-based timing
     * Creates the "breathing" effect synchronized to music tempo
     * With extended durations to prevent epilepsy triggers
     */
    private fun animateGlowTransition(targetEvenAlpha: Float, targetOddAlpha: Float, beatInterval: Long) {
        bpmPulseAnimator?.cancel()
        
        // Longer transition duration for safety (minimum 250ms to prevent rapid flashing)
        val transitionDuration = (beatInterval * 0.5).toLong().coerceIn(250L, 600L)
        
        val startEvenAlpha = evenThreadAlpha
        val startOddAlpha = oddThreadAlpha
        
        bpmPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = transitionDuration
            interpolator = AccelerateDecelerateInterpolator() // Smooth ease in/out
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                
                // Interpolate even thread alpha
                evenThreadAlpha = startEvenAlpha + (targetEvenAlpha - startEvenAlpha) * progress
                
                // Interpolate odd thread alpha
                oddThreadAlpha = startOddAlpha + (targetOddAlpha - startOddAlpha) * progress
                
                // Combine: Even threads are the base, odd threads overlay when active
                val combinedAlpha = (evenThreadAlpha + oddThreadAlpha * 0.5f).coerceIn(0.15f, 1.0f)
                
                // Apply to spider web (NO SCALE CHANGES - only alpha for smooth glow!)
                spiderWebBg.alpha = combinedAlpha
            }
            
            start()
        }
    }
    
    /**
     * Slow, ambient breathing effect when nothing is playing
     * Alpha: 0.2 → 0.4 over 3 seconds
     */
    private fun startIdleGlow() {
        stopIdleGlow()
        
        // Reset scale to normal (no movement)
        spiderWebBg.scaleX = 1.0f
        spiderWebBg.scaleY = 1.0f
        
        idleGlowAnimator = ValueAnimator.ofFloat(0.2f, 0.4f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                spiderWebBg.alpha = animation.animatedValue as Float
            }
            
            start()
        }
        
        Log.d("KafkaThreads", "🌙 Started idle glow")
    }
    
    private fun stopIdleGlow() {
        idleGlowAnimator?.cancel()
        idleGlowAnimator = null
        bpmPulseAnimator?.cancel()
        bpmPulseAnimator = null
        
        // Reset to no movement
        spiderWebBg.scaleX = 1.0f
        spiderWebBg.scaleY = 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        seekBarHandler.removeCallbacks(seekBarRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
        
        stopIdlePulse()
        stopPlayingPulse()
        stopIdleGlow()
    }
}