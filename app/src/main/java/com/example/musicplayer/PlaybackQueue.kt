package com.example.musicplayer

import com.example.musicplayer.Song

object PlaybackQueue {
    private var songs: List<Song> = emptyList()
    private var currentIndex: Int = 0

    fun set(songList: List<Song>, startIndex: Int = 0) {
        songs = songList
        currentIndex = startIndex
    }

    fun current(): Song? = songs.getOrNull(currentIndex)

    fun next(): Song? {
        if (songs.isEmpty()) return null
        currentIndex = (currentIndex + 1) % songs.size
        return current()
    }

    fun previous(): Song? {
        if (songs.isEmpty()) return null
        currentIndex = if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        return current()
    }

    fun allSongs(): List<Song> = songs
}
