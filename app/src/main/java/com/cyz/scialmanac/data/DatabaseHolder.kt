package com.cyz.scialmanac.data

import android.content.Context
import androidx.room.Room

object DatabaseHolder {
    @Volatile
    private var db: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "scialmanac.db"
            ).build().also { db = it }
        }
}
