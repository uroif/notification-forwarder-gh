package com.uroif.notificationforwarder
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
class SpeakHistoryFragment : Fragment() {
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: SpeakHistoryAdapter
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_speak_history, container, false)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        setupHistoryRecyclerView()
        loadHistory()
        return view
    }
    override fun onResume() {
        super.onResume()
        loadHistory()
    }
    private fun setupHistoryRecyclerView() {
        historyAdapter = SpeakHistoryAdapter(emptyList())
        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        historyRecyclerView.adapter = historyAdapter
    }
    private fun loadHistory() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        val gson = Gson()
        val json = sharedPref.getString("SPEAK_HISTORY_LOGS", "[]")
        val type = object : TypeToken<List<SpeakHistoryLog>>() {}.type
        val logs: List<SpeakHistoryLog> = gson.fromJson(json, type) ?: emptyList()
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        val recentLogs = logs.filter { it.timestamp >= sevenDaysAgo }
            .sortedByDescending { it.timestamp }
        historyAdapter.updateLogs(recentLogs)
    }
}
