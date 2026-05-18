package com.miseservice.smsovh.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSmsCircuitTest {

    private lateinit var context: Context

    private companion object {
        const val TEST_PHONE = "+33612345678"
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testPermissionsCheck() {
        val canSendSms = SmsPermissionsManager.canSendSms(context)
        SmsPermissionsManager.canSendMms(context)

        if (!canSendSms) {
            SmsPermissionsManager.getMissingPermissions(context)
        }

        assert(canSendSms) { "Permissions SMS manquantes" }
    }

    @Test
    fun testPhoneNumberValidation() {
        val testNumbers = mapOf(
            "0612345678" to true,
            "+33612345678" to true,
            "+331" to false,
            "abc123" to false
        )

        for ((number, shouldBeValid) in testNumbers) {
            assert(PhoneNumberValidator.isValid(number) == shouldBeValid) { "Validation échouée pour $number" }
        }
    }

    @Test
    fun testPhoneNumberFormatting() {
        val testCases = mapOf(
            "0612345678" to "+33612345678",
            "+33712345678" to "+33712345678",
            "612345678" to "+33612345678"
        )

        for ((input, expected) in testCases) {
            assert(PhoneNumberValidator.normalize(input) == expected) { "Formatage échoué: $input" }
        }
    }

    @Test
    fun testCountryDetection() {
        assert(PhoneNumberValidator.getCountryCode(TEST_PHONE) == "33") { "Code pays incorrect" }
        assert(PhoneNumberValidator.getCountryName(TEST_PHONE) == "France") { "Pays incorrect" }
    }

    @Test
    fun testSmsPartCalculation() {
        val testCases = mapOf(
            "Court" to 1,
            "A".repeat(160) to 1,
            "A".repeat(161) to 2,
            "A".repeat(320) to 2,
            "A".repeat(321) to 3,
            "Bonjour éàü" to 1
        )

        for ((message, expectedParts) in testCases) {
            assert(OvhSmsConfig.calculateSmsPartCount(message) == expectedParts) {
                "Calcul de parties échoué pour message de ${message.length} chars"
            }
        }
    }

    @Test
    fun testMmsImageValidation() {
        val testCases = mapOf(
            1024 to true,
            1024 * 1024 to true,
            2 * 1024 * 1024 to true,
            3 * 1024 * 1024 to true,
            4 * 1024 * 1024 to false,
            10 * 1024 * 1024 to false
        )

        for ((sizeBytes, shouldBeValid) in testCases) {
            assert(OvhSmsConfig.isValidMmsImageSize(sizeBytes) == shouldBeValid) { "Validation taille échouée" }
        }
    }

    @Test
    fun testPhoneBatchValidation() {
        val phoneNumbers = listOf(
            "+33612345678",
            "0712345678",
            "abc123",
            "+331",
            "+33123456789012"
        )

        val (valid, invalid) = PhoneNumberValidator.validateBatch(phoneNumbers)

        assert(valid.size == 2) { "Devrait avoir 2 numéros valides, eu ${valid.size}" }
        assert(invalid.size == 3) { "Devrait avoir 3 numéros invalides, eu ${invalid.size}" }
    }

    @Test
    fun testOvhConfiguration() {
        assert(OvhSmsConfig.Mmsc.URL == "http://mms.ovh.net") { "MMSC URL incorrecte" }
        assert(OvhSmsConfig.Mmsc.PORT == 8080) { "Port MMSC incorrect" }
        assert(OvhSmsConfig.Limits.MMS_SIZE_LIMIT_MB == 3) { "Limite MMS incorrecte" }
    }

    @Test
    fun testOvhSettingsCreation() {
        val smsSettings = OvhSmsConfig.createSmsSettings()
        val mmsSettings = OvhSmsConfig.createMmsSettings()
        OvhSmsConfig.createLongMessageSettings()

        assert(mmsSettings.mmsc == OvhSmsConfig.Mmsc.URL) { "MMSC non configuré correctement" }
        assert(mmsSettings.mmsPort == OvhSmsConfig.Mmsc.PORT) { "Port MMSC incorrect" }
        assert(smsSettings.useSystemSending)
    }

    @Test
    fun testMessageStatusManager() {
        val statusManager = MessageStatusManager(context)

        statusManager.registerStatusCallback("test-msg-1") { _ -> }
        statusManager.unregisterStatusCallback("test-msg-1")
        statusManager.cleanup()
    }

    @Test
    fun testDisplayFullConfiguration() {
        OvhSmsConfig.logConfiguration()
    }
}
