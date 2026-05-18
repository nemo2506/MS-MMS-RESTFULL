package com.miseservice.smsovh.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.miseservice.smsovh.R
import com.miseservice.smsovh.domain.usecase.SendRestMessageUseCase
import com.miseservice.smsovh.model.SendMessageRequest
import com.miseservice.smsovh.model.SendResult
import com.miseservice.smsovh.util.ApiTokenManager
import com.miseservice.smsovh.util.RestServerEventManager
import com.miseservice.smsovh.util.RestServerEvent
import com.miseservice.smsovh.util.RestServerEventType
import com.miseservice.smsovh.util.PhoneNumberValidator
import com.miseservice.smsovh.util.OvhSmsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import java.net.BindException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.miseservice.smsovh.data.local.LogDao

@Singleton
class SmsRestServer @Inject constructor(
    @ApplicationContext val context: Context,
    private val sendRestMessageUseCase: SendRestMessageUseCase,
    private val logDao: LogDao,
    private val restServerEventManager: RestServerEventManager,
    @Named("restPort") port: Int = 8080
) : NanoHTTPD(port) {
    private companion object {
        const val API_VERSION = "1.0"
        // Limite stricte MMS France : 600 Ko (614400 octets)
        const val MMS_IMAGE_MAX_SIZE_BYTES = 614400
    }

    private fun string(resId: Int, vararg args: Any): String {
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }

    private fun extractRecipient(json: JSONObject): String {
        return json.optString("recipient", "").trim()
    }

    @Volatile
    private var serverStarted = false

    @Volatile
    private var currentPort: Int = port.coerceIn(1, 65535)

    @Volatile
    private var activeServer: NanoHTTPD? = null

    @Synchronized
    fun isRunning(): Boolean = serverStarted

    @Synchronized
    fun startServer(): Boolean {
        if (serverStarted) return true

        return try {
            val server = object : NanoHTTPD(currentPort) {
                override fun serve(session: IHTTPSession): Response = this@SmsRestServer.serve(session)
            }
            server.start()
            activeServer = server
            serverStarted = true
            emitEvent(RestServerEventType.SERVER_START_SUCCESS, string(R.string.rest_server_started_message, currentPort))
            true
        } catch (_: BindException) {
            activeServer = null
            serverStarted = false
            emitEvent(RestServerEventType.SERVER_PORT_IN_USE, string(R.string.rest_server_port_in_use_message, currentPort))
            false
        } catch (_: Exception) {
            activeServer = null
            serverStarted = false
            emitEvent(RestServerEventType.SERVER_START_ERROR, string(R.string.rest_server_start_error_message, ""))
            false
        }
    }

    @Synchronized
    fun stopServer() {
        if (!serverStarted) return
        runCatching { activeServer?.stop() }
        activeServer = null
        serverStarted = false
    }

    @Synchronized
    @Suppress("unused")
    fun updatePort(newPort: Int) {
        val sanitized = newPort.coerceIn(1, 65535)
        if (sanitized == currentPort) return

        val shouldRestart = serverStarted
        if (shouldRestart) {
            stopServer()
        }

        currentPort = sanitized

        if (shouldRestart) {
            startServer()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            val headers = session.headers
            val authHeader = headers["authorization"]
            val expectedToken = "Bearer ${ApiTokenManager.getToken(context)}"

            if (authHeader == null || authHeader != expectedToken) {
                saveApiLog(
                    string(
                        R.string.feedback_error_with_message,
                        string(R.string.rest_server_unauthorized_log_message, session.method.name, session.uri)
                    )
                )
                val json = JSONObject()
                json.put("success", false)
                json.put("error", string(R.string.unauthorized_request_message))
                json.put("timestamp", System.currentTimeMillis())
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", json.toString())
            }

            return when {
                session.method == Method.POST && session.uri == "/api/send-message" -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val body = map["postData"]
                    if (body.isNullOrBlank()) {
                        saveApiLog(
                            string(
                                R.string.feedback_error_with_message,
                                string(R.string.rest_server_missing_body_log_message, session.method.name, session.uri)
                            )
                        )
                        return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_request_body_message), 400)
                    }
                    handleSendMessage(body)
                }
                session.method == Method.POST && session.uri == "/api/send-sms" -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val body = map["postData"]
                    if (body.isNullOrBlank()) {
                        saveApiLog(
                            string(
                                R.string.feedback_error_with_message,
                                string(R.string.rest_server_missing_body_log_message, session.method.name, session.uri)
                            )
                        )
                        return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_request_body_message), 400)
                    }
                    handleSendSms(body)
                }
                session.method == Method.POST && session.uri == "/api/send-mms" -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val body = map["postData"]
                    if (body.isNullOrBlank()) {
                        saveApiLog(
                            string(
                                R.string.feedback_error_with_message,
                                string(R.string.rest_server_missing_body_log_message, session.method.name, session.uri)
                            )
                        )
                        return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_request_body_message), 400)
                    }
                    handleSendMms(body)
                }
                session.method == Method.GET && session.uri == "/api/logs" -> {
                    handleGetLogs()
                }
                session.method == Method.POST && session.uri == "/api/logs" -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val body = map["postData"]
                    if (body.isNullOrBlank()) {
                        saveApiLog(
                            string(
                                R.string.feedback_error_with_message,
                                string(R.string.rest_server_missing_body_log_message, session.method.name, session.uri)
                            )
                        )
                        return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_request_body_message), 400)
                    }
                    handleLogMessage(body)
                }
                session.method == Method.GET && session.uri == "/api/health" -> {
                    handleHealthCheck()
                }
                session.method == Method.GET && session.uri == "/api/battery" -> {
                    handleBatteryStatus()
                }
                else -> {
                    saveApiLog(
                        string(
                            R.string.feedback_error_with_message,
                            string(R.string.rest_server_endpoint_not_found_log_message, session.method.name, session.uri)
                        )
                    )
                    createErrorResponse(Response.Status.NOT_FOUND, string(R.string.endpoint_not_found_message), 404)
                }
            }
        } catch (e: Exception) {
            val errorMessage = e.message ?: ""
            saveApiLog(string(R.string.feedback_error_with_message, string(R.string.internal_server_error_message, errorMessage)))
            return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, errorMessage))
        }
    }

    private fun handleHealthCheck(): Response {
        val json = JSONObject()
        json.put("success", true)
        json.put("status", "online")
        json.put("version", API_VERSION)
        json.put("timestamp", System.currentTimeMillis())
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun handleBatteryStatus(): Response {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (batteryIntent == null) {
                return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.battery_data_unavailable_message))
            }

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            if (level < 0 || scale <= 0) {
                return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.invalid_battery_values_message))
            }

            val percent = (level * 100) / scale
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val json = JSONObject().apply {
                put("success", true)
                put("level", percent)
                put("isCharging", isCharging)
                put("timestamp", System.currentTimeMillis())
            }

            newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        } catch (e: Exception) {
            createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun handleSendMessage(body: String): Response {
        try {
            val json = JSONObject(body)
            val senderId = json.optString("senderId", "").ifBlank { null }
            val recipient = extractRecipient(json)
            val text = json.optString("text", "").trim()
            val base64Jpeg = if (json.has("base64Jpeg") && !json.isNull("base64Jpeg")) {
                json.getString("base64Jpeg")
            } else {
                null
            }

            // Vérification taille image si base64Jpeg présent
            if (!base64Jpeg.isNullOrBlank()) {
                try {
                    val base64Clean = base64Jpeg.substringAfter(",", base64Jpeg).replace("\n", "").replace(" ", "")
                    val imageBytes = android.util.Base64.decode(base64Clean, android.util.Base64.DEFAULT)
                    if (imageBytes.size > MMS_IMAGE_MAX_SIZE_BYTES) {
                        val errorMsg = string(R.string.feedback_error_with_message, "Image trop volumineuse pour MMS (" + (imageBytes.size / 1024) + " Ko, max 600 Ko)")
                        emitEvent(RestServerEventType.SMS_SENT_ERROR, errorMsg)
                        saveApiLog(errorMsg)
                        return createErrorResponse(Response.Status.BAD_REQUEST, "Image trop volumineuse pour MMS (max 600 Ko)", 400)
                    }
                } catch (_: Exception) {
                    // Ignore, la vérification sera faite plus loin si besoin
                }
            }

            // Valide les champs
            if (recipient.isBlank() || text.isBlank()) {
                emitEvent(
                    RestServerEventType.SMS_SENT_ERROR,
                    string(R.string.feedback_error_with_message, string(R.string.rest_server_missing_destinataire_or_text_log_message))
                )
                return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_destinataire_or_text_message), 400)
            }

            // Valide et formate le numéro
            val normalizedNumber = PhoneNumberValidator.normalize(recipient)
                ?: run {
                    emitEvent(
                        RestServerEventType.SMS_SENT_ERROR,
                        string(R.string.feedback_error_with_message, string(R.string.rest_server_invalid_phone_number_log_message, recipient))
                    )
                    return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.invalid_phone_number_message, recipient), 400)
                }

            val request = SendMessageRequest(senderId, normalizedNumber, text, base64Jpeg)
            val result = runBlocking { sendRestMessageUseCase(request) }

            return when (result) {
                is SendResult.Success -> {
                    val messageType = if (base64Jpeg.isNullOrBlank()) "SMS" else "MMS"
                    val eventMessage = if (messageType == string(R.string.smshelper_sms_type)) {
                        string(R.string.sms_sent_with_success_message, normalizedNumber)
                    } else {
                        string(R.string.mms_sent_with_success_message, normalizedNumber)
                    }
                    emitEvent(RestServerEventType.SMS_SENT_SUCCESS, eventMessage)
                    saveApiLog(eventMessage)
                    createSuccessResponse(eventMessage, mapOf(
                        "type" to messageType,
                        "phoneNumber" to normalizedNumber
                    ))
                }
                is SendResult.Error -> {
                    val eventMessage = string(R.string.feedback_error_with_message, result.message)
                    emitEvent(RestServerEventType.SMS_SENT_ERROR, eventMessage)
                    saveApiLog(eventMessage)
                    createErrorResponse(Response.Status.INTERNAL_ERROR, result.message, result.code)
                }
            }
        } catch (e: Exception) {
            val eventMessage = string(R.string.feedback_error_with_message, e.message ?: "")
            emitEvent(RestServerEventType.SMS_SENT_ERROR, eventMessage)
            saveApiLog(eventMessage)
            return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun handleSendSms(body: String): Response {
        try {
            val json = JSONObject(body)
            val senderId = json.optString("senderId", "").ifBlank { null }
            val recipient = extractRecipient(json)
            val text = json.optString("text", "").trim()

            if (recipient.isBlank() || text.isBlank()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_destinataire_or_text_message), 400)
            }

            val normalizedNumber = PhoneNumberValidator.normalize(recipient)
                ?: return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.rest_server_invalid_phone_number_simple_message), 400)

            // Vérifie la longueur du SMS
            val partCount = OvhSmsConfig.calculateSmsPartCount(text)
            val request = SendMessageRequest(senderId, normalizedNumber, text, null)
            val result = runBlocking { sendRestMessageUseCase(request) }

            return when (result) {
                is SendResult.Success -> {
                    val eventMessage = string(R.string.sms_sent_with_success_message, normalizedNumber)
                    emitEvent(RestServerEventType.SMS_SENT_SUCCESS, eventMessage)
                    saveApiLog(eventMessage)
                    createSuccessResponse(eventMessage, mapOf(
                        "type" to "SMS",
                        "parts" to partCount.toString(),
                        "characters" to text.length.toString()
                    ))
                }
                is SendResult.Error -> {
                    val eventMessage = string(R.string.feedback_error_with_message, result.message)
                    emitEvent(RestServerEventType.SMS_SENT_ERROR, eventMessage)
                    saveApiLog(eventMessage)
                    createErrorResponse(Response.Status.INTERNAL_ERROR, result.message, result.code)
                }
            }
        } catch (e: Exception) {
            val eventMessage = string(R.string.feedback_error_with_message, e.message ?: "")
            saveApiLog(eventMessage)
            return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun handleSendMms(body: String): Response {
        try {
            val json = JSONObject(body)
            val senderId = json.optString("senderId", "").ifBlank { null }
            val recipient = extractRecipient(json)
            val text = json.optString("text", "").trim()
            val base64Jpeg = json.optString("base64Jpeg", "").ifBlank { null }

            if (recipient.isBlank() || base64Jpeg.isNullOrBlank()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_destinataire_or_image_message), 400)
            }

            // Vérification taille image MMS
            try {
                val base64Clean = base64Jpeg.substringAfter(",", base64Jpeg).replace("\n", "").replace(" ", "")
                val imageBytes = android.util.Base64.decode(base64Clean, android.util.Base64.DEFAULT)
                if (imageBytes.size > MMS_IMAGE_MAX_SIZE_BYTES) {
                    val errorMsg = string(R.string.feedback_error_with_message, "Image trop volumineuse pour MMS (" + (imageBytes.size / 1024) + " Ko, max 600 Ko)")
                    emitEvent(RestServerEventType.SMS_SENT_ERROR, errorMsg)
                    saveApiLog(errorMsg)
                    return createErrorResponse(Response.Status.BAD_REQUEST, "Image trop volumineuse pour MMS (max 600 Ko)", 400)
                }
            } catch (_: Exception) {
                // Ignore, la vérification sera faite plus loin si besoin
            }

            val normalizedNumber = PhoneNumberValidator.normalize(recipient)
                ?: return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.invalid_phone_number_message), 400)

            val request = SendMessageRequest(senderId, normalizedNumber, text, base64Jpeg)
            val result = runBlocking { sendRestMessageUseCase(request) }

            return when (result) {
                is SendResult.Success -> {
                    val eventMessage = string(R.string.mms_sent_with_success_message, normalizedNumber)
                    emitEvent(RestServerEventType.SMS_SENT_SUCCESS, eventMessage)
                    saveApiLog(eventMessage)
                    createSuccessResponse(eventMessage, mapOf(
                        "type" to "MMS",
                        "imageSize" to base64Jpeg.length.toString()
                    ))
                }
                is SendResult.Error -> {
                    val eventMessage = string(R.string.feedback_error_with_message, result.message)
                    emitEvent(RestServerEventType.SMS_SENT_ERROR, eventMessage)
                    saveApiLog(eventMessage)
                    createErrorResponse(Response.Status.INTERNAL_ERROR, result.message, result.code)
                }
            }
        } catch (e: Exception) {
            val eventMessage = string(R.string.feedback_error_with_message, e.message ?: "")
            saveApiLog(eventMessage)
            return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun handleGetLogs(): Response {
        return try {
            val logs = runBlocking { logDao.getLast5Logs() }
            val logsArray = JSONArray()
            logs.forEach { log ->
                logsArray.put(
                    JSONObject().apply {
                        put("id", log.id)
                        put("message", log.message)
                        put("timestamp", log.timestamp)
                    }
                )
            }

            val json = JSONObject().apply {
                put("success", true)
                put("count", logs.size)
                put("logs", logsArray)
                put("timestamp", System.currentTimeMillis())
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        } catch (e: Exception) {
            createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun handleLogMessage(body: String): Response {
        try {
            val json = JSONObject(body)
            val message = json.optString("message", "").trim()
            if (message.isBlank()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, string(R.string.missing_log_message), 400)
            }

            runBlocking {
                logDao.insertLog(com.miseservice.smsovh.data.local.LogEntity(message = message))
                logDao.deleteOldLogs()
                emitEvent(RestServerEventType.LOG_RECEIVED_SUCCESS, string(R.string.log_saved_message))
            }

            return createSuccessResponse(string(R.string.rest_server_log_saved_response_message), mapOf("message" to message))
        } catch (e: Exception) {
            val eventMessage = string(R.string.feedback_error_with_message, e.message ?: "")
            emitEvent(RestServerEventType.LOG_RECEIVED_ERROR, eventMessage)
            return createErrorResponse(Response.Status.INTERNAL_ERROR, string(R.string.internal_server_error_message, e.message ?: ""))
        }
    }

    private fun createSuccessResponse(message: String, extras: Map<String, String> = emptyMap()): Response {
        val json = JSONObject()
        json.put("success", true)
        json.put("message", message)
        json.put("timestamp", System.currentTimeMillis())

        for ((key, value) in extras) {
            json.put(key, value)
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun createErrorResponse(status: Response.Status, error: String, code: Int = 500): Response {
        val json = JSONObject()
        json.put("success", false)
        json.put("error", error)
        json.put("code", code)
        json.put("timestamp", System.currentTimeMillis())
        return newFixedLengthResponse(status, "application/json", json.toString())
    }

    private fun emitEvent(type: RestServerEventType, message: String) {
        try {
            runBlocking {
                restServerEventManager.emitEvent(RestServerEvent(type, message))
            }
        } catch (_: Exception) {
        }
    }

    private fun saveApiLog(message: String) {
        runCatching {
            runBlocking {
                logDao.insertLog(com.miseservice.smsovh.data.local.LogEntity(message = message))
                logDao.deleteOldLogs()
            }
        }.onFailure { }
    }
}
