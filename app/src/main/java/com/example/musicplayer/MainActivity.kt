package com.example.musicplayer

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    companion object {
        private const val REQUEST_PERMISSION = 101
        const val ACTION_UPDATE_UI = "UPDATE_UI"
        const val EXTRA_SONG_TITLE = "SONG_TITLE"
        const val EXTRA_SONG_ARTIST = "SONG_ARTIST"
        const val EXTRA_IS_PLAYING = "IS_PLAYING"
        
        private const val KAFKA_ANIM_DURATION = 400L
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra(EXTRA_SONG_TITLE)
            val artist = intent?.getStringExtra(EXTRA_SONG_ARTIST)
            isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, false) ?: false
            
            updateNowPlaying(title, artist)
            updatePlayPauseButton()
            updateKafkaAnimation(isPlaying)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_UI))

        if (hasStoragePermission()) {
            initApp()
        } else {
            requestStoragePermission()
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
        
        // Initialize Kafka animation views
        kafkaIdle = findViewById(R.id.kafkaIdle)
        kafkaPlaying = findViewById(R.id.kafkaPlaying)
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
        val index = songList.indexOf(song)
        
        val playIntent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY
            putExtra(PlayerService.EXTRA_INDEX, index)
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
    
    // ========================================
    // KAFKA ANIMATION METHODS
    // ========================================
    
    /**
     * Animates Kafka between idle and playing states with smooth crossfade
     * @param playing true to show excited Kafka, false to show calm Kafka
     */
    private fun updateKafkaAnimation(playing: Boolean) {
        if (playing) {
            animateKafkaToPlaying()
        } else {
            animateKafkaToIdle()
        }
    }
    
    private fun animateKafkaToPlaying() {
        // Fade out idle Kafka
        ObjectAnimator.ofFloat(kafkaIdle, "alpha", 1f, 0f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }.also {
            it.addUpdateListener { animation ->
                if (animation.animatedFraction == 1f) {
                    kafkaIdle.visibility = View.GONE
                }
            }
        }
        
        // Fade in playing Kafka
        kafkaPlaying.visibility = View.VISIBLE
        kafkaPlaying.alpha = 0f
        ObjectAnimator.ofFloat(kafkaPlaying, "alpha", 0f, 1f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
    
    private fun animateKafkaToIdle() {
        // Fade out playing Kafka
        ObjectAnimator.ofFloat(kafkaPlaying, "alpha", 1f, 0f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }.also {
            it.addUpdateListener { animation ->
                if (animation.animatedFraction == 1f) {
                    kafkaPlaying.visibility = View.GONE
                }
            }
        }
        
        // Fade in idle Kafka
        kafkaIdle.visibility = View.VISIBLE
        kafkaIdle.alpha = 0f
        ObjectAnimator.ofFloat(kafkaIdle, "alpha", 0f, 1f).apply {
            duration = KAFKA_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
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
                    // Refresh playlist view
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}
