package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val view = inflater.inflate(R.layout.fragment_folders, container, false)
        folderRecycler = view.findViewById(R.id.folderRecycler)
        emptyText = view.findViewById(R.id.emptyText)
        
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        folderRecycler.layoutManager = LinearLayoutManager(requireContext())
        
        val folders = getMusicFolders(requireContext())
        
        if (folders.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No music folders found\nAdd music to your device"
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_folder_songs, null)
        
        val titleText = dialogView.findViewById<TextView>(R.id.folderTitle)
        titleText.text = folderName
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderSongsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val adapter = SongAdapter(songs) { song ->
            (activity as? MainActivity)?.playSong(song, false)
        }
        recyclerView.adapter = adapter
        
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .setPositiveButton("Play All") { dialog, which ->
                if (songs.isNotEmpty()) {
                    (activity as? MainActivity)?.playSong(songs[0], false)
                }
            }
            .show()
    }
}