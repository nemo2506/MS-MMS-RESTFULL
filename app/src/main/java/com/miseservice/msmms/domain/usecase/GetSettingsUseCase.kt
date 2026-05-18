package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.data.local.AppSettingsEntity
import com.miseservice.msmms.data.repository.SettingsRepository
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AppSettingsEntity? = repository.getSettings()
}

