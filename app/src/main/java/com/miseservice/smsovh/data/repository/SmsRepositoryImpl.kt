package com.miseservice.smsovh.data.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.miseservice.smsovh.domain.repository.SmsRepository
import com.miseservice.smsovh.model.SmsMessage
import com.miseservice.smsovh.model.SendResult
import com.miseservice.smsovh.model.SendMessageRequest
import com.miseservice.smsovh.util.SmsHelper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SmsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsRepository {
    private companion object {
        const val SMS_SENT = "SMS_SENT"
    }

    /**
     * Envoie un SMS en mode natif pour éviter toute dépendance READ_SMS côté provider téléphonie.
     */
    override suspend fun sendSms(sms: SmsMessage): SendResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (telephonyManager.simState != TelephonyManager.SIM_STATE_READY) {
            return SendResult.Error(503, "SIM absente ou non prête")
        }

        if (sms.to.isBlank() || sms.message.isBlank()) {
            return SendResult.Error(400, "Destinataire ou message manquant")
        }

        return suspendCancellableCoroutine { cont ->
            sendSmsDirect(sms, cont)
        }
    }

    private fun resolveSmsManagerOrNull(): SmsManager? {
        @Suppress("DEPRECATION")
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
        return manager
    }

    private fun mapSmsManagerUnavailable(): SendResult.Error {
        return SendResult.Error(503, "SmsManager indisponible")
    }

    private fun sendSmsDirect(sms: SmsMessage, cont: kotlinx.coroutines.CancellableContinuation<SendResult>) {
        try {
            val smsManager = resolveSmsManagerOrNull()
            if (smsManager == null) {
                if (cont.isActive) cont.resume(mapSmsManagerUnavailable())
                return
            }

            val sentIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                Intent(SMS_SENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val sentReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    try {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                if (cont.isActive) cont.resume(SendResult.Success)
                            }
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> if (cont.isActive) cont.resume(SendResult.Error(500, "Echec generique"))
                            SmsManager.RESULT_ERROR_NO_SERVICE -> if (cont.isActive) cont.resume(SendResult.Error(503, "Pas de reseau"))
                            SmsManager.RESULT_ERROR_NULL_PDU -> if (cont.isActive) cont.resume(SendResult.Error(500, "PDU nul"))
                            SmsManager.RESULT_ERROR_RADIO_OFF -> if (cont.isActive) cont.resume(SendResult.Error(503, "Radio desactivee"))
                            else -> if (cont.isActive) cont.resume(SendResult.Error(500, "Erreur inconnue"))
                        }
                    } finally {
                        runCatching { context.unregisterReceiver(this) }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                sentReceiver,
                IntentFilter(SMS_SENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            smsManager.sendTextMessage(sms.to, null, sms.message, sentIntent, null)

            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(sentReceiver) }
            }
        } catch (e: Exception) {
            val code = if (e.message?.contains("SmsManager indisponible", ignoreCase = true) == true) 503 else 500
            if (cont.isActive) cont.resume(SendResult.Error(code, "Erreur: ${e.message}"))
        }
    }

    /**
     * Envoie un MMS via SmsHelper (envoi direct natif).
     */
    override suspend fun sendMms(request: SendMessageRequest): SendResult {
        if (request.recipient.isBlank()) {
            return SendResult.Error(400, "Destinataire manquant")
        }

        if (request.base64Jpeg.isNullOrBlank()) {
            return SendResult.Error(400, "Image base64 manquante")
        }

        return suspendCancellableCoroutine { cont ->
            SmsHelper.sendMmsWithStatus(
                context,
                request.recipient,
                request.text,
                request.base64Jpeg,
                request.senderId
            ) { success, json ->
                if (success) {
                    if (cont.isActive) cont.resume(SendResult.Success)
                } else {
                    val errorMsg = json.optString("error", "Erreur MMS inconnue")
                    val code = json.optInt("code", 500)
                    if (cont.isActive) cont.resume(SendResult.Error(code, errorMsg))
                }
            }
        }
    }
}
