package com.uroif.notificationforwarder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
class SelectedAppsAdapter(
    private val apps: List<AppInfo>,
    private val onRemoveClicked: (String) -> Unit
) : RecyclerView.Adapter<SelectedAppsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIconImageView)
        val appName: TextView = view.findViewById(R.id.appNameTextView)
        val batteryStatus: TextView = view.findViewById(R.id.batteryOptimizationStatus)
        val optimizeButton: Button = view.findViewById(R.id.optimizeAppButton)
        val removeButton: ImageButton = view.findViewById(R.id.removeAppButton)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_selected_app, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        holder.appName.text = app.name
        holder.appIcon.setImageDrawable(app.icon)
        updateBatteryOptimizationStatus(holder, app, context)
        holder.optimizeButton.setOnClickListener {
            openBatteryOptimizationSettings(context, app.packageName)
        }
        holder.removeButton.setOnClickListener {
            onRemoveClicked(app.packageName)
        }
    }
    private fun updateBatteryOptimizationStatus(holder: ViewHolder, app: AppInfo, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isOptimized = !powerManager.isIgnoringBatteryOptimizations(app.packageName)
            app.isBatteryOptimized = isOptimized
            if (isOptimized) {
                holder.batteryStatus.visibility = View.VISIBLE
                holder.batteryStatus.text = "⚠ Battery optimized - may delay notifications"
                holder.batteryStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                holder.optimizeButton.visibility = View.VISIBLE
            } else {
                holder.batteryStatus.visibility = View.VISIBLE
                holder.batteryStatus.text = "✓ Battery optimization disabled"
                holder.batteryStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                holder.optimizeButton.visibility = View.GONE
            }
        } else {
            holder.batteryStatus.visibility = View.GONE
            holder.optimizeButton.visibility = View.GONE
        }
    }
    private fun openBatteryOptimizationSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Tap 'Battery' → Select 'Unrestricted'",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Find '$packageName' and select 'Don't optimize'",
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                Toast.makeText(
                    context,
                    "Unable to open battery settings",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    override fun getItemCount() = apps.size
}
