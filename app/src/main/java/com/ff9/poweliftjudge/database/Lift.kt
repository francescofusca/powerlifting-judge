package com.ff9.poweliftjudge.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity //data class che definisce le colonne
data class Lift(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val type: String,
    val date: Long,
    val valid: Boolean,
    val reps: Int = 1,
    val repTimes: String = "", // JSON array del tempo in mezzo alle rep in millisec
    val totalTime: Long = 0, // tempo totale rep in millisec
    val notes: String = "", // User note e commetni
    val weight: Double = 0.0,
    val weightUnit: String = "kg",
    val rpe: Int = 0
)
