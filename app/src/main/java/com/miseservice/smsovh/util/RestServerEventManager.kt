package com.miseservice.smsovh.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Singleton

/**
 * Événement d'API REST pour notifier l'UI des actions du serveur REST
 */
data class RestServerEvent(
    val type: RestServerEventType,
    val message: String
)

enum class RestServerEventType {
    SMS_SENT_SUCCESS,
    SMS_SENT_ERROR,
    LOG_RECEIVED_SUCCESS,
    LOG_RECEIVED_ERROR,
    SERVER_START_SUCCESS,
    SERVER_START_ERROR,
    SERVER_PORT_IN_USE
}

/**
 * Manager singleton pour les événements du serveur REST
 * Permet au serveur REST de notifier l'UI via les événements
 */
@Singleton
class RestServerEventManager {
    private val _eventFlow = MutableSharedFlow<RestServerEvent>(replay = 0)
    val eventFlow: SharedFlow<RestServerEvent> = _eventFlow.asSharedFlow()

    suspend fun emitEvent(event: RestServerEvent) {
        _eventFlow.emit(event)
    }
}
