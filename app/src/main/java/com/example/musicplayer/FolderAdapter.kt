package com.example.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderAdapter(
    private val folders: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.playlistName)
        val countText: TextView = view.findViewById(R.id.songCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folderName = folders[position]
        val songCount = getSongsByFolder(holder.itemView.context)[folderName]?.size ?: 0
        
        holder.nameText.text = folderName
        holder.countText.text = "$songCount songs"
        holder.itemView.setOnClickListener { onClick(folderName) }
    }

    override fun getItemCount(): Int = folders.size
}