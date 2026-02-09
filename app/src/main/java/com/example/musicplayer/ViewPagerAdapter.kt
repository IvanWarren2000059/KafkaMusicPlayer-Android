package com.example.musicplayer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private var songs: List<Song>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SongsFragment.newInstance(songs)
            1 -> PlaylistsFragment.newInstance()
            2 -> FoldersFragment.newInstance()
            else -> SongsFragment.newInstance(songs)
        }
    }
    
    /**
     * Update the song list and refresh all fragments
     */
    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}