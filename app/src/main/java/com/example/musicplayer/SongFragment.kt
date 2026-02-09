package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SongsFragment : Fragment() {

    private lateinit var songRecycler: RecyclerView
    private var songs: MutableList<Song> = mutableListOf()
    private var songAdapter: SongAdapter? = null
    
    private val deletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val deletedUri = intent?.getStringExtra(SongAdapter.EXTRA_DELETED_URI)
            if (deletedUri != null) {
                android.util.Log.d("SongsFragment", "📡 Received deletion broadcast: $deletedUri")
                songAdapter?.removeSong(deletedUri)
            }
        }
    }

    companion object {
        private const val ARG_SONGS = "songs"

        fun newInstance(songs: List<Song>): SongsFragment {
            val fragment = SongsFragment()
            val bundle = Bundle()
            bundle.putParcelableArrayList(ARG_SONGS, ArrayList(songs.map { 
                SongParcelable(it.title, it.artist, it.uri.toString()) 
            }))
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val parcelables = it.getParcelableArrayList<SongParcelable>(ARG_SONGS)
            songs = parcelables?.map { p -> 
                Song(p.title, p.artist, android.net.Uri.parse(p.uri)) 
            }?.toMutableList() ?: mutableListOf()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_songs, container, false)
        songRecycler = view.findViewById(R.id.songRecycler)
        
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        songRecycler.layoutManager = LinearLayoutManager(requireContext())
        
        // Create adapter - clicking plays song directly
        songAdapter = SongAdapter(songs) { song ->
            (activity as? MainActivity)?.playSong(song, false)
        }
        songRecycler.adapter = songAdapter
    }
    
    override fun onResume() {
        super.onResume()
        // Register for deletion broadcasts
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(deletionReceiver, IntentFilter(SongAdapter.ACTION_SONG_DELETED))
        android.util.Log.d("SongsFragment", "📡 Registered deletion receiver")
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister deletion broadcast receiver
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(deletionReceiver)
        android.util.Log.d("SongsFragment", "📡 Unregistered deletion receiver")
    }
    
    /**
     * Public method to remove a song from the adapter
     * Called directly by MainActivity after deletion
     */
    fun removeSongFromAdapter(deletedUri: String) {
        android.util.Log.d("SongsFragment", "🗑️ Removing song from adapter: $deletedUri")
        songAdapter?.removeSong(deletedUri)
    }
}