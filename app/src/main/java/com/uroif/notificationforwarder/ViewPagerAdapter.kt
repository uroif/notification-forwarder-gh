package com.uroif.notificationforwarder
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 6
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GeneralFragment()
            1 -> AppsFragment()
            2 -> TelegramFragment()
            3 -> SupabaseFragment()
            4 -> SpeakContainerFragment()
            5 -> HistoryFragment()
            else -> throw IllegalStateException("Invalid position")
        }
    }
}
