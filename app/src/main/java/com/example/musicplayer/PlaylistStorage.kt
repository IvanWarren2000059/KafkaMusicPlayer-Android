package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import com.example.musicplayer.Song

object PlaylistStorage {

    private const val PREFS = "playlists"
    private const val KEY = "data"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun savePlaylist(context: Context, name: String, songs: List<Song>) {
        val root = JSONObject(loadAll(context))
        val arr = JSONArray()

        songs.forEach {
            val o = JSONObject()
            o.put("title", it.title)
            o.put("artist", it.artist)
            o.put("uri", it.uri.toString())  // Serialize Uri to String
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
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    uri = Uri.parse(o.getString("uri"))  // Deserialize
                )
            )
        }
        return list
    }

    private fun loadAll(context: Context): String =
        prefs(context).getString(KEY, "{}") ?: "{}"
}
