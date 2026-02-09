package com.example.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val playlists: List<String>,
    private val onPlaylistClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val getSongCount: (String) -> Int
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.playlistName)
        val countText: TextView = view.findViewById(R.id.songCount)
        val deleteBtn: ImageButton = view.findViewById(R.id.deletePlaylistBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlistName = playlists[position]
        val songCount = getSongCount(playlistName)
        
        holder.nameText.text = playlistName
        holder.countText.text = "$songCount song${if (songCount != 1) "s" else ""}"
        
        holder.itemView.setOnClickListener { 
            onPlaylistClick(playlistName)
        }
        
        holder.deleteBtn.setOnClickListener {
            onDeleteClick(playlistName)
        }
    }

    override fun getItemCount(): Int = playlists.size
}