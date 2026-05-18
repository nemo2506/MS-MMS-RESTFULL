package com.miseservice.smsovh.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AndroidServiceControlManagerTest {

    @Test
    fun stop_callsStopServiceAndRestServer_evenWithoutPriorStart() {
        val context = mock<Context>()
        val smsRestServer = mock<SmsRestServer>()
        val manager = AndroidServiceControlManager(context, smsRestServer)

        whenever(context.stopService(any<Intent>())).thenReturn(true)

        manager.stop()

        verify(context).stopService(any())
        verify(smsRestServer).stopServer()
        verify(smsRestServer, never()).startServer()
    }

    @Test
    fun start_thenStop_callsRestServerStartThenStop() {
        val context = mock<Context>()
        val smsRestServer = mock<SmsRestServer>()
        val manager = AndroidServiceControlManager(context, smsRestServer)

        whenever(context.startService(any<Intent>())).thenReturn(
            ComponentName("com.miseservice.smsovh", "com.miseservice.smsovh.service.SmsOvhForegroundService")
        )
        whenever(context.stopService(any<Intent>())).thenReturn(true)

        manager.start()
        manager.stop()

        verify(smsRestServer).startServer()
        verify(smsRestServer).stopServer()
    }
}

