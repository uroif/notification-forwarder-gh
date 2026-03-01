package com.uroif.notificationforwarder
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
class SpeakPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SpeakSettingsFragment()
            1 -> SpeakHistoryFragment()
            else -> throw IllegalStateException("Invalid position")
        }
    }
}
