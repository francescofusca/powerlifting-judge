package com.ff9.poweliftjudge.data.repository

import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.database.LiftDao
import kotlinx.coroutines.flow.Flow

class LiftRepository(private val dao: LiftDao) {

    fun getAllLiftsFlow(): Flow<List<Lift>> = dao.getAllFlow()

    fun getLiftByIdFlow(id: Int): Flow<Lift?> = dao.getLiftByIdFlow(id)

    suspend fun insertLift(lift: Lift) = dao.insertLift(lift)

    suspend fun updateReps(liftId: Int, newReps: Int) = dao.updateReps(liftId, newReps)

    suspend fun deleteLift(liftId: Int) = dao.deleteLift(liftId)

    suspend fun updateNotes(liftId: Int, newNotes: String) = dao.updateNotes(liftId, newNotes)

    suspend fun updateWeight(liftId: Int, weight: Double, weightUnit: String) = dao.updateWeight(liftId, weight, weightUnit)

    suspend fun deleteAll() = dao.deleteAll()

    fun getLiftsByTypeFlow(type: String): Flow<List<Lift>> = dao.getByTypeFlow(type)

    suspend fun deleteLifts(ids: List<Int>) = dao.deleteLifts(ids)

    suspend fun getMaxWeightForType(type: String): Double? = dao.getMaxWeightForType(type)

    suspend fun updateRpe(liftId: Int, rpe: Int) = dao.updateRpe(liftId, rpe)
}
