package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fines")
data class Fine(
    @PrimaryKey
    val fineId: String,
    val userId: String,
    val userName: String,
    val studentId: String = "",
    val borrowId: String = "",
    val bookTitle: String,
    val overdueDays: Int,
    val amount: Double,
    val status: String = "Unpaid", // "Unpaid", "Paid", "Waived"
    val createdAt: Long = System.currentTimeMillis()
)
