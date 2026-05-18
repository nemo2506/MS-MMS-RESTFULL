package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.repository.SettingsRepository
import javax.inject.Inject

class UpdateRestPortUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(restPort: Int) = repository.updateRestPort(restPort)
}

