package com.miseservice.smsovh.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.mms.ContentType
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.SendReq
import com.miseservice.smsovh.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object SmsHelper {
    private fun string(context: Context, resId: Int, vararg args: Any): String {
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }

    private fun resolveSmsManager(context: Context): SmsManager {
        val managerFromSystem = runCatching {
            context.getSystemService(SmsManager::class.java)
        }.getOrNull()
        if (managerFromSystem != null) return managerFromSystem

        @Suppress("DEPRECATION")
        val defaultManager = runCatching { SmsManager.getDefault() }.getOrNull()
        if (defaultManager != null) return defaultManager

        throw IllegalStateException(string(context, R.string.smshelper_smsmanager_unavailable))
    }

    private fun errorCodeFromException(context: Context, throwable: Throwable?): Int {
        val unavailable = string(context, R.string.smshelper_smsmanager_unavailable)
        return if (throwable?.message?.contains(unavailable, ignoreCase = true) == true) 503 else 500
    }

    private fun normalizeBase64Image(input: String): String {
        val trimmed = input.trim()
        val payload = if (trimmed.startsWith("data:") && trimmed.contains(",")) {
            trimmed.substringAfter(',')
        } else {
            trimmed
        }
        return payload.replace("\n", "").replace("\r", "").replace(" ", "")
    }

    // Construit un PDU MMS binaire (WAP) pour sendMultimediaMessage.
    private fun buildMmsPdu(
        context: Context,
        to: String,
        text: String,
        imageBytes: ByteArray,
        senderId: String?
    ): ByteArray {
        val pduBody = PduBody()

        if (text.isNotBlank()) {
            val textPart = PduPart().apply {
                setCharset(CharacterSets.UTF_8)
                setContentType(ContentType.TEXT_PLAIN.toByteArray())
                setContentId("<text>".toByteArray())
                setName("text.txt".toByteArray())
                setData(text.toByteArray(Charsets.UTF_8))
            }
            pduBody.addPart(textPart)
        }

        val imagePart = PduPart().apply {
            setContentType(ContentType.IMAGE_JPEG.toByteArray())
            setContentId("<image.jpg>".toByteArray())
            setName("image.jpg".toByteArray())
            setData(imageBytes)
        }
        pduBody.addPart(imagePart)

        val sendReq = SendReq().apply {
            addTo(EncodedStringValue(to))
            setSubject(
                EncodedStringValue(
                    senderId?.takeIf { it.isNotBlank() }
                        ?: string(context, R.string.smshelper_mms_subject_default)
                )
            )
            setBody(pduBody)
            setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
            setExpiry(604800L)
            setMessageSize(imageBytes.size.toLong())
            try {
                setPriority(PduHeaders.PRIORITY_NORMAL)
            } catch (_: InvalidHeaderValueException) {
                // Priorite non critique: on conserve le reste du PDU.
            }
        }

        return PduComposer(context, sendReq).make()
    }

    fun sendMmsWithStatus(
        context: Context,
        phoneNumber: String,
        message: String,
        base64Jpeg: String,
        senderId: String?,
        callback: (Boolean, JSONObject) -> Unit
    ) {
        var receiver: BroadcastReceiver? = null
        var networkCallback: ConnectivityManager.NetworkCallback? = null
        var pduFile: File? = null
        var connectivityManager: ConnectivityManager? = null
        var networkBound = false
        val completed = AtomicBoolean(false)
        val sendTriggered = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val action = "${context.packageName}.MMS_SENT_${System.currentTimeMillis()}"

        try {
            require(phoneNumber.isNotBlank()) { string(context, R.string.smshelper_number_empty) }
            require(base64Jpeg.isNotBlank()) { string(context, R.string.smshelper_image_empty) }

            val imageBytes = Base64.decode(normalizeBase64Image(base64Jpeg), Base64.DEFAULT)
            val pduBytes = buildMmsPdu(context.applicationContext, phoneNumber, message, imageBytes, senderId)

            pduFile = File(context.cacheDir, "mms_out_${System.currentTimeMillis()}.dat")
            FileOutputStream(pduFile).use { it.write(pduBytes) }

            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pduFile
            )

            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(action),
                PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )

            @Suppress("UNNECESSARY_SAFE_CALL")
            fun cleanup() {
                runCatching { receiver?.let { context.unregisterReceiver(it) } }
                runCatching { pduFile?.delete() }
                runCatching {
                    if (networkBound) {
                        connectivityManager?.bindProcessToNetwork(null)
                        networkBound = false
                    }
                }
                runCatching { networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) } }
            }

            fun complete(success: Boolean, json: JSONObject) {
                if (completed.compareAndSet(false, true)) {
                    handler.removeCallbacksAndMessages(null)
                    cleanup()
                    callback(success, json)
                }
            }

            handler.postDelayed({
                complete(
                    false,
                    JSONObject().apply {
                        put("success", false)
                        put("error", string(context, R.string.smshelper_mms_timeout, 30))
                        put("type", string(context, R.string.smshelper_mms_type))
                    }
                )
            }, 30_000L)

            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val json = JSONObject()
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            json.put("success", true)
                            json.put("message", string(context, R.string.smshelper_mms_sent, phoneNumber))
                            json.put("type", string(context, R.string.smshelper_mms_type))
                            json.put("imageSize", imageBytes.size)
                            complete(true, json)
                        }

                        else -> {
                            json.put("success", false)
                            json.put("error", mapMmsError(context, resultCode))
                            json.put("type", string(context, R.string.smshelper_mms_type))
                            complete(false, json)
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val smsManager = resolveSmsManager(context)

            val sendMmsAction = {
                try {
                    smsManager.sendMultimediaMessage(
                        context,
                        pduUri,
                        null,
                        null,
                        sentIntent
                    )
                    runCatching {
                        if (networkBound) {
                            connectivityManager?.bindProcessToNetwork(null)
                            networkBound = false
                        }
                    }
                } catch (e: Exception) {
                    complete(
                        false,
                        JSONObject().apply {
                            val message = e.message ?: string(context, R.string.smshelper_smsmanager_unavailable)
                            put("success", false)
                            put("error", string(context, R.string.smshelper_mms_error, message))
                            put("code", errorCodeFromException(context, e))
                            put("type", string(context, R.string.smshelper_mms_type))
                        }
                    )
                }
            }

            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val cm = connectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                    .build()

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (!sendTriggered.compareAndSet(false, true)) return
                        runCatching {
                            networkBound = cm.bindProcessToNetwork(network)
                        }
                        sendMmsAction()
                    }

                    override fun onUnavailable() {
                        complete(
                            false,
                            JSONObject().apply {
                                put("success", false)
                                put("error", string(context, R.string.smshelper_mms_error_no_data_network))
                                put("type", string(context, R.string.smshelper_mms_type))
                            }
                        )
                    }
                }

                cm.requestNetwork(request, networkCallback as ConnectivityManager.NetworkCallback, 30_000)
            } else {
                sendMmsAction()
            }
        } catch (e: IllegalArgumentException) {
            runCatching { receiver?.let { context.unregisterReceiver(it) } }
            runCatching { pduFile?.delete() }
            callback(
                false,
                JSONObject().apply {
                    put("success", false)
                    put("error", string(context, R.string.smshelper_invalid_param, e.message ?: ""))
                    put("code", 400)
                    put("type", string(context, R.string.smshelper_mms_type))
                }
            )
        } catch (e: Exception) {
            runCatching { receiver?.let { context.unregisterReceiver(it) } }
            runCatching { pduFile?.delete() }
            callback(
                false,
                JSONObject().apply {
                    put("success", false)
                    put("error", string(context, R.string.smshelper_mms_error, e.message ?: ""))
                    put("code", errorCodeFromException(context, e))
                    put("type", string(context, R.string.smshelper_mms_type))
                }
            )
        }
    }

    private fun mapMmsError(context: Context, code: Int): String {
        return when (code) {
            SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> string(context, R.string.smshelper_mms_error_configuration)
            SmsManager.MMS_ERROR_HTTP_FAILURE -> string(context, R.string.smshelper_mms_error_http)
            SmsManager.MMS_ERROR_INVALID_APN -> string(context, R.string.smshelper_mms_error_invalid_apn)
            SmsManager.MMS_ERROR_IO_ERROR -> string(context, R.string.smshelper_mms_error_io)
            SmsManager.MMS_ERROR_NO_DATA_NETWORK -> string(context, R.string.smshelper_mms_error_no_data_network)
            SmsManager.MMS_ERROR_RETRY -> string(context, R.string.smshelper_mms_error_retry)
            SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> string(context, R.string.smshelper_mms_error_unable_connect)
            SmsManager.MMS_ERROR_UNSPECIFIED -> string(context, R.string.smshelper_mms_error_unspecified)
            else -> string(context, R.string.smshelper_mms_error_code, code)
        }
    }

    fun sendSmsWithSystem(
        context: Context,
        phoneNumber: String,
        message: String,
        senderId: String?,
        callback: (Boolean, JSONObject) -> Unit
    ) {
        try {
            require(phoneNumber.isNotBlank()) { string(context, R.string.smshelper_number_empty) }
            require(message.isNotBlank()) { string(context, R.string.smshelper_message_empty) }

            val smsManager = resolveSmsManager(context)

            // Sender ID non supporte en SMS natif cote Android; on conserve le parametre pour compat API.
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)

            callback(
                true,
                JSONObject().apply {
                    put("success", true)
                    put("message", string(context, R.string.smshelper_sms_sent, phoneNumber))
                    put("type", string(context, R.string.smshelper_sms_type))
                    put("senderId", senderId ?: "")
                }
            )
        } catch (e: Exception) {
            callback(
                false,
                JSONObject().apply {
                    put("success", false)
                    put("error", string(context, R.string.smshelper_sms_error, e.message ?: ""))
                    put("code", errorCodeFromException(context, e))
                    put("type", string(context, R.string.smshelper_sms_type))
                }
            )
        }
    }

    @Suppress("unused")
    fun sendMmsWithAttachments(
        context: Context,
        phoneNumber: String,
        message: String,
        imageBytesList: List<ByteArray>,
        senderId: String?,
        callback: (Boolean, JSONObject) -> Unit
    ) {
        val first = imageBytesList.firstOrNull()
            ?: return callback(
                false,
                JSONObject().apply {
                    put("success", false)
                    put("error", context.getString(R.string.smshelper_mms_attachment_missing))
                    put("type", context.getString(R.string.smshelper_mms_type))
                }
            )

        sendMmsWithStatus(
            context = context,
            phoneNumber = phoneNumber,
            message = message,
            base64Jpeg = Base64.encodeToString(first, Base64.NO_WRAP),
            senderId = senderId,
            callback = callback
        )
    }

    @Suppress("unused")
    fun sendLongMessage(
        context: Context,
        phoneNumber: String,
        message: String,
        senderId: String?,
        callback: (Boolean, JSONObject) -> Unit
    ) {
        sendSmsWithSystem(context, phoneNumber, message, senderId, callback)
    }
}
