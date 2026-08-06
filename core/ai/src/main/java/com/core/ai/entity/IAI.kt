package com.core.ai.entity

interface IAI {
    suspend fun chat(message: String): String

    var modelSet: MutableSet<String>

    //todo request model
}