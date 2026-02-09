package com.example.musicplayer

import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import java.io.File

class SongAdapter(
    private val songs: MutableList<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    companion object {
        const val ACTION_SONG_DELETED = "com.example.musicplayer.SONG_DELETED"
        const val EXTRA_DELETED_URI = "DELETED_URI"
        
        // Store the URI of the song being deleted
        var pendingDeletionUri: String? = null
    }

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
val card = holder.itemView as? androidx.cardview.widget.CardView
card?.setCardBackgroundColor(bgColor)
card?.cardElevation = 0f    // Use 0f for float
card?.maxCardElevation = 0f // Fixed spelling here


    // --- FIX ENDS HERE ---        
        // Click to play song
        holder.itemView.setOnClickListener { 
            onItemClick(song)
        }
        
        // Menu button shows options
        holder.menuBtn.setOnClickListener { 
            showSongMenu(holder.itemView.context, song)
        }
        
        // Long press also shows menu
        holder.itemView.setOnLongClickListener {
            showSongMenu(holder.itemView.context, song)
            true
        }
    }

    override fun getItemCount(): Int = songs.size

    fun setCurrentlyPlaying(songUri: String?) {
        currentlyPlayingSongUri = songUri
        notifyDataSetChanged()
    }
    
    /**
     * Remove a song from the list with animation
     */
    fun removeSong(songUri: String) {
        val position = songs.indexOfFirst { it.uri.toString() == songUri }
        if (position != -1) {
            songs.removeAt(position)
            notifyItemRemoved(position)
            // Notify range changed to update positions
            if (position < songs.size) {
                notifyItemRangeChanged(position, songs.size - position)
            }
        }
    }
    
  // REPLACE the showSongMenu method in SongAdapter.kt with this:

