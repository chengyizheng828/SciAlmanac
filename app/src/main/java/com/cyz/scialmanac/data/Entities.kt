package com.cyz.scialmanac.data

import androidx.room.Dao
import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Database

@Serializable
@Entity(tableName = "fortunes")
data class FortuneEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val luckLevel: String,
    val themeColor: String,
    val yi: String,   // JSON array string
    val ji: String,   // JSON array string
    val aiComment: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val content: String,
    val tag: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface FortuneDao {
    @Query("SELECT * FROM fortunes WHERE date = :date")
    suspend fun get(date: String): FortuneEntity?

    @Query("SELECT * FROM fortunes WHERE date BETWEEN :start AND :end")
    suspend fun getBetween(start: String, end: String): List<FortuneEntity>

    @Query("SELECT * FROM fortunes ORDER BY date DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<FortuneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FortuneEntity)

    @Query("DELETE FROM fortunes WHERE date = :date")
    suspend fun delete(date: String)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE date = :date ORDER BY id DESC")
    suspend fun getByDate(date: String): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE date BETWEEN :start AND :end")
    suspend fun getBetween(start: String, end: String): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE id = :id")
    suspend fun get(id: Long): DiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DiaryEntity): Long

    @Query("DELETE FROM diaries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [FortuneEntity::class, DiaryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fortuneDao(): FortuneDao
    abstract fun diaryDao(): DiaryDao
}
