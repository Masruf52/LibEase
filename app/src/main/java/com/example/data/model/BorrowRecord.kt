package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BorrowStatus {
    PENDING,
    APPROVED,
    BORROWED,
    RETURNED,
    REJECTED,
    OVERDUE
}

@Entity(tableName = "borrow_records")
data class BorrowRecord(
    @PrimaryKey
    val borrowId: String,
    val userId: String,
    val userName: String,
    val bookId: String,
    val bookTitle: String,
    val bookAuthor: String,
    val borrowDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000),
    val returnDate: Long? = null,
    val status: String = "Borrowed", // "Pending", "Approved", "Borrowed", "Returned", "Rejected", "Overdue"
    val fineAmount: Double = 0.0
)
