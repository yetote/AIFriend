package com.core.ai.entity

import android.util.Log
import com.core.ai.GEMINI_KEY
import com.core.ai.model.AIRequestBody
import com.core.repository.WebRepository

class AIGemini() : IAI {
    override suspend fun chat(message: String): String {

        val content = WebRepository.instance.chat<AIRequestBody>(
            "https://generativelanguage.googleapis.com/v1beta/interactions",
            AIRequestBody("gemini-3.6-flash", message),
            GEMINI_KEY
        )
        Log.i("AIGemini", "chat: $content ")
        return "Gemini AI response to: $content"
    }

    override var modelSet: MutableSet<String> = mutableSetOf()

}