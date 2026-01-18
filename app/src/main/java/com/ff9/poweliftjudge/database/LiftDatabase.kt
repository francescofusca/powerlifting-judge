package com.ff9.poweliftjudge.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Lift::class], version = 3)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun liftDao(): LiftDao

    companion object {
        @Volatile
        private var INSTANCE: LiftDatabase? = null

        fun getDatabase(context: Context): LiftDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LiftDatabase::class.java,
                    "lift_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
