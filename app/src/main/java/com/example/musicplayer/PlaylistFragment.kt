package com.example.musicplayer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PlaylistsFragment : Fragment() {

    private lateinit var playlistRecycler: RecyclerView
    private lateinit var emptyText: TextView
    private var playlistAdapter: PlaylistAdapter? = null

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
        loadPlaylists()
    }

    fun loadPlaylists() {
        val playlists = PlaylistStorage.getAllPlaylistNames(requireContext())
        
        if (playlists.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No playlists yet\nTap + to create one"
            playlistRecycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            playlistRecycler.visibility = View.VISIBLE
            
            playlistAdapter = PlaylistAdapter(
                playlists = playlists,
                onPlaylistClick = { playlistName ->
                    openPlaylist(playlistName)
                },
                onDeleteClick = { playlistName ->
                    confirmDeletePlaylist(playlistName)
                },
                getSongCount = { playlistName ->
                    PlaylistStorage.loadPlaylist(requireContext(), playlistName).size
                }
            )
            playlistRecycler.adapter = playlistAdapter
        }
    }

    fun showCreatePlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_playlist, null)
        
        val nameLayout = dialogView.findViewById<TextInputLayout>(R.id.playlistNameLayout)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlistNameInput)
        val errorText = dialogView.findViewById<TextView>(R.id.errorText)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val createButton = dialogView.findViewById<Button>(R.id.createButton)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.KafkaDialog)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Real-time validation
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s.toString().trim()
                
                when {
                    name.isEmpty() -> {
                        errorText.visibility = View.GONE
                        createButton.isEnabled = false
                        createButton.alpha = 0.5f
                    }
                    PlaylistStorage.playlistExists(requireContext(), name) -> {
                        errorText.text = "⚠️ Playlist already exists"
                        errorText.visibility = View.VISIBLE
                        nameLayout.error = " "
                        createButton.isEnabled = false
                        createButton.alpha = 0.5f
                    }
                    name.length > 30 -> {
                        errorText.text = "⚠️ Name too long (max 30 characters)"
                        errorText.visibility = View.VISIBLE
                        nameLayout.error = " "
                        createButton.isEnabled = false
                        createButton.alpha = 0.5f
                    }
                    else -> {
                        errorText.visibility = View.GONE
                        nameLayout.error = null
                        createButton.isEnabled = true
                        createButton.alpha = 1.0f
                    }
                }
            }
        })
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        createButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty() && !PlaylistStorage.playlistExists(requireContext(), name)) {
                createPlaylist(name)
                dialog.dismiss()
            }
        }
        
        // Initially disable create button
        createButton.isEnabled = false
        createButton.alpha = 0.5f
        
        dialog.show()
    }

    private fun createPlaylist(name: String) {
        // Create empty playlist
        PlaylistStorage.savePlaylist(requireContext(), name, emptyList())
        
        Toast.makeText(
            requireContext(),
            "✅ Created '$name'",
            Toast.LENGTH_SHORT
        ).show()
        
        // Reload the list
        loadPlaylists()
    }

    private fun openPlaylist(name: String) {
        val songs = PlaylistStorage.loadPlaylist(requireContext(), name)
        showPlaylistSongsDialog(name, songs)
    }
    
    private fun showPlaylistSongsDialog(playlistName: String, songs: List<Song>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_songs, null)
        
        val titleText = dialogView.findViewById<TextView>(R.id.playlistTitle)
        titleText.text = playlistName
        
        val emptyView = dialogView.findViewById<TextView>(R.id.emptyPlaylistText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.playlistSongsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        if (songs.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            
            val adapter = SongAdapter(songs.toMutableList()) { song ->
                (activity as? MainActivity)?.playSong(song, false)
            }
            recyclerView.adapter = adapter
        }
        
        AlertDialog.Builder(requireContext(), R.style.KafkaDialog)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .setPositiveButton("Play All") { dialog, which ->
                if (songs.isNotEmpty()) {
                    (activity as? MainActivity)?.playSong(songs[0], false)
                }
            }
            .setNeutralButton("Add Songs") { dialog, which ->
                showAddSongsDialog(playlistName)
            }
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }.show()
    }
    
    private fun showAddSongsDialog(playlistName: String) {
        val allSongs = scanSongs(requireContext())
        val currentPlaylist = PlaylistStorage.loadPlaylist(requireContext(), playlistName)
        val currentUris = currentPlaylist.map { it.uri.toString() }.toSet()
        
        // Filter out songs already in playlist
        val availableSongs = allSongs.filter { it.uri.toString() !in currentUris }
        
        if (availableSongs.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "All songs are already in this playlist",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val songTitles = availableSongs.map { it.title }.toTypedArray()
        val selectedSongs = mutableListOf<Song>()
        
        AlertDialog.Builder(requireContext(), R.style.KafkaDialog)
            .setTitle("Add Songs to $playlistName")
            .setMultiChoiceItems(songTitles, null) { _, which, isChecked ->
                if (isChecked) {
                    selectedSongs.add(availableSongs[which])
                } else {
                    selectedSongs.remove(availableSongs[which])
                }
            }
            .setPositiveButton("Add") { _, _ ->
                if (selectedSongs.isNotEmpty()) {
                    val updatedPlaylist = currentPlaylist + selectedSongs
                    PlaylistStorage.savePlaylist(requireContext(), playlistName, updatedPlaylist)
                    
                    Toast.makeText(
                        requireContext(),
                        "✅ Added ${selectedSongs.size} song(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    loadPlaylists()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
private fun confirmDeletePlaylist(playlistName: String) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)
    val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<TextView>(R.id.confirmTitle).text = "Delete Playlist"
    dialogView.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete '$playlistName'?"
    
    dialogView.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
    
    dialogView.findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
        PlaylistStorage.deletePlaylist(requireContext(), playlistName)
        Toast.makeText(requireContext(), "🗑️ Deleted '$playlistName'", Toast.LENGTH_SHORT).show()
        loadPlaylists()
        dialog.dismiss()
    }

    dialog.show()
}
    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }
}