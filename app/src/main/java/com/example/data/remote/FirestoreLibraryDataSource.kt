package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.NotificationItem
import com.example.data.model.Reservation
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreLibraryDataSource(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- 1. Books Catalog (/books/{bookId}) ---

    fun observeBooks(): Flow<List<Book>> {
        val path = "books"
        return db.collection(path)
            .whereEqualTo("campusId", "main")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreBook::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toRoomBook() }
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getBookById(bookId: String): Book? {
        val docRef = db.collection("books").document(bookId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(
                FirestoreBook::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toRoomBook()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createBook(book: Book, createdByUid: String) {
        val docRef = db.collection("books").document(book.bookId)
        try {
            docRef.set(book.toCreatePayload(createdByUid)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateBook(book: Book) {
        val docRef = db.collection("books").document(book.bookId)
        try {
            docRef.update(book.toUpdatePayload()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adjustBookStock(bookId: String, availableCopies: Int, status: String) {
        val docRef = db.collection("books").document(bookId)
        val payload = mapOf<String, Any>(
            "availableCopies" to availableCopies.coerceAtLeast(0),
            "status" to status,
            "availabilityStatus" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteBook(bookId: String) {
        val docRef = db.collection("books").document(bookId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 2. Private User Account & Librarian Opt-In (/users/{uid}, /librarians/{uid}) ---

    suspend fun getUserAccount(uid: String): FirestoreUserAccount? {
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(
                FirestoreUserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeUserAccount(uid: String): Flow<FirestoreUserAccount?> {
        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(
                    FirestoreUserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun upsertUserAccount(
        uid: String,
        name: String,
        email: String,
        phone: String = "",
        preferredRole: UserRole
    ) {
        val docRef = db.collection("users").document(uid)
        val roleString = if (preferredRole == UserRole.ADMIN) "ADMIN" else "STUDENT"
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updatePayload = mapOf<String, Any>(
                    "name" to name.ifBlank { "Library Member" }.take(120),
                    "email" to email.ifBlank { "$uid@libease.edu" }.take(200),
                    "phone" to phone.take(40),
                    "preferredRole" to roleString,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf<String, Any>(
                    "userId" to uid,
                    "name" to name.ifBlank { "Library Member" }.take(120),
                    "email" to email.ifBlank { "$uid@libease.edu" }.take(200),
                    "phone" to phone.take(40),
                    "preferredRole" to roleString,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun setLibrarianMode(uid: String, isAdmin: Boolean) {
        val libRef = db.collection("librarians").document(uid)
        try {
            val existing = libRef.get().await()
            if (existing.exists()) {
                libRef.update(
                    mapOf(
                        "active" to isAdmin,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                libRef.set(
                    mapOf(
                        "userId" to uid,
                        "active" to isAdmin,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "preferredRole" to if (isAdmin) "ADMIN" else "STUDENT",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, libRef.path)
            throw e
        }
    }

    // --- 3. Non-PII Member Profiles (/member_profiles/{memberId}) ---

    fun observeMemberProfiles(ownerUid: String): Flow<List<FirestoreMemberProfile>> {
        val path = "member_profiles"
        return db.collection(path)
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreMemberProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun upsertMemberProfile(user: User, ownerUid: String) {
        val docRef = db.collection("member_profiles").document(user.userId)
        val roleString = if (user.role == UserRole.ADMIN) "ADMIN" else "STUDENT"
        val cleanStudentId = user.studentId.ifBlank { "LIB-${user.userId.takeLast(4).uppercase()}" }.take(60)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "name" to user.name.ifBlank { "Member" }.take(120),
                        "studentId" to cleanStudentId,
                        "department" to user.department.take(120),
                        "roleLabel" to roleString,
                        "membershipStatus" to user.membershipStatus,
                        "totalBorrowedCount" to user.totalBorrowedCount.coerceAtLeast(0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerUid" to ownerUid,
                        "name" to user.name.ifBlank { "Member" }.take(120),
                        "studentId" to cleanStudentId,
                        "department" to user.department.take(120),
                        "roleLabel" to roleString,
                        "membershipStatus" to user.membershipStatus,
                        "totalBorrowedCount" to user.totalBorrowedCount.coerceAtLeast(0),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemberProfile(memberId: String) {
        val docRef = db.collection("member_profiles").document(memberId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. Circulation Loans (/loans/{loanId}) ---

    fun observeLoans(ownerUid: String): Flow<List<BorrowRecord>> {
        val path = "loans"
        return db.collection(path)
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreLoan::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toRoomBorrowRecord() }
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createLoan(record: BorrowRecord, ownerUid: String) {
        val docRef = db.collection("loans").document(record.borrowId)
        val payload = mutableMapOf<String, Any>(
            "ownerUid" to ownerUid,
            "userId" to record.userId,
            "userName" to record.userName.ifBlank { "Member" }.take(120),
            "bookId" to record.bookId,
            "bookTitle" to record.bookTitle.take(200),
            "bookAuthor" to record.bookAuthor.ifBlank { "Unknown" }.take(150),
            "borrowDateMs" to record.borrowDate.coerceAtLeast(1L),
            "dueDateMs" to record.dueDate.coerceAtLeast(record.borrowDate.coerceAtLeast(1L)),
            "status" to record.status,
            "fineAmount" to record.fineAmount.coerceAtLeast(0.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        record.returnDate?.let { payload["returnDateMs"] = it }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateLoanReturn(borrowId: String, returnDateMs: Long, fineAmount: Double) {
        val docRef = db.collection("loans").document(borrowId)
        try {
            docRef.update(
                mapOf(
                    "returnDateMs" to returnDateMs,
                    "status" to "Returned",
                    "fineAmount" to fineAmount.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateLoanRenewal(borrowId: String, newDueDateMs: Long) {
        val docRef = db.collection("loans").document(borrowId)
        try {
            docRef.update(
                mapOf(
                    "dueDateMs" to newDueDateMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 5. Reservations (/reservations/{reservationId}) ---

    fun observeReservations(ownerUid: String): Flow<List<Reservation>> {
        val path = "reservations"
        return db.collection(path)
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreReservation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toRoomReservation() }
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createReservation(reservation: Reservation, ownerUid: String) {
        val docRef = db.collection("reservations").document(reservation.reservationId)
        val payload = mapOf<String, Any>(
            "ownerUid" to ownerUid,
            "userId" to reservation.userId,
            "userName" to reservation.userName.ifBlank { "Member" }.take(120),
            "bookId" to reservation.bookId,
            "bookTitle" to reservation.bookTitle.take(200),
            "reservationDateMs" to reservation.reservationDate.coerceAtLeast(1L),
            "status" to reservation.status,
            "pickupLocation" to reservation.pickupLocation.ifBlank { "Main Library, Desk 1" }.take(120),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteReservation(reservationId: String) {
        val docRef = db.collection("reservations").document(reservationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 6. Fines (/fines/{fineId}) ---

    fun observeFines(ownerUid: String): Flow<List<Fine>> {
        val path = "fines"
        return db.collection(path)
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreFine::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toRoomFine() }
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createFine(fine: Fine, ownerUid: String) {
        val docRef = db.collection("fines").document(fine.fineId)
        val payload = mapOf<String, Any>(
            "ownerUid" to ownerUid,
            "userId" to fine.userId,
            "userName" to fine.userName.ifBlank { "Member" }.take(120),
            "studentId" to fine.studentId.take(60),
            "borrowId" to fine.borrowId.take(128),
            "bookTitle" to fine.bookTitle.take(200),
            "overdueDays" to fine.overdueDays.coerceAtLeast(0),
            "amount" to fine.amount.coerceAtLeast(0.0),
            "status" to fine.status,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateFineStatus(fineId: String, status: String) {
        val docRef = db.collection("fines").document(fineId)
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 7. Notifications (/notifications/{notificationId}) ---

    fun observeNotifications(ownerUid: String): Flow<List<NotificationItem>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreNotification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toRoomNotification() }
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createNotification(item: NotificationItem, ownerUid: String) {
        val docRef = db.collection("notifications").document(item.notificationId)
        val payload = mapOf<String, Any>(
            "ownerUid" to ownerUid,
            "userId" to item.userId,
            "title" to item.title.ifBlank { "Library Notice" }.take(120),
            "message" to item.message.ifBlank { "Update from LibEase" }.take(600),
            "type" to item.type,
            "isRead" to item.isRead,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        val docRef = db.collection("notifications").document(notificationId)
        try {
            docRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
