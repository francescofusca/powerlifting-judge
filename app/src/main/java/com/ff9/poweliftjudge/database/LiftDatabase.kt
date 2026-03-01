package com.ff9.poweliftjudge.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

//classe che rappresenta intero database dell app.

@Database(entities = [Lift::class], version = 5)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun liftDao(): LiftDao

        //definisce quali entita fanno parte del db
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
