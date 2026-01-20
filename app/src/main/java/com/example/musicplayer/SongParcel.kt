package com.example.musicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongParcelable(
    val title: String,
    val artist: String,
    val uri: String
) : Parcelable