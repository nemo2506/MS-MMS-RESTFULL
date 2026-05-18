package com.miseservice.smsovh.data.repository

import com.miseservice.smsovh.domain.repository.CredentialsRepository
import com.miseservice.smsovh.data.datasource.LocalCredentialsDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsRepositoryImpl @Inject constructor(
    private val localDataSource: LocalCredentialsDataSource
) : CredentialsRepository {
    override fun saveCredentials(login: String, password: String) {
        localDataSource.saveCredentials(login, password)
    }
    override fun getLogin(): String? = localDataSource.getLogin()
    override fun getPassword(): String? = localDataSource.getPassword()
    override fun clearCredentials() {
        localDataSource.clearCredentials()
    }
}

