package com.uroif.notificationforwarder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class SpeakHistoryAdapter(private var logs: List<SpeakHistoryLog>) :
    RecyclerView.Adapter<SpeakHistoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timestampTextView: TextView = view.findViewById(R.id.timestampTextView)
        val amountTextView: TextView = view.findViewById(R.id.amountTextView)
        val appNameTextView: TextView = view.findViewById(R.id.appNameTextView)
        val fullTextTextView: TextView = view.findViewById(R.id.fullTextTextView)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_speak_history, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        holder.timestampTextView.text = sdf.format(Date(log.timestamp))
        holder.amountTextView.text = formatAmount(log.amount)
        holder.appNameTextView.text = log.appName
        holder.fullTextTextView.text = log.fullText
    }
    override fun getItemCount(): Int = logs.size
    fun updateLogs(newLogs: List<SpeakHistoryLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }
    private fun formatAmount(amount: String): String {
        return try {
            val num = amount.toLongOrNull() ?: return "${amount}đ"
            String.format(Locale.US, "%,dđ", num).replace(",", ".")
        } catch (e: Exception) {
            "${amount}đ"
        }
    }
}
