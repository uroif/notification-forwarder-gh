package com.uroif.notificationforwarder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
class GeneralFragment : Fragment() {
    private lateinit var permissionStatusTextView: TextView
    private lateinit var serviceStatusTextView: TextView
    private lateinit var permissionButton: Button
    private lateinit var enableServiceSwitch: SwitchMaterial
    private lateinit var versionTextView: TextView
    private lateinit var batteryOptimizationStatusTextView: TextView
    private lateinit var batteryOptimizationButton: Button
    private lateinit var biometricSwitch: SwitchMaterial
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_general, container, false)
        permissionStatusTextView = view.findViewById(R.id.permissionStatusTextView)
        serviceStatusTextView = view.findViewById(R.id.serviceStatusTextView)
        permissionButton = view.findViewById(R.id.permissionButton)
        enableServiceSwitch = view.findViewById(R.id.enableServiceSwitch)
        versionTextView = view.findViewById(R.id.versionTextView)
        batteryOptimizationStatusTextView = view.findViewById(R.id.batteryOptimizationStatusTextView)
        batteryOptimizationButton = view.findViewById(R.id.batteryOptimizationButton)
        biometricSwitch = view.findViewById(R.id.biometricSwitch)
        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        batteryOptimizationButton.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
        setupServiceSwitch()
        setupBiometricSwitch()
        setupVersionInfo()
        return view
    }
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateServiceStatus()
        updateBatteryOptimizationStatus()
    }
    private fun setupServiceSwitch() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        enableServiceSwitch.isChecked = sharedPref.getBoolean("SERVICE_ENABLED", false)
        enableServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            with(sharedPref.edit()) {
                putBoolean("SERVICE_ENABLED", isChecked)
                apply()
            }
            updateServiceStatus()
        }
    }
    private fun updateServiceStatus() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        val isEnabled = sharedPref.getBoolean("SERVICE_ENABLED", false)
        if (isEnabled) {
            serviceStatusTextView.text = getString(R.string.service_enabled)
            serviceStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
        } else {
            serviceStatusTextView.text = getString(R.string.service_disabled)
            serviceStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        }
        enableServiceSwitch.isChecked = isEnabled
    }
    private fun isNotificationServiceEnabled(): Boolean {
        val context = activity?.applicationContext ?: return false
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }
    private fun updatePermissionStatus() {
        if (isNotificationServiceEnabled()) {
            permissionStatusTextView.text = getString(R.string.permission_granted)
            permissionStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            permissionButton.visibility = View.GONE 
        } else {
            permissionStatusTextView.text = getString(R.string.permission_not_granted)
            permissionStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            permissionButton.visibility = View.VISIBLE 
        }
    }
    private fun setupVersionInfo() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.longVersionCode
            versionTextView.text = "Version $versionName (Build $versionCode)"
        } catch (e: Exception) {
            versionTextView.text = "Version 1.1.0"
        }
    }
    private fun isIgnoringBatteryOptimization(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else {
            true 
        }
    }
    private fun updateBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isIgnoringBatteryOptimization()) {
                batteryOptimizationStatusTextView.text = "Battery Optimization: Disabled ✓"
                batteryOptimizationStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                batteryOptimizationButton.visibility = View.GONE
                Log.d("GeneralFragment", "Battery optimization is disabled (whitelisted)")
            } else {
                batteryOptimizationStatusTextView.text = "Battery Optimization: Enabled ⚠"
                batteryOptimizationStatusTextView.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                batteryOptimizationButton.visibility = View.VISIBLE
                Log.w("GeneralFragment", "Battery optimization is enabled - may cause notification delays")
            }
        } else {
            batteryOptimizationStatusTextView.text = "Battery Optimization: N/A"
            batteryOptimizationStatusTextView.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            batteryOptimizationButton.visibility = View.GONE
        }
    }
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
                Toast.makeText(
                    requireContext(),
                    "Please allow to ignore battery optimizations for instant notifications",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("GeneralFragment", "Requesting battery optimization whitelist")
            } catch (e: Exception) {
                Log.e("GeneralFragment", "Failed to request battery optimization", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to open battery optimization settings",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private fun setupBiometricSwitch() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        val isBiometricEnabled = sharedPref.getBoolean("BIOMETRIC_ENABLED", false)
        biometricSwitch.isChecked = isBiometricEnabled
        val biometricManager = BiometricManager.from(requireContext())
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricSwitch.isEnabled = true
                biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
                    with(sharedPref.edit()) {
                        putBoolean("BIOMETRIC_ENABLED", isChecked)
                        apply()
                    }
                    Log.d("GeneralFragment", "Biometric authentication ${if (isChecked) "enabled" else "disabled"}")
                    Toast.makeText(
                        requireContext(),
                        if (isChecked) "Biometric authentication enabled" else "Biometric authentication disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                biometricSwitch.isEnabled = false
                biometricSwitch.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "No biometric hardware available on this device",
                    Toast.LENGTH_SHORT
                ).show()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                biometricSwitch.isEnabled = false
                biometricSwitch.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "Biometric hardware is currently unavailable",
                    Toast.LENGTH_SHORT
                ).show()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                biometricSwitch.isEnabled = false
                biometricSwitch.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "No biometric credentials enrolled. Please add fingerprint or face in device settings",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                biometricSwitch.isEnabled = false
                biometricSwitch.isChecked = false
            }
        }
    }
}
