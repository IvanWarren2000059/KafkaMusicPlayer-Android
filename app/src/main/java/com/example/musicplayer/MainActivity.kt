package com.example.musicplayer

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
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
    
    // Kafka animation views
    private lateinit var kafkaIdle: ImageView
    private lateinit var kafkaPlaying: ImageView
    
    private var songList: List<Song> = emptyList()
    private var isShuffled = false
    private var isPlaying = false
    private var currentSong: Song? = null
    
    // Animation state
    private var isAnimating = false
    private var currentAnimators = mutableListOf<ObjectAnimator>()
    
    // SeekBar update handler
    private val seekBarHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    companion object {
        private const val REQUEST_PERMISSION = 101
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
                }
                ACTION_UPDATE_PROGRESS -> {
                    val position = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
                    val duration = intent.getIntExtra(EXTRA_DURATION, 0)
                    
                    if (!isUserSeeking && duration > 0) {
                        seekBar.max = duration
                        seekBar.progress = position
                    }
                }
            }
        }
    }

    private val seekBarRunnable = object : Runnable {
        override fun run() {
            // Request progress update from service
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
        
        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE_UI)
            addAction(ACTION_UPDATE_PROGRESS)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)

        if (hasStoragePermission()) {
            initApp()
        } else {
            requestStoragePermission()
        }
        
        // Start SeekBar updates
        seekBarHandler.post(seekBarRunnable)
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
        
        // Initialize Kafka animation views
        kafkaIdle = findViewById(R.id.kafkaIdle)
        kafkaPlaying = findViewById(R.id.kafkaPlaying)
    }

    /**
     * ✅ IMPROVED: Only prompt once, with better UX
     */
    private fun requestBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyPrompted = prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isOptimized = !pm.isIgnoringBatteryOptimizations(packageName)
        
        // Only show if optimized AND haven't prompted before
        if (isOptimized && !alreadyPrompted) {
            showBatteryOptimizationDialog()
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keep Music Playing")
            .setMessage(
                "To ensure Kafka keeps playing music in the background, " +
                "please allow this app to run without battery optimization.\n\n" +
                "This will:\n" +
                "• Keep music playing when screen is off\n" +
                "• Prevent Android from stopping playback\n" +
                "• Allow seamless background playback"
            )
            .setPositiveButton("Allow") { _, _ ->
                openBatteryOptimizationSettings()
                markBatteryPromptShown()
            }
            .setNegativeButton("Later") { _, _ ->
                // Don't mark as shown, will ask again next time
            }
            .setNeutralButton("Don't Ask Again") { _, _ ->
                markBatteryPromptShown()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open battery settings", e)
            // Fallback: open general battery settings
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Log.e("MainActivity", "Failed to open general battery settings", e2)
            }
        }
    }
    
    private fun markBatteryPromptShown() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BATTERY_PROMPT_SHOWN, true)
            .apply()
    }

    private fun setupListeners() {
        playPauseBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PLAY_PAUSE
            }
            startService(intent)
        }

        nextBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_NEXT
            }
            startService(intent)
        }

        prevBtn.setOnClickListener {
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PREV
            }
            startService(intent)
        }

        shuffleBtn.setOnClickListener {
            isShuffled = !isShuffled
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_SHUFFLE
                putExtra(PlayerService.EXTRA_SHUFFLE_STATE, isShuffled)
            }
            startService(intent)
            updateShuffleButton()
        }

        fabAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
        
        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Nothing needed here
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
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initApp()
        } else {
            nowPlaying.text = "Storage permission required"
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
    }

    fun playSong(song: Song, isolateMode: Boolean = false) {
        currentSong = song
        
        Log.d("MainActivity", "")
        Log.d("MainActivity", "========================================")
        Log.d("MainActivity", "👆 USER CLICKED SONG")
        Log.d("MainActivity", "   Title: ${song.title}")
        Log.d("MainActivity", "   Artist: ${song.artist}")
        Log.d("MainActivity", "   URI: ${song.uri}")
        Log.d("MainActivity", "========================================")
        
        val playIntent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY
            putExtra(PlayerService.EXTRA_SONG_URI, song.uri.toString())
            putExtra(PlayerService.EXTRA_ISOLATE_MODE, isolateMode)
        }
        startService(playIntent)
        
        updateNowPlaying(song.title, song.artist)
        isPlaying = true
        updatePlayPauseButton()
        updateKafkaAnimation(true)
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
        if (isAnimating) {
            val animsToCancel = currentAnimators.toList()
            animsToCancel.forEach { it.cancel() }
            currentAnimators.clear()
        }
        
        if (playing && kafkaPlaying.visibility == View.VISIBLE && kafkaPlaying.alpha >= 0.9f) return
        if (!playing && kafkaIdle.visibility == View.VISIBLE && kafkaIdle.alpha >= 0.9f) return
        
        if (playing) {
            animateKafkaToPlaying()
        } else {
            animateKafkaToIdle()
        }
    }
    
    private fun animateKafkaToPlaying() {
        isAnimating = true
        
        val idleAnim = ObjectAnimator.ofFloat(kafkaIdle, "alpha", kafkaIdle.alpha, 0f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    kafkaIdle.visibility = View.GONE
                    currentAnimators.remove(this@apply)
                }
            })
        }
        
        kafkaPlaying.visibility = View.VISIBLE
        kafkaPlaying.alpha = 0f
        val playingAnim = ObjectAnimator.ofFloat(kafkaPlaying, "alpha", 0f, 1f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    currentAnimators.remove(this@apply)
                }
            })
        }
        
        currentAnimators.add(idleAnim)
        currentAnimators.add(playingAnim)
        idleAnim.start()
        playingAnim.start()
    }
    
    private fun animateKafkaToIdle() {
        isAnimating = true
        
        val playingAnim = ObjectAnimator.ofFloat(kafkaPlaying, "alpha", kafkaPlaying.alpha, 0f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    kafkaPlaying.visibility = View.GONE
                    currentAnimators.remove(this@apply)
                }
            })
        }
        
        kafkaIdle.visibility = View.VISIBLE
        kafkaIdle.alpha = 0f
        val idleAnim = ObjectAnimator.ofFloat(kafkaIdle, "alpha", 0f, 1f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    currentAnimators.remove(this@apply)
                }
            })
        }
        
        currentAnimators.add(playingAnim)
        currentAnimators.add(idleAnim)
        playingAnim.start()
        idleAnim.start()
    }

    private fun showCreatePlaylistDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Playlist name"
        
        AlertDialog.Builder(this)
            .setTitle("Create Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    PlaylistStorage.savePlaylist(this, name, emptyList())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        seekBarHandler.removeCallbacks(seekBarRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
        currentAnimators.forEach { it.cancel() }
    }
}