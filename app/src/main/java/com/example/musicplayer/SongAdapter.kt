package com.example.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentlyPlayingSongUri: String? = null

    inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.songTitle)
        val artistText: TextView = view.findViewById(R.id.songArtist)
        val menuBtn: ImageButton = view.findViewById(R.id.songMenuBtn)
        val playingIndicator: View = view.findViewById(R.id.playingIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.titleText.text = song.title
        holder.artistText.text = song.artist
        
        // Show playing indicator for current song
        val isCurrentSong = song.uri.toString() == currentlyPlayingSongUri
        holder.playingIndicator.visibility = if (isCurrentSong) View.VISIBLE else View.GONE
        
        // Highlight current song
        val bgColor = if (isCurrentSong) {
            0xFF2D1654.toInt() // Highlighted background
        } else {
            0xFF1F0F3D.toInt() // Normal background
        }
        holder.itemView.setBackgroundColor(bgColor)
        
        // Click to play song directly
        holder.itemView.setOnClickListener { 
            onItemClick(song)
        }
        
        // Menu button also plays (or you can add menu later)
        holder.menuBtn.setOnClickListener { 
            onItemClick(song)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun setCurrentlyPlaying(songUri: String?) {
        currentlyPlayingSongUri = songUri
        notifyDataSetChanged()
    }
}