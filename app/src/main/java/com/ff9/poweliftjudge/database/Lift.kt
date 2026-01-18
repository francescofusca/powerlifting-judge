package com.ff9.poweliftjudge.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Lift(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val type: String,
    val date: Long,
    val valid: Boolean,
    val reps: Int = 1,
    val repTimes: String = "", // JSON array of times between reps in milliseconds
    val totalTime: Long = 0, // Total time for the set in milliseconds
    val notes: String = "" // User notes/comments
)
