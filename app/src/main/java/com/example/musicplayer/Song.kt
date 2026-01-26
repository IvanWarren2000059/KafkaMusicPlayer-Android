package com.example.musicplayer

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class Song(
    val title: String, 
    val artist: String, 
    val uri: Uri,
    val folder: String = ""
)

fun scanSongs(context: Context): List<Song> {
    val songList = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DATA  // Full path
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val title = cursor.getString(titleIndex)
            val artist = cursor.getString(artistIndex)
            val path = cursor.getString(dataIndex)
            val contentUri = Uri.withAppendedPath(uri, id.toString())
            
            // ✅ FIX: Better folder extraction that works with nested folders
            val folder = extractMusicFolder(path)
            
            songList.add(Song(title, artist, contentUri, folder))
        }
    }

    return songList
}

/**
 * Extracts a meaningful folder name from the full path
 * Handles nested folders intelligently
 */
private fun extractMusicFolder(fullPath: String): String {
    val file = File(fullPath)
    val parentFile = file.parentFile ?: return "Unknown"
    
    // Get the path segments
    val pathParts = parentFile.absolutePath.split("/")
    
    // Common music root folders to skip
    val skipFolders = setOf(
        "storage", "emulated", "0", "Music", "Audio", 
        "Download", "Downloads", "media", "Android"
    )
    
    // Find the most specific folder that's not a system folder
    val meaningfulParts = pathParts
        .filter { it.isNotEmpty() && !skipFolders.contains(it) }
        .takeLast(2) // Take last 2 meaningful parts for nested folders
    
    return if (meaningfulParts.isNotEmpty()) {
        // Join with " > " to show hierarchy (e.g., "Rock > Classic Rock")
        meaningfulParts.joinToString(" > ")
    } else {
        parentFile.name.takeIf { it.isNotEmpty() } ?: "Unknown"
    }
}

// Group songs by folder
fun getSongsByFolder(context: Context): Map<String, List<Song>> {
    return scanSongs(context).groupBy { it.folder }
}

// Get unique folder names
fun getMusicFolders(context: Context): List<String> {
    return scanSongs(context).map { it.folder }.distinct().sorted()
}