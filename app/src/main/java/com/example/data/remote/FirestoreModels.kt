package com.example.data.remote

import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.NotificationItem
import com.example.data.model.Reservation
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreBook(
    @DocumentId val id: String = "",
    val campusId: String = "main",
    val createdByUid: String = "",
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val category: String = "General",
    val publisher: String = "",
    val publicationYear: Long = 2024L,
    val description: String = "",
    val coverImage: String = "",
    val coverGradientIndex: Long = 0L,
    val totalCopies: Long = 1L,
    val availableCopies: Long = 1L,
    val shelfNumber: String = "Shelf A-1",
    val rating: Double = 4.8,
    val status: String = "Available",
    val availabilityStatus: String = "Available",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomBook(): Book {
        val createdMs = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        return Book(
            bookId = id,
            title = title,
            author = author,
            isbn = isbn,
            category = category,
            publisher = publisher,
            publicationYear = publicationYear.toInt(),
            description = description,
            coverImage = coverImage,
            coverGradientIndex = coverGradientIndex.toInt(),
            totalCopies = totalCopies.toInt(),
            availableCopies = availableCopies.toInt(),
            shelfNumber = shelfNumber,
            rating = rating.toFloat(),
            status = status,
            availabilityStatus = availabilityStatus,
            createdAt = createdMs
        )
    }
}

@IgnoreExtraProperties
data class FirestoreUserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val preferredRole: String = "STUDENT",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreMemberProfile(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val studentId: String = "",
    val department: String = "",
    val roleLabel: String = "STUDENT",
    val membershipStatus: String = "Active",
    val totalBorrowedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomUser(privateEmail: String = ""): User {
        val resolvedRole = if (roleLabel == "ADMIN") UserRole.ADMIN else UserRole.STUDENT
        val fallbackEmail = privateEmail.ifBlank {
            "${name.lowercase().replace(" ", ".").filter { it.isLetterOrDigit() || it == '.' }}@campus.edu"
        }
        return User(
            userId = id,
            name = name,
            email = fallbackEmail,
            phone = "",
            studentId = studentId,
            department = department,
            role = resolvedRole,
            membershipStatus = membershipStatus,
            totalBorrowedCount = totalBorrowedCount.toInt(),
            createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}

@IgnoreExtraProperties
data class FirestoreLoan(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val userId: String = "",
    val userName: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val borrowDateMs: Long = 0L,
    val dueDateMs: Long = 0L,
    val returnDateMs: Long? = null,
    val status: String = "Borrowed",
    val fineAmount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomBorrowRecord(): BorrowRecord = BorrowRecord(
        borrowId = id,
        userId = userId,
        userName = userName,
        bookId = bookId,
        bookTitle = bookTitle,
        bookAuthor = bookAuthor,
        borrowDate = if (borrowDateMs > 0L) borrowDateMs else System.currentTimeMillis(),
        dueDate = if (dueDateMs > 0L) dueDateMs else System.currentTimeMillis(),
        returnDate = returnDateMs,
        status = status,
        fineAmount = fineAmount
    )
}

@IgnoreExtraProperties
data class FirestoreReservation(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val userId: String = "",
    val userName: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val reservationDateMs: Long = 0L,
    val status: String = "Pending",
    val pickupLocation: String = "Main Library, Desk 1",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomReservation(): Reservation = Reservation(
        reservationId = id,
        userId = userId,
        userName = userName,
        bookId = bookId,
        bookTitle = bookTitle,
        reservationDate = if (reservationDateMs > 0L) reservationDateMs else System.currentTimeMillis(),
        status = status,
        pickupLocation = pickupLocation
    )
}

@IgnoreExtraProperties
data class FirestoreFine(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val userId: String = "",
    val userName: String = "",
    val studentId: String = "",
    val borrowId: String = "",
    val bookTitle: String = "",
    val overdueDays: Long = 0L,
    val amount: Double = 0.0,
    val status: String = "Unpaid",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomFine(): Fine = Fine(
        fineId = id,
        userId = userId,
        userName = userName,
        studentId = studentId,
        borrowId = borrowId,
        bookTitle = bookTitle,
        overdueDays = overdueDays.toInt(),
        amount = amount,
        status = status,
        createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}

@IgnoreExtraProperties
data class FirestoreNotification(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "INFO",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toRoomNotification(): NotificationItem = NotificationItem(
        notificationId = id,
        userId = userId,
        title = title,
        message = message,
        type = type,
        isRead = isRead,
        createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}

fun Book.toCreatePayload(createdByUid: String): Map<String, Any> {
    return mapOf<String, Any?>(
        "campusId" to "main",
        "createdByUid" to createdByUid,
        "title" to title.take(200),
        "author" to author.take(150),
        "isbn" to isbn.take(40),
        "category" to category.ifBlank { "General" }.take(60),
        "publisher" to publisher.take(120),
        "publicationYear" to publicationYear.coerceIn(1000, 2100),
        "description" to description.take(2000),
        "coverImage" to coverImage.take(500),
        "coverGradientIndex" to coverGradientIndex.coerceIn(0, 10),
        "totalCopies" to totalCopies.coerceIn(0, 1000),
        "availableCopies" to availableCopies.coerceIn(0, totalCopies.coerceAtLeast(0)),
        "shelfNumber" to shelfNumber.ifBlank { "Shelf A-1" }.take(60),
        "rating" to rating.toDouble().coerceIn(0.0, 5.0),
        "status" to status,
        "availabilityStatus" to availabilityStatus,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

fun Book.toUpdatePayload(): Map<String, Any> {
    return mapOf<String, Any?>(
        "title" to title.take(200),
        "author" to author.take(150),
        "isbn" to isbn.take(40),
        "category" to category.ifBlank { "General" }.take(60),
        "publisher" to publisher.take(120),
        "publicationYear" to publicationYear.coerceIn(1000, 2100),
        "description" to description.take(2000),
        "coverImage" to coverImage.take(500),
        "coverGradientIndex" to coverGradientIndex.coerceIn(0, 10),
        "totalCopies" to totalCopies.coerceIn(0, 1000),
        "availableCopies" to availableCopies.coerceIn(0, totalCopies.coerceAtLeast(0)),
        "shelfNumber" to shelfNumber.ifBlank { "Shelf A-1" }.take(60),
        "rating" to rating.toDouble().coerceIn(0.0, 5.0),
        "status" to status,
        "availabilityStatus" to availabilityStatus,
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}
