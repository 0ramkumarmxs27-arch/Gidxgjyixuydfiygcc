package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MyraDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    @Query("SELECT * FROM memory_facts ORDER BY timestamp DESC")
    fun observeMemoryFacts(): Flow<List<MemoryFactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryFact(fact: MemoryFactEntity): Long

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemoryFact(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllMemoryFacts()

    @Query("SELECT * FROM tasks_reminders ORDER BY isCompleted ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskReminderEntity): Long

    @Update
    suspend fun updateTask(task: TaskReminderEntity)

    @Query("DELETE FROM tasks_reminders WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT * FROM voice_profile WHERE id = 1")
    fun observeVoiceProfile(): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profile WHERE id = 1")
    suspend fun getVoiceProfile(): VoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceProfile(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profile")
    suspend fun clearVoiceProfile()
}
