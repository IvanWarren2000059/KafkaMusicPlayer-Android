package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object PlaylistStorage {

    private const val PREFS = "playlists"
    private const val KEY = "data"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Save a playlist with the given name and songs
     */
    fun savePlaylist(context: Context, name: String, songs: List<Song>) {
        val root = JSONObject(loadAll(context))
        val arr = JSONArray()

        songs.forEach {
            val o = JSONObject()
            o.put("title", it.title)
            o.put("artist", it.artist)
            o.put("uri", it.uri.toString())
            arr.put(o)
        }

        root.put(name, arr)
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    /**
     * Load a playlist by name
     */
    fun loadPlaylist(context: Context, name: String): List<Song> {
        val root = JSONObject(loadAll(context))
        val arr = root.optJSONArray(name) ?: return emptyList()

        val list = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Song(
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    uri = Uri.parse(o.getString("uri"))
                )
            )
        }
        return list
    }

    /**
     * Get all playlist names
     */
    fun getAllPlaylistNames(context: Context): List<String> {
        val root = JSONObject(loadAll(context))
        val names = mutableListOf<String>()
        
        root.keys().forEach { key ->
            names.add(key)
        }
        
        return names.sorted()
    }

    /**
     * Delete a playlist by name
     */
    fun deletePlaylist(context: Context, name: String) {
        val root = JSONObject(loadAll(context))
        root.remove(name)
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    /**
     * Check if a playlist exists
     */
    fun playlistExists(context: Context, name: String): Boolean {
        val root = JSONObject(loadAll(context))
        return root.has(name)
    }

    /**
     * Rename a playlist
     */
    fun renamePlaylist(context: Context, oldName: String, newName: String): Boolean {
        if (!playlistExists(context, oldName)) return false
        if (playlistExists(context, newName)) return false
        
        val songs = loadPlaylist(context, oldName)
        savePlaylist(context, newName, songs)
        deletePlaylist(context, oldName)
        
        return true
    }

    /**
     * Add a song to a playlist
     */
    fun addSongToPlaylist(context: Context, playlistName: String, song: Song) {
        val currentSongs = loadPlaylist(context, playlistName)
        
        // Don't add duplicates
        if (currentSongs.any { it.uri == song.uri }) {
            return
        }
        
        savePlaylist(context, playlistName, currentSongs + song)
    }

    /**
     * Remove a song from a playlist
     */
    fun removeSongFromPlaylist(context: Context, playlistName: String, song: Song) {
        val currentSongs = loadPlaylist(context, playlistName)
        val updatedSongs = currentSongs.filter { it.uri != song.uri }
        savePlaylist(context, playlistName, updatedSongs)
    }

    private fun loadAll(context: Context): String =
        prefs(context).getString(KEY, "{}") ?: "{}"
}