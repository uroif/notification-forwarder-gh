package com.uroif.notificationforwarder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
class HistoryAdapter(private val logs: List<HistoryLog>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.historyTitleTextView)
        val body: TextView = view.findViewById(R.id.historyBodyTextView)
        val timestamp: TextView = view.findViewById(R.id.historyTimestampTextView)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_history, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.title.text = log.title
        holder.body.text = log.body
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        holder.timestamp.text = sdf.format(Date(log.timestamp))
    }
    override fun getItemCount() = logs.size
}
