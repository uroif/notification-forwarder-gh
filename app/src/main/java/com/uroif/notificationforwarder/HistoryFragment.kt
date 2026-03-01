package com.uroif.notificationforwarder
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.ceil
class HistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var pageNumberTextView: TextView
    private var currentPage = 1
    private val itemsPerPage = 10
    private var totalPages = 1
    private var fullLogList = listOf<HistoryLog>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        recyclerView = view.findViewById(R.id.historyRecyclerView)
        prevButton = view.findViewById(R.id.prevButton)
        nextButton = view.findViewById(R.id.nextButton)
        pageNumberTextView = view.findViewById(R.id.pageNumberTextView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        prevButton.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                updateHistoryView()
            }
        }
        nextButton.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                updateHistoryView()
            }
        }
        return view
    }
    override fun onResume() {
        super.onResume()
        loadFullHistory()
        updateHistoryView()
    }
    private fun loadFullHistory() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        val gson = Gson()
        val json = sharedPref.getString("HISTORY_LOGS", "[]")
        val type = object : TypeToken<List<HistoryLog>>() {}.type
        fullLogList = gson.fromJson<List<HistoryLog>>(json, type).sortedByDescending { it.timestamp }
        totalPages = ceil(fullLogList.size.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
    }
    private fun updateHistoryView() {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(fullLogList.size)
        val pageList = fullLogList.subList(startIndex, endIndex)
        recyclerView.adapter = HistoryAdapter(pageList)
        pageNumberTextView.text = "$currentPage / $totalPages"
        prevButton.isEnabled = currentPage > 1
        nextButton.isEnabled = currentPage < totalPages
    }
}
