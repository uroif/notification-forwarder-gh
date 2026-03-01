package com.uroif.notificationforwarder
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class AppsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var optimizeAllButton: Button
    private var selectedApps = mutableSetOf<String>()
    private var currentAppsList = listOf<AppInfo>()
    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps.clear()
            if (data != null) {
                selectedApps.addAll(data)
            }
            saveSelectedApps()
            loadSelectedAppsInfo()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_apps, container, false)
        recyclerView = view.findViewById(R.id.selectedAppsRecyclerView)
        fab = view.findViewById(R.id.addAppsFab)
        optimizeAllButton = view.findViewById(R.id.optimizeAllAppsButton)
        recyclerView.layoutManager = LinearLayoutManager(context)
        fab.setOnClickListener {
            val intent = Intent(activity, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("SELECTED_APPS", ArrayList(selectedApps))
            selectAppsLauncher.launch(intent)
        }
        optimizeAllButton.setOnClickListener {
            showOptimizeAllDialog()
        }
        loadSelectedApps()
        loadSelectedAppsInfo()
        return view
    }
    override fun onResume() {
        super.onResume()
        loadSelectedAppsInfo()
    }
    private fun loadSelectedApps() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        val savedApps = sharedPref.getStringSet("SELECTED_APPS", emptySet())
        selectedApps.clear()
        savedApps?.let { selectedApps.addAll(it) }
    }
    private fun saveSelectedApps() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putStringSet("SELECTED_APPS", selectedApps)
            apply()
        }
    }
    private fun loadSelectedAppsInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireActivity().packageManager
            val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val appList = mutableListOf<AppInfo>()
            selectedApps.forEach { packageName ->
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        !powerManager.isIgnoringBatteryOptimizations(packageName)
                    } else {
                        false
                    }
                    appList.add(
                        AppInfo(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(pm),
                            isBatteryOptimized = isBatteryOptimized
                        )
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                }
            }
            withContext(Dispatchers.Main) {
                currentAppsList = appList.sortedBy { it.name }
                val adapter = SelectedAppsAdapter(currentAppsList) { packageName ->
                    selectedApps.remove(packageName)
                    saveSelectedApps()
                    loadSelectedAppsInfo() 
                }
                recyclerView.adapter = adapter
                updateOptimizeAllButtonVisibility()
            }
        }
    }
    private fun updateOptimizeAllButtonVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasOptimizedApps = currentAppsList.any { it.isBatteryOptimized }
            optimizeAllButton.visibility = if (hasOptimizedApps && currentAppsList.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else {
            optimizeAllButton.visibility = View.GONE
        }
    }
    private fun showOptimizeAllDialog() {
        val optimizedApps = currentAppsList.filter { it.isBatteryOptimized }
        if (optimizedApps.isEmpty()) {
            return
        }
        val message = buildString {
            appendLine("To ensure instant notifications, disable battery optimization for these apps:")
            appendLine()
            optimizedApps.forEachIndexed { index, app ->
                appendLine("${index + 1}. ${app.name}")
            }
            appendLine()
            appendLine("Steps:")
            appendLine("1. We'll open Battery Optimization settings")
            appendLine("2. Find each app in the list")
            appendLine("3. Select 'Don't optimize' or 'Unrestricted'")
            appendLine()
            appendLine("This ensures notifications are forwarded instantly even when device is idle.")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Optimize ${optimizedApps.size} Apps")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        startActivity(intent)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
