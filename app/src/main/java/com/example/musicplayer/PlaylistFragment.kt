package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistsFragment : Fragment() {

    private lateinit var playlistRecycler: RecyclerView
    private lateinit var emptyText: TextView

    companion object {
        fun newInstance(): PlaylistsFragment {
            return PlaylistsFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)
        playlistRecycler = view.findViewById(R.id.playlistRecycler)
        emptyText = view.findViewById(R.id.emptyText)
        
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        playlistRecycler.layoutManager = LinearLayoutManager(requireContext())
        
        // Load playlists - this is simplified
        val playlists = listOf("Favorites", "Workout", "Chill")
        
        if (playlists.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            playlistRecycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            playlistRecycler.visibility = View.VISIBLE
            
            val adapter = PlaylistAdapter(playlists) { playlistName ->
                openPlaylist(playlistName)
            }
            playlistRecycler.adapter = adapter
        }
    }

    private fun openPlaylist(name: String) {
        // Load and display playlist songs
        val songs = PlaylistStorage.loadPlaylist(requireContext(), name)
        // Show in new fragment or dialog
    }
}