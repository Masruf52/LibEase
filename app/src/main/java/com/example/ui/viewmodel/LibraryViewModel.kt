package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibEaseDatabase
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.NotificationItem
import com.example.data.model.Reservation
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.AuthUserInfo
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemSettings(
    val defaultBorrowDays: Int = 14,
    val finePerDay: Double = 5.0,
    val autoRenewal: Boolean = true,
    val emailReceipts: Boolean = true,
    val isDarkMode: Boolean = true,
    val fontScale: Float = 1.0f
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LibEaseDatabase.getDatabase(application)
    private val repository = LibraryRepository(
        bookDao = database.bookDao(),
        userDao = database.userDao(),
        borrowRecordDao = database.borrowRecordDao(),
        reservationDao = database.reservationDao(),
        fineDao = database.fineDao(),
        notificationDao = database.notificationDao()
    )

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    val allBooks: StateFlow<List<Book>> = repository.allBooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allUsers: StateFlow<List<User>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allBorrowRecords: StateFlow<List<BorrowRecord>> = repository.allBorrowRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReservations: StateFlow<List<Reservation>> = repository.allReservations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFines: StateFlow<List<Fine>> = repository.allFines.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allNotifications: StateFlow<List<NotificationItem>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // User & Session Management
    private val _currentRole = MutableStateFlow(UserRole.ADMIN)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _currentUserId = MutableStateFlow("admin_01")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    val currentUser: StateFlow<User?> = combine(allUsers, _currentUserId) { users, id ->
        users.find { it.userId == id } ?: users.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedNavTab = MutableStateFlow("home")
    val selectedNavTab: StateFlow<String> = _selectedNavTab.asStateFlow()

    private val _selectedMyBooksTab = MutableStateFlow("borrowed")
    val selectedMyBooksTab: StateFlow<String> = _selectedMyBooksTab.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    // Dialog & Screen inspection states
    private val _selectedBook = MutableStateFlow<Book?>(null)
    val selectedBook: StateFlow<Book?> = _selectedBook.asStateFlow()

    private val _showAddBookDialog = MutableStateFlow(false)
    val showAddBookDialog: StateFlow<Boolean> = _showAddBookDialog.asStateFlow()

    private val _editingBook = MutableStateFlow<Book?>(null)
    val editingBook: StateFlow<Book?> = _editingBook.asStateFlow()

    private val _selectedMember = MutableStateFlow<User?>(null)
    val selectedMember: StateFlow<User?> = _selectedMember.asStateFlow()

    private val _showAddMemberDialog = MutableStateFlow(false)
    val showAddMemberDialog: StateFlow<Boolean> = _showAddMemberDialog.asStateFlow()

    private val _showScannerDialog = MutableStateFlow(false)
    val showScannerDialog: StateFlow<Boolean> = _showScannerDialog.asStateFlow()

    private val _showNotificationsScreen = MutableStateFlow(false)
    val showNotificationsScreen: StateFlow<Boolean> = _showNotificationsScreen.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    // Toast / Feedback message
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    // Actions
    fun setRole(role: UserRole) {
        _currentRole.value = role
        if (role == UserRole.ADMIN) {
            _currentUserId.value = "admin_01"
            showFeedback("Switched to Admin / Librarian view")
        } else {
            _currentUserId.value = "student_01"
            showFeedback("Switched to Student / Member view (Alex Rivera)")
        }
    }

    fun selectUser(userId: String) {
        _currentUserId.value = userId
        val user = allUsers.value.find { it.userId == userId }
        if (user != null) {
            _currentRole.value = user.role
            showFeedback("Logged in as ${user.name}")
        }
    }

    fun handleAuthenticatedUser(authUser: AuthUserInfo, preferredRole: UserRole = UserRole.STUDENT) {
        val email = authUser.email ?: ""
        val existing = allUsers.value.find { it.email.equals(email, ignoreCase = true) }
        if (existing != null) {
            selectUser(existing.userId)
        } else {
            viewModelScope.launch {
                val newId = "usr_${System.currentTimeMillis().toString().takeLast(6)}"
                val displayName = authUser.displayName?.ifBlank { null }
                    ?: if (email.contains("@")) email.substringBefore("@") else "Member"
                val newUser = User(
                    userId = newId,
                    name = displayName,
                    email = email.ifBlank { "user_$newId@libease.edu" },
                    role = preferredRole,
                    department = if (preferredRole == UserRole.ADMIN) "Library Staff" else "General Circulation",
                    studentId = if (preferredRole == UserRole.ADMIN) "LIB-STAFF-${(100..999).random()}" else "LIB-STU-${(1000..9999).random()}",
                    membershipStatus = "Active"
                )
                repository.insertUser(newUser)
                selectUser(newUser.userId)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun selectNavTab(tab: String) {
        _selectedNavTab.value = tab
    }

    fun selectMyBooksTab(tab: String) {
        _selectedMyBooksTab.value = tab
    }

    fun openBookDetails(book: Book) {
        _selectedBook.value = book
    }

    fun closeBookDetails() {
        _selectedBook.value = null
    }

    fun openAddBookDialog() {
        _editingBook.value = null
        _showAddBookDialog.value = true
    }

    fun openEditBookDialog(book: Book) {
        _editingBook.value = book
        _showAddBookDialog.value = true
    }

    fun closeAddBookDialog() {
        _showAddBookDialog.value = false
        _editingBook.value = null
    }

    fun openMemberProfile(user: User) {
        _selectedMember.value = user
    }

    fun closeMemberProfile() {
        _selectedMember.value = null
    }

    fun openAddMemberDialog() {
        _showAddMemberDialog.value = true
    }

    fun closeAddMemberDialog() {
        _showAddMemberDialog.value = false
    }

    fun toggleScanner(show: Boolean) {
        _showScannerDialog.value = show
    }

    fun toggleNotifications(show: Boolean) {
        _showNotificationsScreen.value = show
    }

    fun toggleAuthDialog(show: Boolean) {
        _showAuthDialog.value = show
    }

    fun toggleDarkMode() {
        _settings.value = _settings.value.copy(isDarkMode = !_settings.value.isDarkMode)
    }

    fun updateBorrowDuration(days: Int) {
        _settings.value = _settings.value.copy(defaultBorrowDays = days)
        showFeedback("Default borrow duration set to $days days")
    }

    fun toggleAutoRenewal() {
        val newVal = !_settings.value.autoRenewal
        _settings.value = _settings.value.copy(autoRenewal = newVal)
        showFeedback("Auto-renewal ${if (newVal) "enabled" else "disabled"}")
    }

    fun toggleEmailReceipts() {
        val newVal = !_settings.value.emailReceipts
        _settings.value = _settings.value.copy(emailReceipts = newVal)
        showFeedback("Email receipts ${if (newVal) "enabled" else "disabled"}")
    }

    fun setFontScale(scale: Float) {
        _settings.value = _settings.value.copy(fontScale = scale)
    }

    // Business Logic Calls
    fun borrowCurrentBook(bookId: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val success = repository.borrowBook(
                userId = user.userId,
                userName = user.name,
                bookId = bookId,
                durationDays = _settings.value.defaultBorrowDays
            )
            if (success) {
                showFeedback("Book checked out successfully! Enjoy your reading.")
                _selectedBook.value = null
            } else {
                showFeedback("No copies currently available. You can place a hold.")
            }
        }
    }

    fun reserveCurrentBook(bookId: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val success = repository.reserveBook(
                userId = user.userId,
                userName = user.name,
                bookId = bookId
            )
            if (success) {
                showFeedback("Reservation placed! We will alert you when it arrives.")
                _selectedBook.value = null
            }
        }
    }

    fun renewLoan(borrowId: String) {
        viewModelScope.launch {
            val success = repository.renewBook(borrowId, _settings.value.defaultBorrowDays)
            if (success) {
                showFeedback("Loan successfully extended by ${_settings.value.defaultBorrowDays} days!")
            }
        }
    }

    fun returnLoan(borrowId: String) {
        viewModelScope.launch {
            val success = repository.returnBook(borrowId, _settings.value.finePerDay)
            if (success) {
                showFeedback("Book returned and catalog inventory updated!")
            }
        }
    }

    fun cancelHold(reservationId: String) {
        viewModelScope.launch {
            repository.cancelReservation(reservationId)
            showFeedback("Reservation hold cancelled.")
        }
    }

    fun payFine(fineId: String) {
        viewModelScope.launch {
            repository.payFine(fineId)
            showFeedback("Fine payment recorded. Balance cleared!")
        }
    }

    fun waiveFine(fineId: String) {
        viewModelScope.launch {
            repository.waiveFine(fineId)
            showFeedback("Fine waived by librarian.")
        }
    }

    fun sendOverdueReminder(userId: String, bookTitle: String) {
        viewModelScope.launch {
            repository.sendOverdueReminder(userId, bookTitle)
            showFeedback("Overdue reminder notification dispatched.")
        }
    }

    fun saveBook(
        title: String,
        author: String,
        isbn: String,
        category: String,
        publisher: String,
        year: Int,
        totalCopies: Int,
        shelf: String,
        description: String
    ) {
        viewModelScope.launch {
            val editing = _editingBook.value
            if (editing != null) {
                val newAvailable = (editing.availableCopies + (totalCopies - editing.totalCopies)).coerceAtLeast(0)
                val newStatus = if (newAvailable > 0) "Available" else "Borrowed"
                val updated = editing.copy(
                    title = title,
                    author = author,
                    isbn = isbn,
                    category = category,
                    publisher = publisher,
                    publicationYear = year,
                    totalCopies = totalCopies,
                    availableCopies = newAvailable,
                    status = newStatus,
                    availabilityStatus = newStatus,
                    shelfNumber = shelf,
                    description = description
                )
                repository.updateBook(updated)
                showFeedback("Book \"$title\" updated successfully!")
            } else {
                val statusText = if (totalCopies > 0) "Available" else "Out of Stock"
                val newBook = Book(
                    bookId = "book_" + System.currentTimeMillis(),
                    title = title,
                    author = author,
                    isbn = isbn,
                    category = category,
                    publisher = publisher,
                    publicationYear = year,
                    totalCopies = totalCopies,
                    availableCopies = totalCopies,
                    status = statusText,
                    availabilityStatus = statusText,
                    shelfNumber = shelf,
                    description = description,
                    coverGradientIndex = (0..7).random()
                )
                repository.addBook(newBook)
                showFeedback("New book \"$title\" cataloged!")
            }
            closeAddBookDialog()
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            _selectedBook.value = null
            showFeedback("Book removed from catalog.")
        }
    }

    fun saveMember(
        name: String,
        email: String,
        phone: String,
        studentId: String,
        department: String,
        role: UserRole
    ) {
        viewModelScope.launch {
            val newUser = User(
                userId = "user_" + System.currentTimeMillis(),
                name = name,
                email = email,
                phone = phone,
                studentId = studentId,
                department = department,
                role = role,
                membershipStatus = "Active"
            )
            repository.addUser(newUser)
            closeAddMemberDialog()
            showFeedback("Member account for $name registered!")
        }
    }

    fun deleteMember(user: User) {
        viewModelScope.launch {
            repository.deleteUser(user)
            _selectedMember.value = null
            showFeedback("Member account removed.")
        }
    }

    fun suspendMember(user: User) {
        viewModelScope.launch {
            val updated = user.copy(
                membershipStatus = if (user.membershipStatus == "Active") "Inactive" else "Active"
            )
            repository.updateUser(updated)
            _selectedMember.value = updated
            showFeedback("Account status updated to ${updated.membershipStatus}.")
        }
    }

    fun markAllNotificationsAsRead() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(user.userId)
            showFeedback("All notifications marked as read.")
        }
    }

    fun handleBarcodeScan(scannedCode: String) {
        viewModelScope.launch {
            // Check if matches ISBN or title
            val book = allBooks.value.find {
                it.isbn.contains(scannedCode, ignoreCase = true) ||
                it.bookId.contains(scannedCode, ignoreCase = true) ||
                it.title.contains(scannedCode, ignoreCase = true)
            }
            if (book != null) {
                _selectedBook.value = book
                _showScannerDialog.value = false
                showFeedback("Scanned book: ${book.title}")
                return@launch
            }

            // Check if matches Member ID
            val member = allUsers.value.find {
                it.studentId.contains(scannedCode, ignoreCase = true) ||
                it.userId.contains(scannedCode, ignoreCase = true)
            }
            if (member != null) {
                _selectedMember.value = member
                _showScannerDialog.value = false
                showFeedback("Scanned member: ${member.name}")
                return@launch
            }

            showFeedback("No item or member found matching barcode \"$scannedCode\".")
        }
    }

    private fun showFeedback(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }
}
