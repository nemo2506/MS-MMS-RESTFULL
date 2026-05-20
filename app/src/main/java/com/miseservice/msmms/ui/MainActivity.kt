package com.miseservice.msmms.ui

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
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
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>
    private lateinit var requestIgnoreBatteryOptimization: ActivityResultLauncher<Intent>
    private var pendingSendSmsAfterPermission: Boolean = false

    // Ouvre la page des paramètres de l'application pour accorder la permission manuellement
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = ("package:$packageName").toUri()
        }
        startActivity(intent)
    }

    private fun hasRequiredPermissions(): Boolean {
        val required = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val required = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestMultiplePermissions.launch(missing.toTypedArray())
        } else {
            // Toutes les permissions sont déjà là, on vérifie la batterie
            maybeRequestBatteryOptimizationExemption()
        }
    }

    private fun maybeRequestBatteryOptimizationExemption() {
        viewModel.refreshBatteryOptimizationState()
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
            viewModel.refreshBatteryOptimizationState()
            if (granted) {
                android.widget.Toast.makeText(this, getString(R.string.battery_optimization_disabled_success), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Mise à jour de l'état de la permission de localisation dans le ViewModel
            val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            viewModel.setLocationPermissionGranted(locationGranted)

            if (results.containsKey(Manifest.permission.SEND_SMS)) {
                if (results[Manifest.permission.SEND_SMS] == true) {
                    if (pendingSendSmsAfterPermission) {
                        viewModel.sendSms()
                    }
                } else {
                    val permanentlyDenied = !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.SEND_SMS
                    )
                    viewModel.showSmsDeniedDialog(
                        if (permanentlyDenied) SmsDeniedDialogMode.PERMANENTLY_DENIED else SmsDeniedDialogMode.RATIONALE,
                        pendingSendSmsAfterPermission
                    )
                }
            }
            pendingSendSmsAfterPermission = false
            viewModel.refreshNetworkInfo()
            
            // On enchaîne avec l'exemption de batterie uniquement après la fin du workflow des permissions système
            maybeRequestBatteryOptimizationExemption()
        }

        setContent {
            SmsOvhTheme {
                MainScreen(
                    viewModel = viewModel,
                    hasSendSmsPermission = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    },
                    onRequestSmsPermission = { forAction ->
                        pendingSendSmsAfterPermission = forAction
                        checkAndRequestPermissions()
                    },
                    onSendSmsRequested = { viewModel.sendSms() },
                    onSmsRationaleAllow = { forSendAction ->
                        viewModel.dismissSmsDeniedDialog()
                        pendingSendSmsAfterPermission = forSendAction
                        checkAndRequestPermissions()
                    },
                    onOpenAppSettings = { openAppSettings() },
                    onRequestBatteryOptimization = {
                        viewModel.dismissBatteryOptimizationDialog()
                        BatteryOptimizationHelper.buildIgnoreBatteryOptimizationIntent(this)
                            ?.let { requestIgnoreBatteryOptimization.launch(it) }
                        viewModel.refreshBatteryOptimizationState()
                    }
                )
            }
        }

        // Supprimé : checkAndRequestPermissions() est maintenant déclenché par MainScreen via le pre-prompt
        // checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationState()
        viewModel.refreshNetworkInfo()
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveAllSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
