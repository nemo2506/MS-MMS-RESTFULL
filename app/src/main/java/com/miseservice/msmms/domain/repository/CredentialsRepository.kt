package com.miseservice.msmms.domain.repository

interface CredentialsRepository {
    fun saveCredentials(login: String, password: String)
    fun getLogin(): String?
    fun getPassword(): String?
    fun clearCredentials()
}

