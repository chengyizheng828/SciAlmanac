package com.cyz.scialmanac.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val fortunes: List<FortuneEntity> = emptyList(),
    val diaries: List<DiaryEntity> = emptyList(),
)

object BackupManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(context: Context): String {
        val db = DatabaseHolder.get(context)
        val data = BackupData(
            fortunes = db.fortuneDao().latest(10000),
            diaries = db.diaryDao().latest(10000),
        )
        return json.encodeToString(data)
    }

    suspend fun import(context: Context, content: String): Pair<Int, Int> {
        val data = json.decodeFromString<BackupData>(content)
        val db = DatabaseHolder.get(context)
        var fortuneCount = 0
        var diaryCount = 0
        for (f in data.fortunes) {
            db.fortuneDao().upsert(f)
            fortuneCount++
        }
        for (d in data.diaries) {
            db.diaryDao().upsert(d)
            diaryCount++
        }
        return fortuneCount to diaryCount
    }
}
