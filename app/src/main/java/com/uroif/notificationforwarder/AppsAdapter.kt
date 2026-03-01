package com.uroif.notificationforwarder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class AppsAdapter(
    private val apps: List<AppInfo>,
    private val selectedApps: MutableSet<String>,
    private val onAppSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {
    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIconImageView)
        val appName: TextView = view.findViewById(R.id.appNameTextView)
        val appCheckBox: CheckBox = view.findViewById(R.id.appCheckBox)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_app, parent, false)
        return AppViewHolder(view)
    }
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.name
        holder.appIcon.setImageDrawable(app.icon)
        holder.appCheckBox.setOnCheckedChangeListener(null)
        holder.appCheckBox.isChecked = selectedApps.contains(app.packageName)
        holder.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
            onAppSelected(app.packageName, isChecked)
        }
    }
    override fun getItemCount() = apps.size
}
