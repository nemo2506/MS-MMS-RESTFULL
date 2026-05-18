package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.domain.repository.CredentialsRepository

class SaveCredentialsUseCase(private val repository: CredentialsRepository) {
    operator fun invoke(login: String, password: String) = repository.saveCredentials(login, password)
}

class GetLoginUseCase(private val repository: CredentialsRepository) {
    operator fun invoke(): String? = repository.getLogin()
}

class GetPasswordUseCase(private val repository: CredentialsRepository) {
    operator fun invoke(): String? = repository.getPassword()
}

class ClearCredentialsUseCase(private val repository: CredentialsRepository) {
    operator fun invoke() = repository.clearCredentials()
}

