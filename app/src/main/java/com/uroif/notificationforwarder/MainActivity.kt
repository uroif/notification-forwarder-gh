package com.uroif.notificationforwarder
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executor
class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 100
    }
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var isAuthenticated = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (isBiometricEnabled() && !isAuthenticated) {
            setupBiometricAuthentication()
            showBiometricPrompt()
        } else {
            initializeApp()
        }
    }
    private fun initializeApp() {
        requestNotificationPermission()
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)
        if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
            loadFragment(GeneralContainerFragment())
        }
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_general -> {
                    loadFragment(GeneralContainerFragment())
                    true
                }
                R.id.nav_apps -> {
                    loadFragment(AppsFragment())
                    true
                }
                R.id.nav_forward -> {
                    loadFragment(ForwardContainerFragment())
                    true
                }
                R.id.nav_voice -> {
                    loadFragment(SpeakContainerFragment())
                    true
                }
                else -> false
            }
        }
    }
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
                Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission")
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted")
            }
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted")
            } else {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied")
            }
        }
    }
    private fun isBiometricEnabled(): Boolean {
        val sharedPref = getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE)
        val enabled = sharedPref.getBoolean("BIOMETRIC_ENABLED", false)
        Log.d("MainActivity", "Biometric enabled: $enabled")
        return enabled
    }
    private fun setupBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("MainActivity", "Biometric authentication error: $errString (code: $errorCode)")
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || 
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(applicationContext,
                            "Authentication required to access the app",
                            Toast.LENGTH_SHORT).show()
                        finish()
                    } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                               errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        Toast.makeText(applicationContext,
                            "Too many attempts. Please try again later.",
                            Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(applicationContext,
                            "Authentication error: $errString",
                            Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("MainActivity", "Biometric authentication succeeded!")
                    isAuthenticated = true
                    Toast.makeText(applicationContext,
                        "Authentication successful!",
                        Toast.LENGTH_SHORT).show()
                    initializeApp()
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w("MainActivity", "Biometric authentication failed")
                    Toast.makeText(applicationContext,
                        "Authentication failed. Please try again.",
                        Toast.LENGTH_SHORT).show()
                }
            })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Authenticate to access Notification Forwarder")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                                     BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    }
    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("MainActivity", "Showing biometric prompt")
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w("MainActivity", "No biometric hardware available")
                Toast.makeText(this,
                    "No biometric hardware available. Biometric disabled.",
                    Toast.LENGTH_SHORT).show()
                disableBiometric()
                initializeApp()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.w("MainActivity", "Biometric hardware unavailable")
                Toast.makeText(this,
                    "Biometric hardware unavailable. Biometric disabled.",
                    Toast.LENGTH_SHORT).show()
                disableBiometric()
                initializeApp()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w("MainActivity", "No biometric enrolled")
                Toast.makeText(this,
                    "No biometric enrolled. Biometric disabled.",
                    Toast.LENGTH_LONG).show()
                disableBiometric()
                initializeApp()
            }
            else -> {
                Log.w("MainActivity", "Biometric not available")
                disableBiometric()
                initializeApp()
            }
        }
    }
    private fun disableBiometric() {
        val sharedPref = getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("BIOMETRIC_ENABLED", false)
            apply()
        }
        Log.d("MainActivity", "Biometric disabled due to unavailability")
    }
}
