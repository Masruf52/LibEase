package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationItem(
    @PrimaryKey
    val notificationId: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String = "INFO", // "APPROVAL", "REJECTION", "REMINDER", "OVERDUE", "RESERVATION", "NEW_BOOK"
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
