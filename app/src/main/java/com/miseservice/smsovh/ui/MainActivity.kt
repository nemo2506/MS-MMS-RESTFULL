package com.miseservice.smsovh.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.miseservice.smsovh.R
import com.miseservice.smsovh.viewmodel.MainViewModel
import com.miseservice.smsovh.ui.theme.SmsOvhTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.miseservice.smsovh.util.BatteryOptimizationHelper

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var requestSendSmsPermission: ActivityResultLauncher<String>
    private lateinit var requestIgnoreBatteryOptimization: ActivityResultLauncher<Intent>
    private var pendingSendSmsAfterPermission: Boolean = false

    // Ouvre la page des paramètres de l'application pour accorder la permission manuellement
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = ("package:$packageName").toUri()
        }
        startActivity(intent)
    }

    private fun hasAllSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchSmsPermissionRequest(forSendAction: Boolean) {
        pendingSendSmsAfterPermission = forSendAction
        requestSendSmsPermission.launch(Manifest.permission.SEND_SMS)
    }

    private fun maybeRequestBatteryOptimizationExemption() {
        val intent = BatteryOptimizationHelper.buildIgnoreBatteryOptimizationIntent(this) ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_optimization_title))
            .setMessage(getString(R.string.battery_optimization_message))
            .setPositiveButton(getString(R.string.allow)) { _, _ ->
                requestIgnoreBatteryOptimization.launch(intent)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)

        requestIgnoreBatteryOptimization = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val granted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
            if (granted) {
                Toast.makeText(this, getString(R.string.battery_optimization_disabled_success), Toast.LENGTH_SHORT).show()
            }
        }

        requestSendSmsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (pendingSendSmsAfterPermission) {
                    viewModel.sendSms()
                }
                pendingSendSmsAfterPermission = false
            } else {
                if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.SEND_SMS
                    )
                ) {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.permission_required_title))
                        .setMessage(getString(R.string.permission_denied_permanently_message))
                        .setPositiveButton(getString(R.string.settings)) { _, _ -> openAppSettings() }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.permission_denied_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                pendingSendSmsAfterPermission = false
            }
        }
        setContent {
            SmsOvhTheme {
                MainScreen(
                    viewModel = viewModel,
                    hasSendSmsPermission = { hasAllSmsPermissions() },
                    onRequestSmsPermission = { launchSmsPermissionRequest(true) },
                    onSendSmsRequested = { viewModel.sendSms() }
                )
            }
        }

        maybeRequestBatteryOptimizationExemption()
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveAllSettings()
    }
}
