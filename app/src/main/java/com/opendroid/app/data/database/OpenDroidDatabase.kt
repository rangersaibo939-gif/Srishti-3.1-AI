package com.opendroid.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TaskEntity::class,
        TaskStepEntity::class,
        MemoryEntity::class,
        ConversationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OpenDroidDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val DATABASE_NAME = "srishti_core.db"

        @Volatile
        private var INSTANCE: OpenDroidDatabase? = null

        fun getDatabase(context: Context): OpenDroidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OpenDroidDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
