package com.moviecat.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val label: String,
    val url: String,
    val kind: String,
    val isPinned: Boolean,
    val updatedAt: Long,
    val settingsJson: String
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val entryKey: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val playUrl: String,
    val note: String?,
    val updatedAt: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val entryKey: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val playUrl: String,
    val note: String?,
    val updatedAt: Long,
    val lastPositionMs: Long
)