private fun showSongMenu(context: Context, song: Song) {
    val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_song_menu, null)
    
    val dialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .create()
    
    // Set transparent background so our custom background shows properly
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    
    // Set title
    dialogView.findViewById<TextView>(R.id.dialogTitle).text = song.title
    
    // Set click listeners
    dialogView.findViewById<TextView>(R.id.menuAddToPlaylist).setOnClickListener {
        dialog.dismiss()
        showAddToPlaylistDialog(context, song)
    }
    
    dialogView.findViewById<TextView>(R.id.menuDelete).setOnClickListener {
        dialog.dismiss()
        confirmDeleteSong(context, song)
    }
    
    dialogView.findViewById<TextView>(R.id.menuInfo).setOnClickListener {
        dialog.dismiss()
        showSongInfo(context, song)
    }
    
    dialog.show()
}
    
    private fun showAddToPlaylistDialog(context: Context, song: Song) {
        val playlists = PlaylistStorage.getAllPlaylistNames(context)
         if (playlists.isEmpty()) {
        Toast.makeText(context, "No playlists yet!", Toast.LENGTH_SHORT).show()
        return
    }

    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_to_playlist, null)
    val dialog = AlertDialog.Builder(context).setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    val recycler = dialogView.findViewById<RecyclerView>(R.id.playlistSelectionRecycler)
    recycler.layoutManager = LinearLayoutManager(context)
    
    // Reuse your existing PlaylistAdapter or a simple local one
    recycler.adapter = PlaylistAdapter(playlists, 
        onPlaylistClick = { name ->
            PlaylistStorage.addSongToPlaylist(context, name, song)
            Toast.makeText(context, "✅ Added to $name", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }, 
        onDeleteClick = {}, // Not needed here
        getSongCount = { name -> PlaylistStorage.loadPlaylist(context, name).size }
    )

    dialog.show()
}
   private fun confirmDeleteSong(context: Context, song: Song) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_action, null)
    val dialog = AlertDialog.Builder(context).setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    val title = dialogView.findViewById<TextView>(R.id.confirmTitle)
    val message = dialogView.findViewById<TextView>(R.id.confirmMessage)
    val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
    val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirm)

    title.text = "Delete Song"
    
    // Fallback logic for different OS versions
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        message.text = "Delete '${song.title}'?\n\n⚠️ Android will show a permission dialog next to protect your files."
    } else {
        message.text = "Are you sure you want to delete '${song.title}'?\n\nThis will permanently remove the file from your device."
    }

    btnCancel.setOnClickListener { dialog.dismiss() }

    btnConfirm.setOnClickListener {
        deleteSong(context, song)
        dialog.dismiss()
    }

    dialog.show()
}
    private fun deleteSong(context: Context, song: Song) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Use MediaStore with permission request
                deleteSongModern(context, song)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (API 29): Use scoped storage with RecoverableSecurityException
                deleteSongAndroid10(context, song)
            } else {
                // Android 9 and below: Direct file deletion
                deleteSongLegacy(context, song)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Failed to delete: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Android 11+ deletion
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun deleteSongModern(context: Context, song: Song) {
        try {
            android.util.Log.d("SongAdapter", "=== DELETE MODERN (Android 11+) ===")
            android.util.Log.d("SongAdapter", "Song: ${song.title}")
            android.util.Log.d("SongAdapter", "URI: ${song.uri}")
            
            // Store the URI so we know which song to remove after user approves
            pendingDeletionUri = song.uri.toString()
            
            // On Android 11+, we need to request permission via MediaStore
            // Don't try direct deletion - it will always throw RecoverableSecurityException
            val uris = listOf(song.uri)
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            
            android.util.Log.d("SongAdapter", "PendingIntent created")
            android.util.Log.d("SongAdapter", "Stored pending deletion URI: $pendingDeletionUri")
            
            // This will show system dialog to user
            (context as? MainActivity)?.let { activity ->
                try {
                    android.util.Log.d("SongAdapter", "Starting intent sender with request code: ${MainActivity.DELETE_REQUEST_CODE}")
                    activity.startIntentSenderForResult(
                        pendingIntent.intentSender,
                        MainActivity.DELETE_REQUEST_CODE,
                        null, 0, 0, 0
                    )
                    android.util.Log.d("SongAdapter", "Intent sender started successfully")
                } catch (e: IntentSender.SendIntentException) {
                    android.util.Log.e("SongAdapter", "SendIntentException", e)
                    pendingDeletionUri = null // Clear on error
                    Toast.makeText(context, "Error requesting delete permission: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                android.util.Log.e("SongAdapter", "Context is not MainActivity!")
                pendingDeletionUri = null // Clear on error
                Toast.makeText(context, "Error: Cannot request permission from this context", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("SongAdapter", "Delete failed", e)
            pendingDeletionUri = null // Clear on error
            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Android 10 deletion
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteSongAndroid10(context: Context, song: Song) {
        try {
            android.util.Log.d("SongAdapter", "=== DELETE ANDROID 10 ===")
            android.util.Log.d("SongAdapter", "Song: ${song.title}")
            android.util.Log.d("SongAdapter", "URI: ${song.uri}")
            
            context.contentResolver.delete(song.uri, null, null)
            Toast.makeText(context, "🗑️ Deleted '${song.title}'", Toast.LENGTH_SHORT).show()
            
            // Broadcast deletion and remove from adapter
            broadcastSongDeleted(context, song.uri.toString())
            removeSong(song.uri.toString())
        } catch (securityException: SecurityException) {
            android.util.Log.e("SongAdapter", "SecurityException caught", securityException)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                
                if (recoverableSecurityException != null) {
                    android.util.Log.d("SongAdapter", "RecoverableSecurityException - requesting permission")
                    
                    // Store the URI for later removal
                    pendingDeletionUri = song.uri.toString()
                    
                    // Request user permission
                    (context as? MainActivity)?.let { activity ->
                        try {
                            android.util.Log.d("SongAdapter", "Starting intent sender with request code: ${MainActivity.DELETE_REQUEST_CODE}")
                            activity.startIntentSenderForResult(
                                recoverableSecurityException.userAction.actionIntent.intentSender,
                                MainActivity.DELETE_REQUEST_CODE,
                                null, 0, 0, 0
                            )
                            android.util.Log.d("SongAdapter", "Intent sender started successfully")
                        } catch (e: IntentSender.SendIntentException) {
                            android.util.Log.e("SongAdapter", "SendIntentException", e)
                            pendingDeletionUri = null
                            Toast.makeText(context, "Error requesting permission: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } ?: run {
                        android.util.Log.e("SongAdapter", "Context is not MainActivity!")
                        pendingDeletionUri = null
                        Toast.makeText(context, "Error: Cannot request permission from this context", Toast.LENGTH_LONG).show()
                    }
                } else {
                    android.util.Log.e("SongAdapter", "SecurityException is not recoverable")
                    throw securityException
                }
            } else {
                throw securityException
            }
        } catch (e: Exception) {
            android.util.Log.e("SongAdapter", "Delete failed", e)
            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Android 9 and below deletion
    private fun deleteSongLegacy(context: Context, song: Song) {
        android.util.Log.d("SongAdapter", "=== DELETE LEGACY (Android 9-) ===")
        android.util.Log.d("SongAdapter", "Song: ${song.title}")
        android.util.Log.d("SongAdapter", "URI: ${song.uri}")
        
        // Get file path from URI
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        context.contentResolver.query(
            song.uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val filePath = cursor.getString(dataIndex)
                
                android.util.Log.d("SongAdapter", "File path: $filePath")
                
                val file = File(filePath)
                android.util.Log.d("SongAdapter", "File exists: ${file.exists()}")
                
                if (file.exists() && file.delete()) {
                    android.util.Log.d("SongAdapter", "File deleted successfully")
                    
                    // Tell MediaStore to scan for removed file
                    val deletedRows = context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DATA}=?",
                        arrayOf(filePath)
                    )
                    
                    android.util.Log.d("SongAdapter", "MediaStore rows deleted: $deletedRows")
                    
                    Toast.makeText(context, "🗑️ Deleted '${song.title}'", Toast.LENGTH_SHORT).show()
                    
                    // Broadcast deletion and remove from adapter
                    broadcastSongDeleted(context, song.uri.toString())
                    removeSong(song.uri.toString())
                } else {
                    android.util.Log.e("SongAdapter", "Could not delete file")
                    Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.e("SongAdapter", "Cursor is empty")
                Toast.makeText(context, "Could not find file path", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            android.util.Log.e("SongAdapter", "Query returned null")
            Toast.makeText(context, "Could not query file", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Broadcast that a song was deleted
     */
    private fun broadcastSongDeleted(context: Context, uri: String) {
        val intent = Intent(ACTION_SONG_DELETED).apply {
            putExtra(EXTRA_DELETED_URI, uri)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        android.util.Log.d("SongAdapter", "📡 Broadcast sent: Song deleted - $uri")
    }
    
    private fun showSongInfo(context: Context, song: Song) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_song_info, null)
        
        dialogView.findViewById<TextView>(R.id.songTitleValue).text = song.title
        dialogView.findViewById<TextView>(R.id.songArtistValue).text = song.artist
        dialogView.findViewById<TextView>(R.id.songUriValue).text = song.uri.toString()
        
        val dialog = AlertDialog.Builder(context, R.style.KafkaDialog)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<TextView>(R.id.okButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}