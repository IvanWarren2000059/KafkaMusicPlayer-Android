package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SongsFragment : Fragment() {

    private lateinit var songRecycler: RecyclerView
    private var songs: List<Song> = emptyList()

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
            } ?: emptyList()
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
        val adapter = SongAdapter(songs) { song ->
            (activity as? MainActivity)?.playSong(song, false)
        }
        songRecycler.adapter = adapter
    }
}