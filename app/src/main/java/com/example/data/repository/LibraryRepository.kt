package com.example.data.repository

import android.util.Log
import com.example.data.local.BookDao
import com.example.data.local.BorrowRecordDao
import com.example.data.local.FineDao
import com.example.data.local.NotificationDao
import com.example.data.local.ReservationDao
import com.example.data.local.UserDao
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.NotificationItem
import com.example.data.model.Reservation
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.remote.FirestoreLibraryDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryRepository(
    private val bookDao: BookDao,
    private val userDao: UserDao,
    private val borrowRecordDao: BorrowRecordDao,
    private val reservationDao: ReservationDao,
    private val fineDao: FineDao,
    private val notificationDao: NotificationDao,
    private val firestoreDataSource: FirestoreLibraryDataSource? = null
) {
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    val allBorrowRecords: Flow<List<BorrowRecord>> = borrowRecordDao.getAllRecords()
    val allReservations: Flow<List<Reservation>> = reservationDao.getAllReservations()
    val allFines: Flow<List<Fine>> = fineDao.getAllFines()
    val allNotifications: Flow<List<NotificationItem>> = notificationDao.getAllNotifications()

    @Volatile
    private var activeAuthUid: String? = null

    @Volatile
    private var activeAuthEmail: String = ""

    @Volatile
    private var initialCloudBootstrapDone: Boolean = false

    fun getRecordsForUser(userId: String): Flow<List<BorrowRecord>> =
        borrowRecordDao.getRecordsByUserId(userId)

    fun getReservationsForUser(userId: String): Flow<List<Reservation>> =
        reservationDao.getReservationsByUserId(userId)

    fun getFinesForUser(userId: String): Flow<List<Fine>> =
        fineDao.getFinesByUserId(userId)

    fun getNotificationsForUser(userId: String): Flow<List<NotificationItem>> =
        notificationDao.getNotificationsForUser(userId)

    /**
     * Starts bidirectional synchronization between Cloud Firestore (source of truth)
     * and the local Room database cache for an authenticated Google user session.
     */
    fun syncCloudSession(
        uid: String,
        email: String,
        displayName: String,
        preferredRole: UserRole,
        scope: CoroutineScope
    ): List<Job> {
        activeAuthUid = uid
        activeAuthEmail = email
        initialCloudBootstrapDone = false

        val jobs = mutableListOf<Job>()

        // 1. Bootstrap user account, librarian role registry, starter catalog, and local Room user
        jobs += scope.launch {
            val existingLocal = userDao.getUserById(uid)
            val cleanName = displayName.trim().ifBlank {
                existingLocal?.name?.ifBlank { null }
                    ?: if (email.contains("@")) email.substringBefore("@") else "Library Member"
            }
            val cleanEmail = email.trim().ifBlank {
                existingLocal?.email?.ifBlank { null } ?: "$uid@libease.edu"
            }
            val idSuffix = uid.filter { it.isLetterOrDigit() }.takeLast(4).uppercase().padStart(4, '0')
            val defaultCardId = if (preferredRole == UserRole.ADMIN) {
                "LIB-ADM-$idSuffix"
            } else {
                "LIB-STU-$idSuffix"
            }
            val authenticatedUser = User(
                userId = uid,
                name = cleanName,
                email = cleanEmail,
                phone = existingLocal?.phone ?: "",
                studentId = existingLocal?.studentId?.ifBlank { defaultCardId } ?: defaultCardId,
                department = existingLocal?.department?.ifBlank { null }
                    ?: if (preferredRole == UserRole.ADMIN) "Library Administration" else "Campus Circulation",
                role = preferredRole,
                membershipStatus = existingLocal?.membershipStatus ?: "Active",
                totalBorrowedCount = existingLocal?.totalBorrowedCount ?: 0
            )
            userDao.insertUser(authenticatedUser)

            val remote = firestoreDataSource ?: run {
                seedInitialDataIfNeeded()
                initialCloudBootstrapDone = true
                return@launch
            }

            try {
                remote.upsertUserAccount(
                    uid = uid,
                    name = authenticatedUser.name,
                    email = authenticatedUser.email,
                    phone = authenticatedUser.phone,
                    preferredRole = preferredRole
                )
                remote.setLibrarianMode(uid = uid, isAdmin = preferredRole == UserRole.ADMIN)
                remote.upsertMemberProfile(user = authenticatedUser, ownerUid = uid)

                // Seed starter catalog & campus records in Firestore if empty
                val existingCloudBooks = remote.observeBooks().first()
                if (existingCloudBooks.isEmpty()) {
                    val starterBooks = buildStarterBooks()
                    bookDao.insertBooks(starterBooks)
                    starterBooks.forEach { book ->
                        remote.createBook(book, createdByUid = uid)
                    }
                }

                val existingProfiles = remote.observeMemberProfiles(uid).first()
                if (existingProfiles.size <= 1) {
                    val starterUsers = buildStarterUsers()
                    userDao.insertUsers(starterUsers)
                    starterUsers.forEach { demoUser ->
                        remote.upsertMemberProfile(demoUser, ownerUid = uid)
                    }

                    val now = System.currentTimeMillis()
                    val starterLoans = buildStarterBorrowRecords(now, uid, authenticatedUser.name)
                    borrowRecordDao.insertRecords(starterLoans)
                    starterLoans.forEach { loan ->
                        runCatchingNonCancel { remote.createLoan(loan, ownerUid = uid) }
                    }

                    val starterReservations = buildStarterReservations(now, uid, authenticatedUser.name)
                    reservationDao.insertReservations(starterReservations)
                    starterReservations.forEach { hold ->
                        runCatchingNonCancel { remote.createReservation(hold, ownerUid = uid) }
                    }

                    val starterFines = buildStarterFines(uid, authenticatedUser.name, authenticatedUser.studentId)
                    fineDao.insertFines(starterFines)
                    starterFines.forEach { fine ->
                        runCatchingNonCancel { remote.createFine(fine, ownerUid = uid) }
                    }

                    val starterNotifications = buildStarterNotifications(now, uid)
                    notificationDao.insertNotifications(starterNotifications)
                    starterNotifications.forEach { notif ->
                        runCatchingNonCancel { remote.createNotification(notif, ownerUid = uid) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud session bootstrap encountered an error; continuing with local Room cache", e)
            } finally {
                initialCloudBootstrapDone = true
            }
        }

        val remote = firestoreDataSource ?: return jobs

        // 2. Real-time Firestore -> Room mirror streams
        jobs += scope.launch {
            remote.observeBooks()
                .catch { e -> Log.w(TAG, "Stopped observing cloud books", e) }
                .collect { cloudBooks ->
                    if (cloudBooks.isNotEmpty()) {
                        bookDao.insertBooks(cloudBooks)
                        if (initialCloudBootstrapDone) {
                            val cloudIds = cloudBooks.map { it.bookId }.toSet()
                            val localBooks = bookDao.getAllBooks().first()
                            localBooks.filter { it.bookId !in cloudIds }.forEach { stale ->
                                bookDao.deleteBookById(stale.bookId)
                            }
                        }
                    } else if (initialCloudBootstrapDone) {
                        bookDao.deleteAllBooks()
                    }
                }
        }

        jobs += scope.launch {
            remote.observeMemberProfiles(uid)
                .catch { e -> Log.w(TAG, "Stopped observing cloud member profiles", e) }
                .collect { profiles ->
                    if (profiles.isNotEmpty()) {
                        val localById = userDao.getAllUsers().first().associateBy { it.userId }
                        val mappedUsers = profiles.map { profile ->
                            val existing = localById[profile.id]
                            val resolvedEmail = when {
                                profile.id == uid && activeAuthEmail.isNotBlank() -> activeAuthEmail
                                !existing?.email.isNullOrBlank() -> existing!!.email
                                else -> ""
                            }
                            profile.toRoomUser(privateEmail = resolvedEmail).copy(
                                phone = existing?.phone ?: ""
                            )
                        }
                        userDao.insertUsers(mappedUsers)
                        if (initialCloudBootstrapDone) {
                            val cloudIds = profiles.map { it.id }.toSet()
                            localById.values.filter { it.userId !in cloudIds && it.userId != uid }.forEach { stale ->
                                userDao.deleteUser(stale)
                            }
                        }
                    }
                }
        }

        jobs += scope.launch {
            remote.observeLoans(uid)
                .catch { e -> Log.w(TAG, "Stopped observing cloud loans", e) }
                .collect { loans ->
                    if (loans.isNotEmpty()) {
                        borrowRecordDao.insertRecords(loans)
                    }
                }
        }

        jobs += scope.launch {
            remote.observeReservations(uid)
                .catch { e -> Log.w(TAG, "Stopped observing cloud reservations", e) }
                .collect { holds ->
                    if (holds.isNotEmpty()) {
                        reservationDao.insertReservations(holds)
                    }
                    if (initialCloudBootstrapDone) {
                        val cloudIds = holds.map { it.reservationId }.toSet()
                        val localHolds = reservationDao.getAllReservations().first()
                        localHolds.filter { it.reservationId !in cloudIds }.forEach { stale ->
                            reservationDao.deleteReservationById(stale.reservationId)
                        }
                    }
                }
        }

        jobs += scope.launch {
            remote.observeFines(uid)
                .catch { e -> Log.w(TAG, "Stopped observing cloud fines", e) }
                .collect { fines ->
                    if (fines.isNotEmpty()) {
                        fineDao.insertFines(fines)
                    }
                }
        }

        jobs += scope.launch {
            remote.observeNotifications(uid)
                .catch { e -> Log.w(TAG, "Stopped observing cloud notifications", e) }
                .collect { notifications ->
                    if (notifications.isNotEmpty()) {
                        notificationDao.insertNotifications(notifications)
                    }
                }
        }

        return jobs
    }

    fun clearCloudSession() {
        activeAuthUid = null
        activeAuthEmail = ""
        initialCloudBootstrapDone = false
    }

    suspend fun syncRolePreference(role: UserRole) {
        val uid = activeAuthUid ?: return
        val existing = userDao.getUserById(uid)
        if (existing != null) {
            val updated = existing.copy(
                role = role,
                department = if (role == UserRole.ADMIN && existing.department == "Campus Circulation") {
                    "Library Administration"
                } else if (role == UserRole.STUDENT && existing.department == "Library Administration") {
                    "Campus Circulation"
                } else {
                    existing.department
                }
            )
            userDao.updateUser(updated)
            val remote = firestoreDataSource ?: return
            runCatchingNonCancel {
                remote.setLibrarianMode(uid = uid, isAdmin = role == UserRole.ADMIN)
                remote.upsertMemberProfile(user = updated, ownerUid = uid)
            }
        } else {
            val remote = firestoreDataSource ?: return
            runCatchingNonCancel {
                remote.setLibrarianMode(uid = uid, isAdmin = role == UserRole.ADMIN)
            }
        }
    }

    suspend fun seedInitialDataIfNeeded() {
        val existingBooks = allBooks.first()
        if (existingBooks.isNotEmpty()) return

        val now = System.currentTimeMillis()
        userDao.insertUsers(buildStarterUsers())
        bookDao.insertBooks(buildStarterBooks())
        borrowRecordDao.insertRecords(buildStarterBorrowRecords(now, "student_01", "Alex Rivera"))
        reservationDao.insertReservations(buildStarterReservations(now, "student_01", "Alex Rivera"))
        fineDao.insertFines(buildStarterFines("student_01", "Alex Rivera", "LIB-2023-042"))
        notificationDao.insertNotifications(buildStarterNotifications(now, "student_01"))
    }

    // CRUD Operations (Dual-write to Room cache + Cloud Firestore when authenticated)
    suspend fun addBook(book: Book) {
        bookDao.insertBook(book)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.createBook(book, createdByUid = uid) }
        }
    }

    suspend fun updateBook(book: Book) {
        bookDao.updateBook(book)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel {
                val existingRemote = remote.getBookById(book.bookId)
                if (existingRemote != null) {
                    remote.updateBook(book)
                } else {
                    remote.createBook(book, createdByUid = uid)
                }
            }
        }
    }

    suspend fun deleteBook(bookId: String) {
        bookDao.deleteBookById(bookId)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.deleteBook(bookId) }
        }
    }

    suspend fun getBookByIsbn(isbn: String): Book? {
        return bookDao.getBookByIsbn(isbn)
    }

    fun getBooksByAvailabilityStatus(status: String): Flow<List<Book>> {
        return bookDao.getBooksByAvailabilityStatus(status)
    }

    fun getAvailableBooks(): Flow<List<Book>> {
        return bookDao.getAvailableBooks()
    }

    fun searchBooks(query: String): Flow<List<Book>> {
        return bookDao.searchBooks(query)
    }

    suspend fun updateBookAvailability(bookId: String, status: String) {
        bookDao.updateAvailabilityStatus(bookId, status)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            val updated = bookDao.getBookById(bookId) ?: return
            runCatchingNonCancel { remote.updateBook(updated) }
        }
    }

    suspend fun updateBookStock(bookId: String, availableCopies: Int, totalCopies: Int, status: String) {
        bookDao.updateInventoryStock(bookId, availableCopies, totalCopies, status)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            val updated = bookDao.getBookById(bookId) ?: return
            runCatchingNonCancel { remote.updateBook(updated) }
        }
    }

    suspend fun addUser(user: User) {
        userDao.insertUser(user)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.upsertMemberProfile(user, ownerUid = uid) }
        }
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.upsertMemberProfile(user, ownerUid = uid) }
        }
    }

    suspend fun deleteUser(user: User) {
        userDao.deleteUser(user)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.deleteMemberProfile(user.userId) }
        }
    }

    suspend fun borrowBook(userId: String, userName: String, bookId: String, durationDays: Int = 14): Boolean {
        val book = bookDao.getBookById(bookId) ?: return false
        if (book.availableCopies <= 0) return false

        val newAvailable = book.availableCopies - 1
        val newStatus = if (newAvailable == 0) "Borrowed" else "Available"
        val updatedBook = book.copy(
            availableCopies = newAvailable,
            status = newStatus,
            availabilityStatus = newStatus
        )
        bookDao.updateBook(updatedBook)

        val oneDayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val borrow = BorrowRecord(
            borrowId = "borrow_$now",
            userId = userId,
            userName = userName,
            bookId = book.bookId,
            bookTitle = book.title,
            bookAuthor = book.author,
            borrowDate = now,
            dueDate = now + (durationDays.toLong() * oneDayMs),
            status = "Borrowed"
        )
        borrowRecordDao.insertRecord(borrow)

        val notification = NotificationItem(
            notificationId = "notif_$now",
            userId = userId,
            title = "Borrow Successful",
            message = "You have borrowed \"${book.title}\". Due date: $durationDays days from today.",
            type = "APPROVAL"
        )
        notificationDao.insertNotification(notification)

        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel {
                remote.adjustBookStock(book.bookId, newAvailable, newStatus)
                remote.createLoan(borrow, ownerUid = uid)
                remote.createNotification(notification, ownerUid = uid)
            }
        }
        return true
    }

    suspend fun returnBook(borrowId: String, finePerDay: Double = 5.0): Boolean {
        val record = borrowRecordDao.getRecordById(borrowId) ?: return false
        if (record.status == "Returned") return false

        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60 * 60 * 1000
        val overdueDays = if (now > record.dueDate) {
            ((now - record.dueDate) / oneDayMs).toInt().coerceAtLeast(1)
        } else 0

        val fineAmount = overdueDays * finePerDay

        val updatedRecord = record.copy(
            returnDate = now,
            status = "Returned",
            fineAmount = fineAmount
        )
        borrowRecordDao.updateRecord(updatedRecord)

        val book = bookDao.getBookById(record.bookId)
        var incrementedBookCopies: Int? = null
        if (book != null) {
            val newAvailable = (book.availableCopies + 1).coerceAtMost(book.totalCopies)
            bookDao.updateBook(
                book.copy(
                    availableCopies = newAvailable,
                    status = "Available",
                    availabilityStatus = "Available"
                )
            )
            if (newAvailable == book.availableCopies + 1) {
                incrementedBookCopies = newAvailable
            }
        }

        var createdFine: Fine? = null
        val notification: NotificationItem
        if (overdueDays > 0) {
            val fine = Fine(
                fineId = "fine_$now",
                userId = record.userId,
                userName = record.userName,
                borrowId = record.borrowId,
                bookTitle = record.bookTitle,
                overdueDays = overdueDays,
                amount = fineAmount,
                status = "Unpaid"
            )
            fineDao.insertFine(fine)
            createdFine = fine

            notification = NotificationItem(
                notificationId = "notif_$now",
                userId = record.userId,
                title = "Overdue Return Fine",
                message = "Book \"${record.bookTitle}\" returned $overdueDays days late. Fine incurred: \$$fineAmount.",
                type = "OVERDUE"
            )
            notificationDao.insertNotification(notification)
        } else {
            notification = NotificationItem(
                notificationId = "notif_$now",
                userId = record.userId,
                title = "Book Returned",
                message = "\"${record.bookTitle}\" returned successfully. Thank you!",
                type = "INFO"
            )
            notificationDao.insertNotification(notification)
        }

        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel {
                remote.updateLoanReturn(record.borrowId, now, fineAmount)
                if (book != null && incrementedBookCopies != null) {
                    remote.adjustBookStock(book.bookId, incrementedBookCopies, "Available")
                }
                createdFine?.let { remote.createFine(it, ownerUid = uid) }
                remote.createNotification(notification, ownerUid = uid)
            }
        }
        return true
    }

    suspend fun renewBook(borrowId: String, extensionDays: Int = 14): Boolean {
        val record = borrowRecordDao.getRecordById(borrowId) ?: return false
        val oneDayMs = 24L * 60 * 60 * 1000
        val newDueDate = record.dueDate + (extensionDays.toLong() * oneDayMs)
        borrowRecordDao.updateRecord(record.copy(dueDate = newDueDate))

        val notification = NotificationItem(
            notificationId = "notif_" + System.currentTimeMillis(),
            userId = record.userId,
            title = "Loan Extended",
            message = "\"${record.bookTitle}\" renewed for $extensionDays more days.",
            type = "INFO"
        )
        notificationDao.insertNotification(notification)

        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel {
                remote.updateLoanRenewal(borrowId, newDueDate)
                remote.createNotification(notification, ownerUid = uid)
            }
        }
        return true
    }

    suspend fun reserveBook(userId: String, userName: String, bookId: String): Boolean {
        val book = bookDao.getBookById(bookId) ?: return false
        val now = System.currentTimeMillis()
        val reservation = Reservation(
            reservationId = "res_$now",
            userId = userId,
            userName = userName,
            bookId = book.bookId,
            bookTitle = book.title,
            reservationDate = now,
            status = "Pending",
            pickupLocation = "Main Library, Desk 1"
        )
        reservationDao.insertReservation(reservation)

        val notification = NotificationItem(
            notificationId = "notif_$now",
            userId = userId,
            title = "Hold Placed",
            message = "You have reserved \"${book.title}\". We will notify you once ready.",
            type = "RESERVATION"
        )
        notificationDao.insertNotification(notification)

        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel {
                remote.createReservation(reservation, ownerUid = uid)
                remote.createNotification(notification, ownerUid = uid)
            }
        }
        return true
    }

    suspend fun cancelReservation(reservationId: String) {
        reservationDao.deleteReservationById(reservationId)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.deleteReservation(reservationId) }
        }
    }

    suspend fun payFine(fineId: String) {
        val fines = allFines.first()
        val fine = fines.find { it.fineId == fineId } ?: return
        fineDao.updateFine(fine.copy(status = "Paid"))
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.updateFineStatus(fineId, "Paid") }
        }
    }

    suspend fun waiveFine(fineId: String) {
        val fines = allFines.first()
        val fine = fines.find { it.fineId == fineId } ?: return
        fineDao.updateFine(fine.copy(status = "Waived"))
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.updateFineStatus(fineId, "Waived") }
        }
    }

    suspend fun sendOverdueReminder(userId: String, bookTitle: String) {
        val notification = NotificationItem(
            notificationId = "notif_" + System.currentTimeMillis(),
            userId = userId,
            title = "Urgent Due Reminder",
            message = "The book \"$bookTitle\" is overdue. Please return it to the library promptly to prevent extra charges.",
            type = "REMINDER"
        )
        notificationDao.insertNotification(notification)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            runCatchingNonCancel { remote.createNotification(notification, ownerUid = uid) }
        }
    }

    suspend fun markAllNotificationsRead(userId: String) {
        val unreadForUser = allNotifications.first().filter {
            !it.isRead && (it.userId == userId || it.userId == "ALL")
        }
        notificationDao.markAllAsRead(userId)
        val uid = activeAuthUid
        val remote = firestoreDataSource
        if (uid != null && remote != null) {
            unreadForUser.forEach { item ->
                runCatchingNonCancel { remote.markNotificationRead(item.notificationId) }
            }
        }
    }

    suspend fun insertUser(user: User) {
        addUser(user)
    }

    private suspend inline fun runCatchingNonCancel(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync write failed; local Room cache remains updated", e)
        }
    }

    private fun buildStarterUsers(): List<User> = listOf(
        User(
            userId = "admin_01",
            name = "Eleanor Vance",
            email = "admin@libease.edu",
            phone = "+1 (555) 234-5678",
            studentId = "LIB-ADM-001",
            department = "Administration / Library Services",
            role = UserRole.ADMIN,
            membershipStatus = "Active"
        ),
        User(
            userId = "student_01",
            name = "Alex Rivera",
            email = "alex.rivera@university.edu",
            phone = "+1 (555) 382-9102",
            studentId = "LIB-2023-042",
            department = "Computer Science - Senior",
            role = UserRole.STUDENT,
            membershipStatus = "Active",
            totalBorrowedCount = 14
        ),
        User(
            userId = "student_02",
            name = "Dr. Sarah Jenkins",
            email = "s.jenkins@university.edu",
            phone = "+1 (555) 829-4103",
            studentId = "LIB-2021-089",
            department = "Faculty of Engineering",
            role = UserRole.STUDENT,
            membershipStatus = "Active",
            totalBorrowedCount = 5
        ),
        User(
            userId = "student_03",
            name = "Liam Vance",
            email = "liam.vance@university.edu",
            phone = "+1 (555) 714-2291",
            studentId = "LIB-2022-114",
            department = "Biology - Sophomore",
            role = UserRole.STUDENT,
            membershipStatus = "Inactive",
            totalBorrowedCount = 0
        ),
        User(
            userId = "student_04",
            name = "Jessica Sterling",
            email = "jessica.s@university.edu",
            phone = "+1 (555) 619-3382",
            studentId = "LIB-2022-108",
            department = "Design Systems - Junior",
            role = UserRole.STUDENT,
            membershipStatus = "Active",
            totalBorrowedCount = 3
        ),
        User(
            userId = "student_05",
            name = "David Miller",
            email = "david.m@university.edu",
            phone = "+1 (555) 492-7715",
            studentId = "LIB-2023-891",
            department = "Software Engineering - Senior",
            role = UserRole.STUDENT,
            membershipStatus = "Active",
            totalBorrowedCount = 8
        )
    )

    private fun buildStarterBooks(): List<Book> = listOf(
        Book(
            bookId = "book_01",
            title = "Designing Data-Intensive Applications",
            author = "Martin Kleppmann",
            isbn = "978-1449373328",
            category = "Programming",
            publisher = "O'Reilly Media",
            publicationYear = 2017,
            description = "Data is at the center of many challenges in system design today. Difficulties in scaling, data consistency, and maintainability are common. By weighing the pros and cons of various technologies, this book helps you navigate the myriad design choices for data architecture, distributed systems, and modern web applications.",
            totalCopies = 5,
            availableCopies = 3,
            shelfNumber = "Shelf B-12",
            rating = 4.9f,
            status = "Available",
            coverGradientIndex = 0
        ),
        Book(
            bookId = "book_02",
            title = "Clean Code: A Handbook of Agile Software",
            author = "Robert C. Martin",
            isbn = "978-0132350884",
            category = "Programming",
            publisher = "Prentice Hall",
            publicationYear = 2008,
            description = "Even bad code can function. But if code isn't clean, it can bring a development organization to its knees. Every year, countless hours and significant resources are lost because of poorly written code. But it doesn't have to be that way.",
            totalCopies = 4,
            availableCopies = 2,
            shelfNumber = "Shelf A-4",
            rating = 4.8f,
            status = "Available",
            coverGradientIndex = 1
        ),
        Book(
            bookId = "book_03",
            title = "The Pragmatic Programmer",
            author = "Andy Hunt & Dave Thomas",
            isbn = "978-0135957059",
            category = "Programming",
            publisher = "Addison-Wesley",
            publicationYear = 2019,
            description = "The Pragmatic Programmer cuts through the increasing specialization and technicalities of modern software development to examine the core process—taking a requirement and producing working, maintainable code.",
            totalCopies = 4,
            availableCopies = 4,
            shelfNumber = "Shelf B-05",
            rating = 4.9f,
            status = "Available",
            coverGradientIndex = 2
        ),
        Book(
            bookId = "book_04",
            title = "The Midnight Library",
            author = "Matt Haig",
            isbn = "978-0525559473",
            category = "Fiction",
            publisher = "Viking",
            publicationYear = 2020,
            description = "Between life and death there is a library, and within that library, the shelves go on forever. Every book provides a chance to try another life you could have lived. To see how things would be if you had made other choices.",
            totalCopies = 2,
            availableCopies = 0,
            shelfNumber = "Shelf C-01",
            rating = 4.7f,
            status = "Borrowed",
            availabilityStatus = "Borrowed",
            coverGradientIndex = 3
        ),
        Book(
            bookId = "book_05",
            title = "A Brief History of Time",
            author = "Stephen Hawking",
            isbn = "978-0553380163",
            category = "Science",
            publisher = "Bantam",
            publicationYear = 1998,
            description = "A landmark volume in science writing by one of the great minds of our time, exploring profound questions about the universe, black holes, time travel, and the origins of space.",
            totalCopies = 4,
            availableCopies = 4,
            shelfNumber = "Shelf D-10",
            rating = 4.8f,
            status = "Available",
            coverGradientIndex = 4
        ),
        Book(
            bookId = "book_06",
            title = "Zero to One",
            author = "Peter Thiel",
            isbn = "978-0804139298",
            category = "Business",
            publisher = "Crown Business",
            publicationYear = 2014,
            description = "Notes on startups, or how to build the future. The great secret of our time is that there are still uncharted frontiers to explore and new inventions to create.",
            totalCopies = 3,
            availableCopies = 1,
            shelfNumber = "Shelf E-03",
            rating = 4.6f,
            status = "Available",
            coverGradientIndex = 5
        ),
        Book(
            bookId = "book_07",
            title = "Artificial Intelligence: A Modern Approach",
            author = "Stuart Russell & Peter Norvig",
            isbn = "978-0134610993",
            category = "AI & Data",
            publisher = "Pearson",
            publicationYear = 2020,
            description = "The authoritative, most widely adopted introduction to the theory and practice of artificial intelligence, machine learning, robotics, and natural language understanding.",
            totalCopies = 3,
            availableCopies = 2,
            shelfNumber = "Shelf F-08",
            rating = 4.9f,
            status = "Available",
            coverGradientIndex = 6
        ),
        Book(
            bookId = "book_08",
            title = "Deep Learning",
            author = "Ian Goodfellow & Yoshua Bengio",
            isbn = "978-0262035613",
            category = "AI & Data",
            publisher = "MIT Press",
            publicationYear = 2016,
            description = "An introduction to a broad range of topics in deep learning, covering mathematical and conceptual background, deep learning techniques used in industry, and research perspectives.",
            totalCopies = 3,
            availableCopies = 3,
            shelfNumber = "Shelf F-14",
            rating = 4.8f,
            status = "Available",
            coverGradientIndex = 7
        )
    )

    private fun buildStarterBorrowRecords(
        now: Long,
        primaryUserId: String,
        primaryUserName: String
    ): List<BorrowRecord> {
        val oneDayMs = 24L * 60 * 60 * 1000
        return listOf(
            BorrowRecord(
                borrowId = "borrow_01",
                userId = primaryUserId,
                userName = primaryUserName,
                bookId = "book_01",
                bookTitle = "Designing Data-Intensive Applications",
                bookAuthor = "Martin Kleppmann",
                borrowDate = now - (10L * oneDayMs),
                dueDate = now + (2L * oneDayMs),
                status = "Borrowed"
            ),
            BorrowRecord(
                borrowId = "borrow_02",
                userId = primaryUserId,
                userName = primaryUserName,
                bookId = "book_03",
                bookTitle = "The Pragmatic Programmer",
                bookAuthor = "Andy Hunt & Dave Thomas",
                borrowDate = now - (2L * oneDayMs),
                dueDate = now + (12L * oneDayMs),
                status = "Borrowed"
            ),
            BorrowRecord(
                borrowId = "borrow_03",
                userId = "student_02",
                userName = "Dr. Sarah Jenkins",
                bookId = "book_03",
                bookTitle = "The Pragmatic Programmer",
                bookAuthor = "Andy Hunt & Dave Thomas",
                borrowDate = now - (18L * oneDayMs),
                dueDate = now - (4L * oneDayMs),
                status = "Overdue",
                fineAmount = 20.0
            ),
            BorrowRecord(
                borrowId = "borrow_04",
                userId = "student_05",
                userName = "David Miller",
                bookId = "book_02",
                bookTitle = "Clean Code: A Handbook of Agile Software",
                bookAuthor = "Robert C. Martin",
                borrowDate = now - (26L * oneDayMs),
                dueDate = now - (12L * oneDayMs),
                status = "Overdue",
                fineAmount = 24.0
            ),
            BorrowRecord(
                borrowId = "borrow_05",
                userId = primaryUserId,
                userName = primaryUserName,
                bookId = "book_07",
                bookTitle = "Artificial Intelligence: A Modern Approach",
                bookAuthor = "Stuart Russell & Peter Norvig",
                borrowDate = now - (35L * oneDayMs),
                dueDate = now - (21L * oneDayMs),
                returnDate = now - (22L * oneDayMs),
                status = "Returned"
            )
        )
    }

    private fun buildStarterReservations(
        now: Long,
        primaryUserId: String,
        primaryUserName: String
    ): List<Reservation> {
        val oneDayMs = 24L * 60 * 60 * 1000
        return listOf(
            Reservation(
                reservationId = "res_01",
                userId = primaryUserId,
                userName = primaryUserName,
                bookId = "book_02",
                bookTitle = "Clean Code: A Handbook of Agile Software",
                reservationDate = now - (1L * oneDayMs),
                status = "Ready for Pickup",
                pickupLocation = "Main Library, Desk 3"
            ),
            Reservation(
                reservationId = "res_02",
                userId = "student_02",
                userName = "Dr. Sarah Jenkins",
                bookId = "book_04",
                bookTitle = "The Midnight Library",
                reservationDate = now - (3L * oneDayMs),
                status = "Pending",
                pickupLocation = "Main Library, Hold Shelf 2"
            )
        )
    }

    private fun buildStarterFines(
        primaryUserId: String,
        primaryUserName: String,
        primaryStudentId: String
    ): List<Fine> = listOf(
        Fine(
            fineId = "fine_01",
            userId = primaryUserId,
            userName = primaryUserName,
            studentId = primaryStudentId,
            borrowId = "borrow_01",
            bookTitle = "Introduction to Algorithms",
            overdueDays = 3,
            amount = 12.50,
            status = "Unpaid"
        ),
        Fine(
            fineId = "fine_02",
            userId = "student_04",
            userName = "Jessica Sterling",
            studentId = "LIB-2022-108",
            borrowId = "borrow_06",
            bookTitle = "Design Systems Handbook",
            overdueDays = 1,
            amount = 5.00,
            status = "Paid"
        ),
        Fine(
            fineId = "fine_03",
            userId = "student_05",
            userName = "David Miller",
            studentId = "LIB-2023-891",
            borrowId = "borrow_04",
            bookTitle = "Clean Architecture: A Craftsman's Guide",
            overdueDays = 12,
            amount = 24.00,
            status = "Unpaid"
        )
    )

    private fun buildStarterNotifications(now: Long, primaryUserId: String): List<NotificationItem> {
        val oneDayMs = 24L * 60 * 60 * 1000
        return listOf(
            NotificationItem(
                notificationId = "notif_01",
                userId = primaryUserId,
                title = "Due Date Alert",
                message = "\"Designing Data-Intensive Applications\" is due in 2 days. Please renew or return on time.",
                type = "REMINDER",
                isRead = false,
                createdAt = now - (2L * 60 * 60 * 1000)
            ),
            NotificationItem(
                notificationId = "notif_02",
                userId = primaryUserId,
                title = "Reservation Ready",
                message = "\"Clean Code: A Handbook\" is now ready for pickup at Main Library, Desk 3.",
                type = "RESERVATION",
                isRead = false,
                createdAt = now - (14L * 60 * 60 * 1000)
            ),
            NotificationItem(
                notificationId = "notif_03",
                userId = primaryUserId,
                title = "Cloud Sync Enabled",
                message = "Your library catalog, loans, holds, and fines are now synced with Cloud Firestore.",
                type = "INFO",
                isRead = false,
                createdAt = now - (30L * 60 * 1000)
            ),
            NotificationItem(
                notificationId = "notif_04",
                userId = "ALL",
                title = "New Arrival Cataloged",
                message = "\"Artificial Intelligence: A Modern Approach (4th Ed)\" is now in stock!",
                type = "NEW_BOOK",
                isRead = true,
                createdAt = now - (3L * oneDayMs)
            )
        )
    }

    private companion object {
        const val TAG = "LibraryRepository"
    }
}
