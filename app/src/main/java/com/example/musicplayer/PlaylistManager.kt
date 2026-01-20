package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class PlaylistSong(
    val title: String,
    val artist: String,
    val uri: String
)

object PlaylistManager {

    private const val PREFS = "playlists"
    private const val KEY = "data"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun savePlaylist(context: Context, name: String, songs: List<Song>) {
        val root = JSONObject(loadAll(context))
        val arr = JSONArray()

        songs.forEach { song ->
            val o = JSONObject()
            o.put("title", song.title)
            o.put("artist", song.artist)
            o.put("uri", song.uri.toString())
            arr.put(o)
        }

        root.put(name, arr)
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    fun loadPlaylist(context: Context, name: String): List<Song> {
        val root = JSONObject(loadAll(context))
        val arr = root.optJSONArray(name) ?: return emptyList()

        val list = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Song(
                    o.getString("title"),
                    o.getString("artist"),
                    android.net.Uri.parse(o.getString("uri"))
                )
            )
        }
        return list
    }

    private fun loadAll(context: Context): String =
        prefs(context).getString(KEY, "{}") ?: "{}"
}
