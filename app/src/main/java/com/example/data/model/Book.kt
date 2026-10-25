package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val bookId: String,
    val title: String,
    val author: String,
    val isbn: String,
    val category: String = "General", // "Programming", "Fiction", "Science", "History", "Business", "AI & Data"
    val publisher: String = "",
    val publicationYear: Int = 2023,
    val description: String = "",
    val coverImage: String = "",
    val coverGradientIndex: Int = 0,
    val totalCopies: Int = 1,
    val availableCopies: Int = 1,
    val shelfNumber: String = "Shelf A-1",
    val rating: Float = 4.8f,
    val status: String = "Available", // "Available", "Borrowed", "Reserved"
    val availabilityStatus: String = status, // "Available", "Borrowed", "Reserved", "Out of Stock"
    val createdAt: Long = System.currentTimeMillis()
) {
    val isAvailable: Boolean
        get() = availableCopies > 0 && (
            availabilityStatus.equals("Available", ignoreCase = true) ||
            status.equals("Available", ignoreCase = true)
        )
}

