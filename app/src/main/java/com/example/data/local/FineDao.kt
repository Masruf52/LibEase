package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Fine
import kotlinx.coroutines.flow.Flow

@Dao
interface FineDao {
    @Query("SELECT * FROM fines ORDER BY createdAt DESC")
    fun getAllFines(): Flow<List<Fine>>

    @Query("SELECT * FROM fines WHERE userId = :userId ORDER BY createdAt DESC")
    fun getFinesByUserId(userId: String): Flow<List<Fine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFine(fine: Fine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFines(fines: List<Fine>)

    @Update
    suspend fun updateFine(fine: Fine)

    @Delete
    suspend fun deleteFine(fine: Fine)
}
