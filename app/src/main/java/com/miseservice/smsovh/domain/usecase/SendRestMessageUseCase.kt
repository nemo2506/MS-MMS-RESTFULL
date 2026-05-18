package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.model.SendMessageRequest
import com.miseservice.smsovh.model.SendResult
import javax.inject.Inject

/**
 * Use case de compatibilité pour l'ancien flux API.
 *
 * Il délègue au flux unifié `SendMessageUseCase` qui gère SMS ou MMS selon le contenu.
 */
@Suppress("unused")
class SendRestMessageUseCase @Inject constructor(
	private val sendMessageUseCase: SendMessageUseCase
) {
	suspend operator fun invoke(request: SendMessageRequest): SendResult =
		sendMessageUseCase(request)
}

