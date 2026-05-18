package com.miseservice.msmms.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.miseservice.msmms.R
import com.miseservice.msmms.viewmodel.MainViewModel
import com.miseservice.msmms.viewmodel.SmsDeniedDialogMode
import com.miseservice.msmms.ui.theme.SmsOvhTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.miseservice.msmms.util.BatteryOptimizationHelper

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
        BatteryOptimizationHelper.buildIgnoreBatteryOptimizationIntent(this) ?: return
        viewModel.showBatteryOptimizationDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)

        requestIgnoreBatteryOptimization = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val granted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
            if (granted) {
                android.widget.Toast.makeText(this, getString(R.string.battery_optimization_disabled_success), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        requestSendSmsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (pendingSendSmsAfterPermission) {
                    viewModel.sendSms()
                }
                pendingSendSmsAfterPermission = false
            } else {
                val shouldSendAfterPermission = pendingSendSmsAfterPermission
                val permanentlyDenied = !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.SEND_SMS
                    )
                if (permanentlyDenied) {
                    viewModel.showSmsDeniedDialog(SmsDeniedDialogMode.PERMANENTLY_DENIED, shouldSendAfterPermission)
                } else {
                    viewModel.showSmsDeniedDialog(SmsDeniedDialogMode.RATIONALE, shouldSendAfterPermission)
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
                    onSendSmsRequested = { viewModel.sendSms() },
                    onSmsRationaleAllow = { forSendAction ->
                        viewModel.dismissSmsDeniedDialog()
                        launchSmsPermissionRequest(forSendAction)
                    },
                    onOpenAppSettings = { openAppSettings() },
                    onRequestBatteryOptimization = {
                        viewModel.dismissBatteryOptimizationDialog()
                        BatteryOptimizationHelper.buildIgnoreBatteryOptimizationIntent(this)
                            ?.let { requestIgnoreBatteryOptimization.launch(it) }
                    }
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
