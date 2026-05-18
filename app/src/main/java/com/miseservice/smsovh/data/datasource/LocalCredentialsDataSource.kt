package com.miseservice.smsovh.data.datasource

interface LocalCredentialsDataSource {
    fun saveCredentials(login: String, password: String)
    fun getLogin(): String?
    fun getPassword(): String?
    fun clearCredentials()
}

