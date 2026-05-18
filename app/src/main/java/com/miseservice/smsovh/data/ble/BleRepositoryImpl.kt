package com.miseservice.smsovh.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.miseservice.smsovh.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BleRepository {

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
    }

    private val serviceUuid: UUID = UUID.fromString(BleRuntimeConfig.serviceUuid)
    private val characteristicUuid: UUID = UUID.fromString(BleRuntimeConfig.characteristicUuid)

    private val _connectionState = MutableStateFlow<BleDeviceState>(BleDeviceState.Idle)
    override val connectionState: Flow<BleDeviceState> = _connectionState.asStateFlow()

    private fun hasScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    // ── Android BLE scanner ───────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override suspend fun scanForDevice(timeoutMs: Long): BleDeviceState {
        _connectionState.value = BleDeviceState.Scanning

        if (!hasScanPermission()) {
            return BleDeviceState.Error("Permission BLUETOOTH_SCAN manquante").also {
                _connectionState.value = it
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: return BleDeviceState.Error("Bluetooth non disponible").also {
                _connectionState.value = it
            }

        val scanner = adapter.bluetoothLeScanner
            ?: return BleDeviceState.Error("Scanner BLE indisponible").also {
                _connectionState.value = it
            }

        val found = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<BleDeviceState> { continuation ->
                val leScanCallback = object : android.bluetooth.le.ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                        val device = result.device
                        if (device.name == BleRuntimeConfig.deviceName && continuation.isActive) {
                            runCatching { scanner.stopScan(this) }
                            continuation.resume(
                                BleDeviceState.Found(
                                    address = device.address,
                                    name = device.name ?: BleRuntimeConfig.deviceName
                                )
                            )
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resume(BleDeviceState.Error("Échec du scan BLE (code=$errorCode)"))
                        }
                    }
                }

                runCatching { scanner.startScan(leScanCallback) }
                    .onFailure {
                        if (continuation.isActive) {
                            continuation.resume(BleDeviceState.Error(it.message ?: "Impossible de démarrer le scan BLE"))
                        }
                    }

                continuation.invokeOnCancellation {
                    runCatching { scanner.stopScan(leScanCallback) }
                }
            }
        } ?: BleDeviceState.NotFound

        _connectionState.value = found
        return found
    }

    // ── Connexion + validation PIN ────────────────────────────────────────────

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    /**
     * Future unique qui relie la requete readCharacteristic() a son callback async.
     *
     * - API 33+: valeur retournee par onCharacteristicRead(..., value, status)
     * - API < 33: valeur lue depuis characteristic.value
     */
    private val pendingRead = AtomicReference<CompletableFuture<ByteArray?>>()

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, pin: String): BleDeviceState {
        if (pin != BleRuntimeConfig.pin) {
            return BleDeviceState.InvalidPin.also {
                _connectionState.value = it
            }
        }

        if (!hasConnectPermission()) {
            return BleDeviceState.Error("Permission BLUETOOTH_CONNECT manquante").also {
                _connectionState.value = it
            }
        }

        _connectionState.value = BleDeviceState.Connecting

        // NOTE : intégrez ici votre bibliothèque BLE préférée
        // (Nordic BleManager, RxAndroidBle, ou GATT natif).
        // L'exemple ci-dessous utilise le GATT natif (callback simplifié).

        if (address.isBlank()) {
            return BleDeviceState.NotFound.also {
                _connectionState.value = it
            }
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: return BleDeviceState.Error("Bluetooth non disponible").also {
                _connectionState.value = it
            }
        val device = runCatching { adapter.getRemoteDevice(address) }
            .getOrElse {
                return BleDeviceState.Error(it.message ?: "Adresse Bluetooth invalide").also { state ->
                    _connectionState.value = state
                }
            }

        val result = withTimeoutOrNull(DEFAULT_CONNECT_TIMEOUT_MS) {
            connectGatt(device)
        } ?: BleDeviceState.Timeout

        _connectionState.value = result
        return result
    }

    // ── Lecture état ──────────────────────────────────────────────────────────

    override suspend fun readState(): BleRelayState {
        val char = characteristic
            ?: return BleRelayState.Raw("Non connecté")

        // Lecture GATT (synchronisée — adaptez selon votre lib BLE)
        val raw = readGattCharacteristic(char)
        return parseState(raw)
    }

    // ── Envoi commande ────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override suspend fun sendCommand(command: BleCommand): BleRelayState {
        if (!hasConnectPermission()) return BleRelayState.Raw("Permission Bluetooth manquante")

        val char = characteristic
            ?: return BleRelayState.Raw("Non connecté")

        writeGattCharacteristic(char, command.raw.toByteArray(Charsets.UTF_8))

        // Délai identique au script Python (3s pour WiFi ON, 0.5s sinon)
        delay(if (command is BleCommand.WifiOn) 3_000L else 500L)

        return readState()
    }

    // ── Déconnexion ───────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        if (!hasConnectPermission()) {
            gatt = null
            characteristic = null
            _connectionState.value = BleDeviceState.Disconnected
            return
        }

        gatt?.disconnect()
        gatt?.close()
        gatt = null
        characteristic = null
        _connectionState.value = BleDeviceState.Disconnected
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privés  (à adapter selon la lib BLE choisie)
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseState(raw: String): BleRelayState = when {
        raw.startsWith("IP:") -> BleRelayState.WebServer(raw.removePrefix("IP:").trim())
        raw == "1"            -> BleRelayState.On
        raw == "0"            -> BleRelayState.Off
        else                  -> BleRelayState.Raw(raw)
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(device: BluetoothDevice): BleDeviceState {
        disconnect()

        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(BleDeviceState.Error("Échec connexion GATT (status=$status)"))
                        }
                        gatt.close()
                        return
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(BleDeviceState.Disconnected)
                        }
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(BleDeviceState.Error("Découverte des services BLE impossible (status=$status)"))
                        }
                        gatt.disconnect()
                        gatt.close()
                        return
                    }

                    val service = gatt.getService(serviceUuid)
                    if (service == null) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(BleDeviceState.ServiceNotFound)
                        }
                        gatt.disconnect()
                        gatt.close()
                        return
                    }

                    val char = service.getCharacteristic(characteristicUuid)

                    if (char == null) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(BleDeviceState.CharacteristicNotFound)
                        }
                        gatt.disconnect()
                        gatt.close()
                        return
                    }

                    this@BleRepositoryImpl.gatt = gatt
                    this@BleRepositoryImpl.characteristic = char
                    if (!resumed) {
                        resumed = true
                        continuation.resume(BleDeviceState.Connected)
                    }
                }

                // API 33+ — appele en priorite si disponible.
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    val result = if (status == BluetoothGatt.GATT_SUCCESS) value else null
                    pendingRead.getAndSet(null)?.complete(result)
                }

                // Fallback API < 33.
                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    val result = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
                    pendingRead.getAndSet(null)?.complete(result)
                }
            }

            val createdGatt = device.connectGatt(context, false, callback)
            if (createdGatt == null) {
                continuation.resume(BleDeviceState.Error("Impossible d'ouvrir la connexion GATT"))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                runCatching {
                    createdGatt.disconnect()
                    createdGatt.close()
                }
            }
        }
    }

    /**
     * Lit une caracteristique GATT en mode coroutine sans utiliser getValue() en API 33+.
     *
     * Le flux est:
     * 1) readCharacteristic(char)
     * 2) attente du callback onCharacteristicRead(...)
     * 3) conversion UTF-8 du payload recu (ou vide si timeout/erreur)
     */
    @SuppressLint("MissingPermission")
    private suspend fun readGattCharacteristic(char: BluetoothGattCharacteristic): String {
        if (!hasConnectPermission()) return ""

        val currentGatt = gatt ?: return ""

        return withTimeoutOrNull(1_000L) {
            suspendCancellableCoroutine<String> { continuation ->
                val future = CompletableFuture<ByteArray?>()
                pendingRead.set(future)
                continuation.invokeOnCancellation { pendingRead.compareAndSet(future, null) }

                @Suppress("DEPRECATION")
                val started = currentGatt.readCharacteristic(char)
                if (!started) {
                    pendingRead.set(null)
                    continuation.resume("")
                    return@suspendCancellableCoroutine
                }

                // Complète quand le callback arrive
                future.thenAccept { value ->
                    val result = value?.toString(Charsets.UTF_8).orEmpty()
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        }.orEmpty()
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeGattCharacteristic(char: BluetoothGattCharacteristic, data: ByteArray) {
        if (!hasConnectPermission()) return

        val currentGatt = gatt ?: return
        @Suppress("DEPRECATION")
        run {
            char.writeType = BleRuntimeConfig.writeType
            char.value = data
            currentGatt.writeCharacteristic(char)
        }
        delay(250)
    }
}