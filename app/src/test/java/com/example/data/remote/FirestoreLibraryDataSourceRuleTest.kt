package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.Reservation
import com.example.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreLibraryDataSourceRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveBook_authenticatedUser_emitsRealtimeBook() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreLibraryDataSource(firestore)
        val bookId = "book_${UUID.randomUUID().toString().take(8)}"

        val book = Book(
            bookId = bookId,
            title = "Effective Kotlin",
            author = "Marcin Moskala",
            isbn = "978-8395452802",
            category = "Programming",
            totalCopies = 4,
            availableCopies = 4,
            status = "Available",
            availabilityStatus = "Available"
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.createBook(book, aliceUid)
        }

        val books = withTimeout(FLOW_TIMEOUT_MS) {
            dataSource.observeBooks().first { list -> list.any { it.bookId == bookId } }
        }
        val fetched = books.first { it.bookId == bookId }
        assertEquals("Effective Kotlin", fetched.title)
        assertEquals(4, fetched.availableCopies)
    }

    @Test
    fun librarianRoleSwitchingAndCatalogUpdate_succeedsForOptedInLibrarian() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val bobUid = signInTestUser(BOB_EMAIL)
        val dataSource = FirestoreLibraryDataSource(firestore)
        val bookId = "book_${UUID.randomUUID().toString().take(8)}"

        // Alice creates a book
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.createBook(
                Book(
                    bookId = bookId,
                    title = "Initial Title",
                    author = "Initial Author",
                    isbn = "978-0001112223"
                ),
                aliceUid
            )
        }

        // Bob switches to Librarian mode in-app and edits catalog metadata
        signInTestUser(BOB_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.upsertUserAccount(bobUid, "Bob Librarian", BOB_EMAIL, "", UserRole.ADMIN)
            dataSource.setLibrarianMode(bobUid, isAdmin = true)
            dataSource.updateBook(
                Book(
                    bookId = bookId,
                    title = "Updated Edition Title",
                    author = "Initial Author",
                    isbn = "978-0001112223",
                    totalCopies = 5,
                    availableCopies = 5,
                    status = "Available",
                    availabilityStatus = "Available"
                )
            )
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) { dataSource.getBookById(bookId) }
        assertNotNull(updated)
        assertEquals("Updated Edition Title", updated?.title)
        assertEquals(5, updated?.totalCopies)
    }

    @Test
    fun loansReservationsAndFines_enforceCrossUserIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreLibraryDataSource(firestore)
        val bookId = "book_${UUID.randomUUID().toString().take(8)}"
        val loanId = "loan_${UUID.randomUUID().toString().take(8)}"
        val resId = "res_${UUID.randomUUID().toString().take(8)}"
        val fineId = "fine_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.createBook(
                Book(bookId = bookId, title = "Clean Architecture", author = "Robert Martin", isbn = "978-0134494166"),
                aliceUid
            )
            dataSource.createLoan(
                BorrowRecord(
                    borrowId = loanId,
                    userId = aliceUid,
                    userName = "Alice",
                    bookId = bookId,
                    bookTitle = "Clean Architecture",
                    bookAuthor = "Robert Martin"
                ),
                ownerUid = aliceUid
            )
            dataSource.createReservation(
                Reservation(
                    reservationId = resId,
                    userId = aliceUid,
                    userName = "Alice",
                    bookId = bookId,
                    bookTitle = "Clean Architecture"
                ),
                ownerUid = aliceUid
            )
            dataSource.createFine(
                Fine(
                    fineId = fineId,
                    userId = aliceUid,
                    userName = "Alice",
                    borrowId = loanId,
                    bookTitle = "Clean Architecture",
                    overdueDays = 3,
                    amount = 15.0,
                    status = "Unpaid"
                ),
                ownerUid = aliceUid
            )
        }

        val aliceLoans = withTimeout(FLOW_TIMEOUT_MS) {
            dataSource.observeLoans(aliceUid).first { it.any { r -> r.borrowId == loanId } }
        }
        assertTrue(aliceLoans.any { it.borrowId == loanId })

        // Switch to Bob: Bob attempting to observe Alice's loans must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                dataSource.observeLoans(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob queries Alice's loans")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
        }
    }

    @Test
    fun observeBooks_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val dataSource = FirestoreLibraryDataSource(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                dataSource.observeBooks().first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@libease.test"
        const val BOB_EMAIL = "bob@libease.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
