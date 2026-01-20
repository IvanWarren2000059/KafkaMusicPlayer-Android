package com.example.musicplayer

import androidx.media3.common.Player

open class PlayerListenerAdapter : Player.Listener {
    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {}
    override fun onIsPlayingChanged(isPlaying: Boolean) {}
    override fun onPlaybackStateChanged(playbackState: Int) {}
}
