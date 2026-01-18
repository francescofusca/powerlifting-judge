package com.ff9.poweliftjudge.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LiftDao {
    @Insert
    suspend fun insertLift(lift: Lift)

    @Query("SELECT * FROM Lift ORDER BY date DESC")
    suspend fun getAll(): List<Lift>

    @Query("DELETE FROM Lift")
    suspend fun deleteAll()

    @Query("UPDATE Lift SET reps = :newReps WHERE id = :liftId")
    suspend fun updateReps(liftId: Int, newReps: Int)

    @Query("DELETE FROM Lift WHERE id = :liftId")
    suspend fun deleteLift(liftId: Int)
}
