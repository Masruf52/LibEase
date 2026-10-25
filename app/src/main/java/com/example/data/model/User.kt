package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    ADMIN,
    STUDENT
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val userId: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val studentId: String = "",
    val department: String = "",
    val role: UserRole = UserRole.STUDENT,
    val profileImage: String = "",
    val membershipStatus: String = "Active", // "Active", "Inactive", "Pending"
    val totalBorrowedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
