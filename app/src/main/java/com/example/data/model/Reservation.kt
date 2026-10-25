package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations")
data class Reservation(
    @PrimaryKey
    val reservationId: String,
    val userId: String,
    val userName: String,
    val bookId: String,
    val bookTitle: String,
    val reservationDate: Long = System.currentTimeMillis(),
    val status: String = "Pending", // "Pending", "Ready for Pickup", "Completed", "Cancelled"
    val pickupLocation: String = "Main Library, Desk 3"
)
