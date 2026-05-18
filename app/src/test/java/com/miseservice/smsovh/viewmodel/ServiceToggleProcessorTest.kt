package com.miseservice.smsovh.viewmodel

import com.miseservice.smsovh.service.ServiceControlManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeServiceControlManager : ServiceControlManager {
    var startCalls = 0
    var stopCalls = 0

    override fun start() {
        startCalls++
    }

    override fun stop() {
        stopCalls++
    }
}

class ServiceToggleProcessorTest {

    @Test
    fun toggleOn_updatesStateAndStartsService() {
        val fakeManager = FakeServiceControlManager()
        val processor = ServiceToggleProcessor(fakeManager)
        val initialState = MainUiState(isLoading = false, serviceActive = false, hostIp = "192.168.1.20")

        val result = processor.toggle(
            active = true,
            currentState = initialState,
            currentHostIp = initialState.hostIp,
            feedbackMessage = "✅ Service démarré"
        )

        assertEquals(1, fakeManager.startCalls)
        assertEquals(0, fakeManager.stopCalls)
        assertTrue(result.serviceActive)
        assertFalse(result.isLoading)
        assertEquals("✅ Service démarré", result.feedbackMessage)
        assertEquals(FeedbackType.SUCCESS, result.feedbackType)
        assertTrue(result.isIpValid)
    }

    @Test
    fun toggleOff_updatesStateAndStopsService() {
        val fakeManager = FakeServiceControlManager()
        val processor = ServiceToggleProcessor(fakeManager)
        val initialState = MainUiState(isLoading = false, serviceActive = true, hostIp = "192.168.1.20")

        val result = processor.toggle(
            active = false,
            currentState = initialState,
            currentHostIp = initialState.hostIp,
            feedbackMessage = "⏹️ Service arrêté"
        )

        assertEquals(0, fakeManager.startCalls)
        assertEquals(1, fakeManager.stopCalls)
        assertFalse(result.serviceActive)
        assertFalse(result.isLoading)
        assertEquals("⏹️ Service arrêté", result.feedbackMessage)
        assertEquals(FeedbackType.SUCCESS, result.feedbackType)
        assertFalse(result.isIpValid)
    }

    @Test
    fun toggleWithLocalhostKeepsIpInvalid() {
        val fakeManager = FakeServiceControlManager()
        val processor = ServiceToggleProcessor(fakeManager)
        val initialState = MainUiState(isLoading = false, serviceActive = false, hostIp = "127.0.0.1")

        val result = processor.toggle(
            active = true,
            currentState = initialState,
            currentHostIp = initialState.hostIp,
            feedbackMessage = "✅ Service démarré"
        )

        assertTrue(result.serviceActive)
        assertFalse(result.isIpValid)
    }
}

