package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FoldersFragment : Fragment() {

    private lateinit var folderRecycler: RecyclerView
    private lateinit var emptyText: TextView

    companion object {
        fun newInstance(): FoldersFragment {
            return FoldersFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)
        folderRecycler = view.findViewById(R.id.playlistRecycler)
        emptyText = view.findViewById(R.id.emptyText)
        
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        folderRecycler.layoutManager = LinearLayoutManager(requireContext())
        
        val folders = getMusicFolders(requireContext())
        
        if (folders.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No music folders found"
            folderRecycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            folderRecycler.visibility = View.VISIBLE
            
            val adapter = FolderAdapter(folders) { folderName ->
                openFolder(folderName)
            }
            folderRecycler.adapter = adapter
        }
    }

    private fun openFolder(folderName: String) {
        val songsByFolder = getSongsByFolder(requireContext())
        val songs = songsByFolder[folderName] ?: emptyList()
        
        if (songs.isNotEmpty()) {
            showFolderSongs(folderName, songs)
        }
    }
    
    private fun showFolderSongs(folderName: String, songs: List<Song>) {
        // Create dialog layout programmatically
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF1F0F3D.toInt())
        }
        
        // Title
        val titleText = TextView(requireContext()).apply {
            text = folderName
            textSize = 20f
            setTextColor(0xFFE8D4F8.toInt())
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
        }
        dialogLayout.addView(titleText)
        
        // RecyclerView for songs
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800 // height in pixels
            )
        }
        
        // Create adapter - clicking plays song
        val adapter = SongAdapter(songs) { song ->
            (activity as? MainActivity)?.playSong(song, false)
        }
        recyclerView.adapter = adapter
        
        dialogLayout.addView(recyclerView)
        
        // Show dialog
        AlertDialog.Builder(requireContext())
            .setView(dialogLayout)
            .setNegativeButton("Close", null)
            .setPositiveButton("Play All") { dialog, which ->
                if (songs.isNotEmpty()) {
                    (activity as? MainActivity)?.playSong(songs[0], false)
                }
            }
            .show()
    }
}