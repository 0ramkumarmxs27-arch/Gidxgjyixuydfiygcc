package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        MemoryFactEntity::class,
        TaskReminderEntity::class,
        VoiceProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MyraDatabase : RoomDatabase() {
    abstract fun myraDao(): MyraDao

    companion object {
        @Volatile
        private var INSTANCE: MyraDatabase? = null

        fun getInstance(context: Context): MyraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MyraDatabase::class.java,
                    "myra_ai_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
