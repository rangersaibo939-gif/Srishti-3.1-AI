package com.opendroid.app

import android.app.Application
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.security.KeystoreSecretProvider
import com.opendroid.app.data.database.OpenDroidDatabase
import com.opendroid.app.data.repository.TaskRepository

/**
 * Main Application class for OpenDroid
 * Initializes Keystore, Room SQLite database, and Repository
 */
class OpenDroidApplication : Application() {

    val database: OpenDroidDatabase by lazy {
        OpenDroidDatabase.getDatabase(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }

    override fun onCreate() {
        super.onCreate()
        RedactedLogger.i(TAG, "OpenDroid Application initialized. Target: Android SDK 35 (arm64-v8a)")
        KeystoreSecretProvider.init(this)
    }

    companion object {
        private const val TAG = "OpenDroidApp"
    }
}
