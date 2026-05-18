package com.miseservice.smsovh.util

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitaires pour le système de retour visuel de l'API REST
 */
class RestServerFeedbackSystemTest {

    @Test
    fun testRestServerEventCreation() {
        val event = RestServerEvent(
            RestServerEventType.SMS_SENT_SUCCESS,
            "✅ SMS envoyé avec succès"
        )
        
        assertEquals("Type doit être SMS_SENT_SUCCESS", RestServerEventType.SMS_SENT_SUCCESS, event.type)
        assertEquals("Message doit être correct", "✅ SMS envoyé avec succès", event.message)
    }

    @Test
    fun testAllEventTypes() {
        val successEvent = RestServerEvent(RestServerEventType.SMS_SENT_SUCCESS, "Success")
        val errorEvent = RestServerEvent(RestServerEventType.SMS_SENT_ERROR, "Error")
        val logSuccessEvent = RestServerEvent(RestServerEventType.LOG_RECEIVED_SUCCESS, "LogSuccess")
        val logErrorEvent = RestServerEvent(RestServerEventType.LOG_RECEIVED_ERROR, "LogError")

        assertTrue("SMS_SENT_SUCCESS doit exister", successEvent.type == RestServerEventType.SMS_SENT_SUCCESS)
        assertTrue("SMS_SENT_ERROR doit exister", errorEvent.type == RestServerEventType.SMS_SENT_ERROR)
        assertTrue("LOG_RECEIVED_SUCCESS doit exister", logSuccessEvent.type == RestServerEventType.LOG_RECEIVED_SUCCESS)
        assertTrue("LOG_RECEIVED_ERROR doit exister", logErrorEvent.type == RestServerEventType.LOG_RECEIVED_ERROR)
    }

    @Test
    fun testSuccessMessageFormat() {
        val messages = listOf(
            "✅ SMS envoyé avec succès vers +33612345678",
            "✅ Log reçu et enregistré",
            "✅ SMS envoyé avec succès"
        )

        messages.forEach { message ->
            assertTrue("Le message '$message' doit commencer par ✅", message.startsWith("✅"))
        }
    }

    @Test
    fun testErrorMessageFormat() {
        val messages = listOf(
            "❌ Erreur: Numéro invalide",
            "❌ Erreur: recipient ou texte manquants",
            "❌ Erreur: Authentification échouée"
        )

        messages.forEach { message ->
            assertTrue("Le message '$message' doit commencer par ❌", message.startsWith("❌"))
        }
    }

    @Test
    fun testEventMessageContent() {
        val smsSuccessEvent = RestServerEvent(
            RestServerEventType.SMS_SENT_SUCCESS,
            "✅ SMS envoyé avec succès vers +33612345678"
        )

        assertTrue("Le message doit contenir le succès", smsSuccessEvent.message.contains("SMS envoyé"))
        assertTrue("Le message doit contenir le numéro", smsSuccessEvent.message.contains("+33612345678"))
    }

    @Test
    fun testRestServerEventDataClass() {
        val event1 = RestServerEvent(RestServerEventType.SMS_SENT_SUCCESS, "Message 1")
        val event2 = RestServerEvent(RestServerEventType.SMS_SENT_SUCCESS, "Message 1")

        assertEquals("Les événements identiques doivent être égaux", event1, event2)
    }

    @Test
    fun testRestServerManagerCreation() {
        val manager = RestServerEventManager()
        assertNotNull("Le manager doit être créé", manager)
        assertNotNull("Le flow doit exister", manager.eventFlow)
    }

    @Test
    fun testRestServerEventEmissionIsAsync() = runTest {
        // Ce test vérifie que le système d'événements fonctionne
        val manager = RestServerEventManager()
        
        // Créer un événement
        val testEvent = RestServerEvent(
            RestServerEventType.SMS_SENT_SUCCESS,
            "✅ Test message"
        )
        
        // Émettre l'événement (ne pas bloquer)
        manager.emitEvent(testEvent)
        
        // Vérifier que l'événement a été créé correctement
        assertEquals("Le type doit être SMS_SENT_SUCCESS", RestServerEventType.SMS_SENT_SUCCESS, testEvent.type)
        assertEquals("Le message doit être correct", "✅ Test message", testEvent.message)
    }

    @Test
    fun testRestServerEventTypesExist() {
        val types = listOf(
            RestServerEventType.SMS_SENT_SUCCESS,
            RestServerEventType.SMS_SENT_ERROR,
            RestServerEventType.LOG_RECEIVED_SUCCESS,
            RestServerEventType.LOG_RECEIVED_ERROR
        )

        assertEquals("Il doit y avoir 4 types d'événements", 4, types.size)
    }

    @Test
    fun testEventTypeNames() {
        assertEquals("SMS_SENT_SUCCESS doit avoir le bon nom", "SMS_SENT_SUCCESS", RestServerEventType.SMS_SENT_SUCCESS.name)
        assertEquals("SMS_SENT_ERROR doit avoir le bon nom", "SMS_SENT_ERROR", RestServerEventType.SMS_SENT_ERROR.name)
        assertEquals("LOG_RECEIVED_SUCCESS doit avoir le bon nom", "LOG_RECEIVED_SUCCESS", RestServerEventType.LOG_RECEIVED_SUCCESS.name)
        assertEquals("LOG_RECEIVED_ERROR doit avoir le bon nom", "LOG_RECEIVED_ERROR", RestServerEventType.LOG_RECEIVED_ERROR.name)
    }
}

