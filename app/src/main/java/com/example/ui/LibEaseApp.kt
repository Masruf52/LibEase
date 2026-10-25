package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.BookManagementScreen
import com.example.ui.admin.FineManagementScreen
import com.example.ui.admin.MemberManagementScreen
import com.example.ui.components.AddEditBookDialog
import com.example.ui.components.AddMemberDialog
import com.example.ui.components.AuthDialog
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.BookDetailsDialog
import com.example.ui.components.LibEaseBottomNav
import com.example.ui.components.LibEaseTopBar
import com.example.ui.components.MemberProfileDialog
import com.example.ui.components.NotificationsDialog
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.student.MyBooksScreen
import com.example.ui.student.StudentHomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthenticationViewModel
import com.example.ui.viewmodel.LibraryViewModel

@Composable
fun LibEaseApp(
    viewModel: LibraryViewModel = viewModel(),
    authViewModel: AuthenticationViewModel = viewModel(),
    onNavigateToAuth: (() -> Unit)? = null
) {
    var showLoginScreen by remember { mutableStateOf(false) }
    val openLoginAction: () -> Unit = {
        if (onNavigateToAuth != null) {
            onNavigateToAuth()
        } else {
            showLoginScreen = true
        }
    }

    val currentUser by viewModel.currentUser.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val allBorrowRecords by viewModel.allBorrowRecords.collectAsState()
    val allReservations by viewModel.allReservations.collectAsState()
    val allFines by viewModel.allFines.collectAsState()
    val allNotifications by viewModel.allNotifications.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedNavTab by viewModel.selectedNavTab.collectAsState()
    val selectedMyBooksTab by viewModel.selectedMyBooksTab.collectAsState()

    val selectedBook by viewModel.selectedBook.collectAsState()
    val showAddBookDialog by viewModel.showAddBookDialog.collectAsState()
    val editingBook by viewModel.editingBook.collectAsState()

    val selectedMember by viewModel.selectedMember.collectAsState()
    val showAddMemberDialog by viewModel.showAddMemberDialog.collectAsState()
    val showScannerDialog by viewModel.showScannerDialog.collectAsState()
    val showNotificationsScreen by viewModel.showNotificationsScreen.collectAsState()
    val showAuthDialog by viewModel.showAuthDialog.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val unreadNotifsCount = allNotifications.count { !it.isRead && (it.userId == currentUser?.userId || it.userId == "ALL") }
    val userActiveBorrow = allBorrowRecords.find { it.userId == currentUser?.userId && it.status == "Borrowed" }
    val userReservations = allReservations.filter { it.userId == currentUser?.userId }
    val userBorrows = allBorrowRecords.filter { it.userId == currentUser?.userId }

    MyApplicationTheme(darkTheme = settings.isDarkMode) {
        if (showLoginScreen) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { authUser ->
                    viewModel.handleAuthenticatedUser(authUser, authViewModel.uiState.value.selectedRole)
                    showLoginScreen = false
                },
                onDismissOrContinueGuest = {
                    showLoginScreen = false
                }
            )
        } else {
            Scaffold(
                topBar = {
                LibEaseTopBar(
                    currentUser = currentUser,
                    currentRole = currentRole,
                    unreadNotificationsCount = unreadNotifsCount,
                    onRoleToggle = {
                        viewModel.setRole(
                            if (currentRole == UserRole.ADMIN) UserRole.STUDENT else UserRole.ADMIN
                        )
                    },
                    onNotificationsClick = { viewModel.toggleNotifications(true) },
                    onProfileClick = { viewModel.selectNavTab("settings") }
                )
            },
            bottomBar = {
                LibEaseBottomNav(
                    currentRole = currentRole,
                    selectedTab = selectedNavTab,
                    onTabSelected = { tabId ->
                        if (tabId == "scan") {
                            viewModel.toggleScanner(true)
                        } else {
                            viewModel.selectNavTab(tabId)
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentRole == UserRole.ADMIN) {
                    when (selectedNavTab) {
                        "home" -> AdminDashboardScreen(
                            currentUser = currentUser,
                            books = allBooks,
                            users = allUsers,
                            borrowRecords = allBorrowRecords,
                            fines = allFines,
                            onAddBookClick = { viewModel.openAddBookDialog() },
                            onAddMemberClick = { viewModel.openAddMemberDialog() },
                            onScanClick = { viewModel.toggleScanner(true) },
                            onRemindMember = { userId, title -> viewModel.sendOverdueReminder(userId, title) },
                            onProcessReturn = { borrowId -> viewModel.returnLoan(borrowId) }
                        )
                        "books" -> BookManagementScreen(
                            books = allBooks,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onCategoryChange = { viewModel.setSelectedCategory(it) },
                            onBookClick = { viewModel.openBookDetails(it) },
                            onAddBookClick = { viewModel.openAddBookDialog() }
                        )
                        "members" -> MemberManagementScreen(
                            users = allUsers,
                            borrowRecords = allBorrowRecords,
                            fines = allFines,
                            onMemberClick = { viewModel.openMemberProfile(it) },
                            onAddMemberClick = { viewModel.openAddMemberDialog() }
                        )
                        "fines" -> FineManagementScreen(
                            fines = allFines,
                            onPayFine = { viewModel.payFine(it) },
                            onWaiveFine = { viewModel.waiveFine(it) }
                        )
                        "settings" -> SettingsScreen(
                            currentUser = currentUser,
                            currentRole = currentRole,
                            settings = settings,
                            onRoleToggle = {
                                viewModel.setRole(
                                    if (currentRole == UserRole.ADMIN) UserRole.STUDENT else UserRole.ADMIN
                                )
                            },
                            onSwitchAccountClick = { viewModel.toggleAuthDialog(true) },
                            onOpenLoginScreen = openLoginAction,
                            onToggleDarkMode = { viewModel.toggleDarkMode() },
                            onUpdateBorrowDuration = { viewModel.updateBorrowDuration(it) },
                            onToggleAutoRenew = { viewModel.toggleAutoRenewal() },
                            onToggleEmailReceipts = { viewModel.toggleEmailReceipts() }
                        )
                        else -> AdminDashboardScreen(
                            currentUser = currentUser,
                            books = allBooks,
                            users = allUsers,
                            borrowRecords = allBorrowRecords,
                            fines = allFines,
                            onAddBookClick = { viewModel.openAddBookDialog() },
                            onAddMemberClick = { viewModel.openAddMemberDialog() },
                            onScanClick = { viewModel.toggleScanner(true) },
                            onRemindMember = { userId, title -> viewModel.sendOverdueReminder(userId, title) },
                            onProcessReturn = { borrowId -> viewModel.returnLoan(borrowId) }
                        )
                    }
                } else {
                    // Student Portal
                    when (selectedNavTab) {
                        "home" -> StudentHomeScreen(
                            currentUser = currentUser,
                            books = allBooks,
                            activeBorrow = userActiveBorrow,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onCategoryChange = { viewModel.setSelectedCategory(it) },
                            onBookClick = { viewModel.openBookDetails(it) },
                            onRenewLoan = { viewModel.renewLoan(it) },
                            onScanClick = { viewModel.toggleScanner(true) }
                        )
                        "books" -> BookManagementScreen(
                            books = allBooks,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onCategoryChange = { viewModel.setSelectedCategory(it) },
                            onBookClick = { viewModel.openBookDetails(it) },
                            onAddBookClick = { viewModel.openAddBookDialog() }
                        )
                        "my_books" -> MyBooksScreen(
                            currentTab = selectedMyBooksTab,
                            borrowRecords = userBorrows,
                            reservations = userReservations,
                            onTabSelected = { viewModel.selectMyBooksTab(it) },
                            onRenewLoan = { viewModel.renewLoan(it) },
                            onReturnLoan = { viewModel.returnLoan(it) },
                            onCancelHold = { viewModel.cancelHold(it) }
                        )
                        "settings" -> SettingsScreen(
                            currentUser = currentUser,
                            currentRole = currentRole,
                            settings = settings,
                            onRoleToggle = {
                                viewModel.setRole(
                                    if (currentRole == UserRole.ADMIN) UserRole.STUDENT else UserRole.ADMIN
                                )
                            },
                            onSwitchAccountClick = { viewModel.toggleAuthDialog(true) },
                            onOpenLoginScreen = openLoginAction,
                            onToggleDarkMode = { viewModel.toggleDarkMode() },
                            onUpdateBorrowDuration = { viewModel.updateBorrowDuration(it) },
                            onToggleAutoRenew = { viewModel.toggleAutoRenewal() },
                            onToggleEmailReceipts = { viewModel.toggleEmailReceipts() }
                        )
                        else -> StudentHomeScreen(
                            currentUser = currentUser,
                            books = allBooks,
                            activeBorrow = userActiveBorrow,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onCategoryChange = { viewModel.setSelectedCategory(it) },
                            onBookClick = { viewModel.openBookDetails(it) },
                            onRenewLoan = { viewModel.renewLoan(it) },
                            onScanClick = { viewModel.toggleScanner(true) }
                        )
                    }
                }

                // Modal Dialogs
                selectedBook?.let { book ->
                    BookDetailsDialog(
                        book = book,
                        currentRole = currentRole,
                        onDismiss = { viewModel.closeBookDetails() },
                        onBorrow = { viewModel.borrowCurrentBook(book.bookId) },
                        onReserve = { viewModel.reserveCurrentBook(book.bookId) },
                        onEdit = {
                            viewModel.closeBookDetails()
                            viewModel.openEditBookDialog(book)
                        },
                        onDelete = { viewModel.deleteBook(book.bookId) }
                    )
                }

                if (showAddBookDialog) {
                    AddEditBookDialog(
                        editingBook = editingBook,
                        onDismiss = { viewModel.closeAddBookDialog() },
                        onSave = { title, author, isbn, category, publisher, year, copies, shelf, desc ->
                            viewModel.saveBook(title, author, isbn, category, publisher, year, copies, shelf, desc)
                        }
                    )
                }

                selectedMember?.let { member ->
                    val memberRecords = allBorrowRecords.filter { it.userId == member.userId }
                    val memberFines = allFines.filter { it.userId == member.userId }
                    MemberProfileDialog(
                        user = member,
                        borrowRecords = memberRecords,
                        fines = memberFines,
                        onDismiss = { viewModel.closeMemberProfile() },
                        onToggleSuspend = { viewModel.suspendMember(member) },
                        onDelete = { viewModel.deleteMember(member) }
                    )
                }

                if (showAddMemberDialog) {
                    AddMemberDialog(
                        onDismiss = { viewModel.closeAddMemberDialog() },
                        onSave = { name, email, phone, studentId, dept, role ->
                            viewModel.saveMember(name, email, phone, studentId, dept, role)
                        }
                    )
                }

                if (showScannerDialog) {
                    BarcodeScannerDialog(
                        onDismiss = { viewModel.toggleScanner(false) },
                        onCodeScanned = { code -> viewModel.handleBarcodeScan(code) }
                    )
                }

                if (showNotificationsScreen) {
                    val userNotifs = allNotifications.filter { it.userId == currentUser?.userId || it.userId == "ALL" }
                    NotificationsDialog(
                        notifications = userNotifs,
                        onDismiss = { viewModel.toggleNotifications(false) },
                        onMarkAllAsRead = { viewModel.markAllNotificationsAsRead() }
                    )
                }

                if (showAuthDialog) {
                    AuthDialog(
                        users = allUsers,
                        currentUserId = currentUser?.userId ?: "",
                        onDismiss = { viewModel.toggleAuthDialog(false) },
                        onSelectUser = { userId -> viewModel.selectUser(userId) },
                        onOpenLoginScreen = {
                            viewModel.toggleAuthDialog(false)
                            openLoginAction()
                        }
                    )
                }
            }
        }
    }
}
}
