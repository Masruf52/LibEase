package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Reservation
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations ORDER BY reservationDate DESC")
    fun getAllReservations(): Flow<List<Reservation>>

    @Query("SELECT * FROM reservations WHERE userId = :userId ORDER BY reservationDate DESC")
    fun getReservationsByUserId(userId: String): Flow<List<Reservation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReservation(reservation: Reservation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReservations(reservations: List<Reservation>)

    @Update
    suspend fun updateReservation(reservation: Reservation)

    @Delete
    suspend fun deleteReservation(reservation: Reservation)

    @Query("DELETE FROM reservations WHERE reservationId = :id")
    suspend fun deleteReservationById(id: String)
}
