package com.uroif.notificationforwarder
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class SelectAppsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppsAdapter
    private val selectedApps = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_apps)
        val currentSelected = intent.getStringArrayListExtra("SELECTED_APPS")
        if (currentSelected != null) {
            selectedApps.addAll(currentSelected)
        }
        recyclerView = findViewById(R.id.appsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val resultIntent = Intent()
                resultIntent.putStringArrayListExtra("SELECTED_APPS", ArrayList(selectedApps))
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        })
        loadInstalledApps()
    }
    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolvedInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            val appList = resolvedInfos.map {
                AppInfo(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.name }
            withContext(Dispatchers.Main) {
                adapter = AppsAdapter(appList, selectedApps) { packageName, isSelected ->
                    if (isSelected) {
                        selectedApps.add(packageName)
                    } else {
                        selectedApps.remove(packageName)
                    }
                }
                recyclerView.adapter = adapter
            }
        }
    }
}
