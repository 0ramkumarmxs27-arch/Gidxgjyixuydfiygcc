package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String, // "user" or "myra"
    val text: String,
    val attachmentLabel: String? = null,
    val actionExecuted: String? = null,
    val isSamplePlaceholder: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fact: String,
    val category: String = "Personal",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks_reminders")
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val dueTimeMillis: Long? = null,
    val isReminder: Boolean = false,
    val isCompleted: Boolean = false,
    val priority: String = "Normal",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_profile")
data class VoiceProfileEntity(
    @PrimaryKey val id: Int = 1,
    val ownerName: String,
    val featureVectorCsv: String,
    val sampleCount: Int,
    val enrolledAt: Long = System.currentTimeMillis()
)
