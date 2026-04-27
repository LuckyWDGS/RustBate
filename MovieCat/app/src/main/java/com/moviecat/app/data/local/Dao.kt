package com.moviecat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SourceEntity>)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE entryKey = :entryKey)")
    suspend fun exists(entryKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE entryKey = :entryKey")
    suspend fun deleteById(entryKey: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)
}

