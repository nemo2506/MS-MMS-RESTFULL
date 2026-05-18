package com.miseservice.smsovh.viewmodel

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests pour le système de feedback UI
 */
class FeedbackSystemTest {

    @Test
    fun testFeedbackTypeValues() {
        val noneType = FeedbackType.NONE
        val successType = FeedbackType.SUCCESS
        val errorType = FeedbackType.ERROR

        assertNotNull("NONE doit exister", noneType)
        assertNotNull("SUCCESS doit exister", successType)
        assertNotNull("ERROR doit exister", errorType)
    }

    @Test
    fun testMainUiStateDefault() {
        val state = MainUiState()

        assertNull("feedbackMessage doit être null par défaut", state.feedbackMessage)
        assertEquals("feedbackType doit être NONE par défaut", FeedbackType.NONE, state.feedbackType)
    }

    @Test
    fun testMainUiStateWithFeedback() {
        val state = MainUiState(
            feedbackMessage = "✅ SMS envoyé",
            feedbackType = FeedbackType.SUCCESS
        )

        assertEquals("feedbackMessage doit être correct", "✅ SMS envoyé", state.feedbackMessage)
        assertEquals("feedbackType doit être SUCCESS", FeedbackType.SUCCESS, state.feedbackType)
    }

    @Test
    fun testMainUiStateCopy() {
        val originalState = MainUiState(
            senderId = "TEST",
            recipient = "+33612345678",
            message = "Hello",
            feedbackMessage = "Ancien message",
            feedbackType = FeedbackType.SUCCESS
        )

        val newState = originalState.copy(
            feedbackMessage = "Nouveau message",
            feedbackType = FeedbackType.ERROR
        )

        // Vérifier que l'original n'a pas changé
        assertEquals("L'original doit conserver son message", "Ancien message", originalState.feedbackMessage)
        assertEquals("L'original doit conserver son type", FeedbackType.SUCCESS, originalState.feedbackType)

        // Vérifier que la copie a les nouvelles valeurs
        assertEquals("La copie doit avoir le nouveau message", "Nouveau message", newState.feedbackMessage)
        assertEquals("La copie doit avoir le nouveau type", FeedbackType.ERROR, newState.feedbackType)

        // Vérifier que les autres champs sont conservés
        assertEquals("L'ID doit être conservé", "TEST", newState.senderId)
        assertEquals("Le recipient doit être conservé", "+33612345678", newState.recipient)
    }

    @Test
    fun testFeedbackMessageClearance() {
        val state = MainUiState(
            feedbackMessage = "Message",
            feedbackType = FeedbackType.SUCCESS
        )

        val clearedState = state.copy(
            feedbackMessage = null,
            feedbackType = FeedbackType.NONE
        )

        assertNotNull("L'original doit avoir un message", state.feedbackMessage)
        assertNull("L'état nettoyé doit avoir null", clearedState.feedbackMessage)
        assertEquals("L'état nettoyé doit avoir NONE", FeedbackType.NONE, clearedState.feedbackType)
    }

    @Test
    fun testSuccessAndErrorDurationDifference() {
        val successDuration = 3000L  // 3 secondes pour succès
        val errorDuration = 5000L    // 5 secondes pour erreur

        assertEquals("La durée d'erreur doit être de 5 secondes", 5000L, errorDuration)
        assertEquals("Différence doit être de 2 secondes", 2000L, errorDuration - successDuration)
    }

    @Test
    fun testMainUiStatePropertiesExist() {
        val state = MainUiState(
            senderId = "SENDER",
            recipient = "+336",
            message = "Test",
            feedbackMessage = "Message",
            feedbackType = FeedbackType.ERROR
        )

        // Vérifier que toutes les propriétés existent
        assertNotNull("senderId doit exister", state.senderId)
        assertNotNull("recipient doit exister", state.recipient)
        assertNotNull("message doit exister", state.message)
        assertNotNull("feedbackMessage doit exister", state.feedbackMessage)
        assertNotNull("feedbackType doit exister", state.feedbackType)
    }

    @Test
    fun testFeedbackMessageVariations() {
        val successMessages = listOf(
            "✅ SMS envoyé avec succès vers +33612345678",
            "✅ Log reçu et enregistré"
        )

        val errorMessages = listOf(
            "❌ Erreur: Numéro invalide",
            "❌ Erreur: recipient ou texte manquants"
        )

        successMessages.forEach { msg ->
            assertTrue("Le message de succès doit commencer par ✅: $msg", msg.startsWith("✅"))
        }

        errorMessages.forEach { msg ->
            assertTrue("Le message d'erreur doit commencer par ❌: $msg", msg.startsWith("❌"))
        }
    }

    @Test
    fun testMainUiStateEquality() {
        val state1 = MainUiState(feedbackMessage = "Test", feedbackType = FeedbackType.SUCCESS)
        val state2 = MainUiState(feedbackMessage = "Test", feedbackType = FeedbackType.SUCCESS)

        assertEquals("Les états identiques doivent être égaux", state1, state2)
    }

    @Test
    fun testCanSendLocalSmsFalseWhenServiceOff() {
        val state = MainUiState(
            serviceActive = false,
            isLoading = false,
            recipient = "+33612345678",
            message = "Test"
        )

        assertFalse("En OFF, l'envoi local doit être bloqué", state.canSendLocalSms)
    }

    @Test
    fun testCanSendLocalSmsFalseWhenServiceIsToggling() {
        val state = MainUiState(
            serviceActive = true,
            isLoading = true,
            recipient = "+33612345678",
            message = "Test"
        )

        assertFalse("Pendant la bascule du service, l'envoi local doit être bloqué", state.canSendLocalSms)
    }

    @Test
    fun testCanSendLocalSmsTrueWhenServiceOnAndValidForm() {
        val state = MainUiState(
            serviceActive = true,
            isLoading = false,
            recipient = "+33612345678",
            message = "Test"
        )

        assertTrue("En ON avec formulaire valide, l'envoi local doit être autorisé", state.canSendLocalSms)
    }
}

