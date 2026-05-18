package com.miseservice.smsovh.util

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Exemple d'utilisation des utilitaires SMS/MMS
 * 
 * Ce fichier montre comment utiliser les différentes fonctions
 * créées pour l'envoi optimisé de SMS et MMS via OVH.
 */
@Suppress("unused")
object SmsUsageExamples {
    fun example1_simpleSms(context: Context) {
        SmsHelper.sendSmsWithSystem(context, "+33612345678", "Bonjour! Ceci est un SMS de test.", null) { _, _ -> }
    }

    fun example2_validateAndSend(context: Context) {
        PhoneNumberValidator.normalize("0612345678")?.let {
            SmsHelper.sendSmsWithSystem(context, it, "Message valide", "MyApp") { _, _ -> }
        }
    }

    fun example3_checkPermissions(context: Context) {
        if (!SmsPermissionsManager.canSendSms(context)) {
            return
        }

        SmsHelper.sendSmsWithSystem(context, "+33612345678", "OK à envoyer", null) { _, _ -> }
    }

    fun example4_sendMms(context: Context, base64Image: String) {
        if (!SmsPermissionsManager.canSendMms(context)) {
            return
        }

        SmsHelper.sendMmsWithStatus(
            context,
            "+33612345678",
            "Regardez cette image!",
            base64Image,
            "MonApp"
        ) { _, _ -> }
    }

    fun example5_smsCalculations() {
        val messages = listOf(
            "Court",
            "Ceci est un SMS un peu plus long avec des accents éàü",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
        )

        messages.forEach {
            OvhSmsConfig.willFitInSingleSms(it)
            OvhSmsConfig.calculateSmsPartCount(it)
        }
    }

    fun example6_validateBatch() {
        val phoneNumbers = listOf(
            "+33612345678",    // Valide
            "0712345678",      // Valide (format français)
            "abc123",          // Invalide
            "+331",            // Invalide (trop court)
            "+33123456789012"  // Invalide (trop long)
        )

        PhoneNumberValidator.validateBatch(phoneNumbers)
    }

    fun example7_phoneInfo() {
        val phoneNumber = "+33612345678"

        PhoneNumberValidator.getCountryCode(phoneNumber)
        PhoneNumberValidator.getCountryName(phoneNumber)
    }

    fun example8_configOvh() {
        OvhSmsConfig.logConfiguration()
    }

    fun example9_createSettings() {
        OvhSmsConfig.createSmsSettings()
        OvhSmsConfig.createMmsSettings()
        OvhSmsConfig.createLongMessageSettings()
    }

    fun example10_trackMessageStatus(context: Context) {
        val statusManager = MessageStatusManager(context)

        statusManager.registerStatusCallback("msg-123") { _ -> }
        statusManager.cleanup()
    }

    @Deprecated(
        message = "Use example11_restApiSms()",
        replaceWith = ReplaceWith("example11_restApiSms()")
    )
    fun example11_restApiSmS() = example11_restApiSms()

    fun example11_restApiSms() {
        val url = "http://${NetworkInfoProvider.getHostIp()}:${NetworkInfoProvider.getApiPort()}/api/send-sms"
        val json = org.json.JSONObject().apply {
            put("senderId", "MonApp")
            put("recipient", "+33612345678")
            put("text", "Message via API REST")
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer token123")
            .post(requestBody)
            .build()
        // Requête construite à titre d'exemple.
    }

    fun example12_restApiMms(base64Image: String) {
        val url = "http://${NetworkInfoProvider.getHostIp()}:${NetworkInfoProvider.getApiPort()}/api/send-mms"
        val json = org.json.JSONObject()
        json.put("senderId", "MonApp")
        json.put("recipient", "+33612345678")
        json.put("text", "Message avec image")
        json.put("base64Jpeg", base64Image)
        okhttp3.Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaType())).build()
    }

    fun example13_errorHandling(context: Context) {
        if (!PhoneNumberValidator.isValid("abc123")) return

        if (!SmsPermissionsManager.canSendSms(context)) return

        val message = "A".repeat(200)
        OvhSmsConfig.willFitInSingleSms(message)
        OvhSmsConfig.calculateSmsPartCount(message)

        val sizeBytes = 5 * 1024 * 1024
        OvhSmsConfig.isValidMmsImageSize(sizeBytes)
        OvhSmsConfig.getImageSizeErrorMessage(sizeBytes)
    }
}
