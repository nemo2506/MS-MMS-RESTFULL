package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.local.AppSettingsEntity
import com.miseservice.smsovh.data.repository.SettingsRepository
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AppSettingsEntity? = repository.getSettings()
}

