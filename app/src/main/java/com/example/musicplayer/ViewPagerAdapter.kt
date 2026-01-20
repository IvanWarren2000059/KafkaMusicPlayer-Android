package com.example.musicplayer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(
    activity: FragmentActivity,
    private val songs: List<Song>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SongsFragment.newInstance(songs)
            1 -> PlaylistsFragment.newInstance()
            2 -> FoldersFragment.newInstance()
            else -> SongsFragment.newInstance(songs)
        }
    }
}