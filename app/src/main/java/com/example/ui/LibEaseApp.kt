package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.BookManagementScreen
import com.example.ui.admin.FineManagementScreen
import com.example.ui.admin.MemberManagementScreen
import com.example.ui.components.AddMemberDialog
import com.example.ui.components.AuthDialog
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.BookDetailsDialog
import com.example.ui.components.LibEaseBottomNav
import com.example.ui.components.LibEaseTopBar
import com.example.ui.components.MemberProfileDialog
import com.example.ui.components.NotificationsDialog
import com.example.ui.screens.AddBookScreen
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
    onSignOutComplete: (() -> Unit)? = null,
    onNavigateToAddBook: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentAuthUser by authViewModel.currentAuthUser.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allBorrowRecords by viewModel.allBorrowRecords.collectAsStateWithLifecycle()
    val allReservations by viewModel.allReservations.collectAsStateWithLifecycle()
    val allFines by viewModel.allFines.collectAsStateWithLifecycle()
    val allNotifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedNavTab by viewModel.selectedNavTab.collectAsStateWithLifecycle()
    val selectedMyBooksTab by viewModel.selectedMyBooksTab.collectAsStateWithLifecycle()

    val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()
    val showAddBookDialog by viewModel.showAddBookDialog.collectAsStateWithLifecycle()

    val selectedMember by viewModel.selectedMember.collectAsStateWithLifecycle()
    val showAddMemberDialog by viewModel.showAddMemberDialog.collectAsStateWithLifecycle()
    val showScannerDialog by viewModel.showScannerDialog.collectAsStateWithLifecycle()
    val showNotificationsScreen by viewModel.showNotificationsScreen.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showAuthDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val performSignOut: () -> Unit = {
        viewModel.clearAuthenticatedSession()
        authViewModel.signOut(context) {
            onSignOutComplete?.invoke()
        }
    }

    val openAddBookAction: () -> Unit = {
        if (onNavigateToAddBook != null) {
            viewModel.closeAddBookDialog()
            onNavigateToAddBook()
        } else {
            viewModel.openAddBookDialog()
        }
    }

    MyApplicationTheme(darkTheme = settings.isDarkMode) {
        val activeAuth = currentAuthUser
        if (activeAuth == null) {
            // Strict Auth Gate: Never compose feature screens or listeners when unauthenticated
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { signedInUser ->
                    viewModel.bindAuthenticatedUser(signedInUser, authUiState.selectedRole)
                }
            )
        } else {
            LaunchedEffect(activeAuth.uid) {
                viewModel.bindAuthenticatedUser(activeAuth, authUiState.selectedRole)
            }

            if (showAddBookDialog && onNavigateToAddBook == null) {
                BackHandler { viewModel.closeAddBookDialog() }
                AddBookScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.closeAddBookDialog() }
                )
            } else {
                if (selectedNavTab != "home") {
                    BackHandler { viewModel.selectNavTab("home") }
                }

                val unreadNotifsCount = allNotifications.count {
                    !it.isRead && (it.userId == currentUser?.userId || it.userId == "ALL")
                }
                val userActiveBorrow = allBorrowRecords.find {
                    it.userId == currentUser?.userId && it.status == "Borrowed"
                }
                val userReservations = allReservations.filter { it.userId == currentUser?.userId }
                val userBorrows = allBorrowRecords.filter { it.userId == currentUser?.userId }

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
                                    onAddBookClick = openAddBookAction,
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
                                    onAddBookClick = openAddBookAction
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
                                    authEmail = activeAuth.email,
                                    onRoleToggle = {
                                        viewModel.setRole(
                                            if (currentRole == UserRole.ADMIN) UserRole.STUDENT else UserRole.ADMIN
                                        )
                                    },
                                    onSwitchAccountClick = { viewModel.toggleAuthDialog(true) },
                                    onSignOutClick = performSignOut,
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
                                    onAddBookClick = openAddBookAction,
                                    onAddMemberClick = { viewModel.openAddMemberDialog() },
                                    onScanClick = { viewModel.toggleScanner(true) },
                                    onRemindMember = { userId, title -> viewModel.sendOverdueReminder(userId, title) },
                                    onProcessReturn = { borrowId -> viewModel.returnLoan(borrowId) }
                                )
                            }
                        } else {
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
                                    onAddBookClick = openAddBookAction
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
                                    authEmail = activeAuth.email,
                                    onRoleToggle = {
                                        viewModel.setRole(
                                            if (currentRole == UserRole.ADMIN) UserRole.STUDENT else UserRole.ADMIN
                                        )
                                    },
                                    onSwitchAccountClick = { viewModel.toggleAuthDialog(true) },
                                    onSignOutClick = performSignOut,
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
                                    onNavigateToAddBook?.invoke()
                                },
                                onDelete = { viewModel.deleteBook(book.bookId) }
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
                            val userNotifs = allNotifications.filter {
                                it.userId == currentUser?.userId || it.userId == "ALL"
                            }
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
                                onSignOut = performSignOut
                            )
                        }
                    }
                }
            }
        }
    }
}
