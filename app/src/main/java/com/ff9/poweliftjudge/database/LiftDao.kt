package com.ff9.poweliftjudge.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LiftDao {
    @Insert
    suspend fun insertLift(lift: Lift)

    @Query("SELECT * FROM Lift ORDER BY date DESC")
    suspend fun getAll(): List<Lift>

    @Query("SELECT * FROM Lift ORDER BY date DESC")
    fun getAllFlow(): Flow<List<Lift>>

    @Query("SELECT * FROM Lift WHERE id = :id")
    fun getLiftByIdFlow(id: Int): Flow<Lift?>

    @Query("DELETE FROM Lift")
    suspend fun deleteAll()

    @Query("UPDATE Lift SET reps = :newReps WHERE id = :liftId")
    suspend fun updateReps(liftId: Int, newReps: Int)

    @Query("DELETE FROM Lift WHERE id = :liftId")
    suspend fun deleteLift(liftId: Int)

    @Query("UPDATE Lift SET notes = :newNotes WHERE id = :liftId")
    suspend fun updateNotes(liftId: Int, newNotes: String)

    @Query("SELECT * FROM Lift WHERE type = :type ORDER BY date DESC")
    fun getByTypeFlow(type: String): Flow<List<Lift>>

    @Query("DELETE FROM Lift WHERE id IN (:ids)")
    suspend fun deleteLifts(ids: List<Int>)

    @Query("UPDATE Lift SET weight = :weight, weightUnit = :weightUnit WHERE id = :liftId")
    suspend fun updateWeight(liftId: Int, weight: Double, weightUnit: String)

    @Query("SELECT MAX(weight) FROM Lift WHERE type = :type")
    suspend fun getMaxWeightForType(type: String): Double?

    @Query("UPDATE Lift SET rpe = :rpe WHERE id = :liftId")
    suspend fun updateRpe(liftId: Int, rpe: Int)
}
