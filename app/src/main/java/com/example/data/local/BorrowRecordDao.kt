package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BorrowRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowRecordDao {
    @Query("SELECT * FROM borrow_records ORDER BY borrowDate DESC")
    fun getAllRecords(): Flow<List<BorrowRecord>>

    @Query("SELECT * FROM borrow_records WHERE userId = :userId ORDER BY borrowDate DESC")
    fun getRecordsByUserId(userId: String): Flow<List<BorrowRecord>>

    @Query("SELECT * FROM borrow_records WHERE borrowId = :id LIMIT 1")
    suspend fun getRecordById(id: String): BorrowRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BorrowRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<BorrowRecord>)

    @Update
    suspend fun updateRecord(record: BorrowRecord)

    @Delete
    suspend fun deleteRecord(record: BorrowRecord)
}
