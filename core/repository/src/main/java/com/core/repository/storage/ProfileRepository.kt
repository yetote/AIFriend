package com.core.repository.storage

import android.content.Context
import com.core.repository.storage.db.AppDatabase
import com.core.repository.storage.db.ProfileEntity

class ProfileRepository(context: Context) {

    private val dao = AppDatabase.get(context).profileDao()

    suspend fun loadAvatarPath(): String? = dao.avatarPath()

    suspend fun saveAvatarPath(path: String) {
        dao.upsert(ProfileEntity(avatarPath = path))
    }
}
