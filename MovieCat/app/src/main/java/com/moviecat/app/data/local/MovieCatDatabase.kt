package com.moviecat.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, FavoriteEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MovieCatDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: MovieCatDatabase? = null

        fun getInstance(context: Context): MovieCatDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MovieCatDatabase::class.java,
                    "movie_cat.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
