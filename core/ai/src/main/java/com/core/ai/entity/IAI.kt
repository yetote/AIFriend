package com.core.ai.entity

interface IAI {
    suspend fun requestModel()

    var modelSet: MutableSet<String>

    //todo request model
    suspend fun chat(message: String, model: String): String
}