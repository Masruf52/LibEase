package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BookDao
import com.example.data.local.LibEaseDatabase
import com.example.data.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookRoomDatabaseTest {

    private lateinit var database: LibEaseDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibEaseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = database.bookDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveBookByIsbnAndId() = runBlocking {
        val book = Book(
            bookId = "book_test_1",
            title = "Clean Code",
            author = "Robert C. Martin",
            isbn = "978-0132350884",
            category = "Software Engineering",
            totalCopies = 3,
            availableCopies = 3,
            availabilityStatus = "Available"
        )

        bookDao.insertBook(book)

        // Retrieve by primary key ID
        val retrievedById = bookDao.getBookById("book_test_1")
        assertNotNull(retrievedById)
        assertEquals("Clean Code", retrievedById?.title)
        assertEquals("Robert C. Martin", retrievedById?.author)
        assertEquals("978-0132350884", retrievedById?.isbn)
        assertEquals("Available", retrievedById?.availabilityStatus)
        assertTrue(retrievedById?.isAvailable == true)

        // Retrieve by ISBN
        val retrievedByIsbn = bookDao.getBookByIsbn("978-0132350884")
        assertNotNull(retrievedByIsbn)
        assertEquals("book_test_1", retrievedByIsbn?.bookId)
        assertEquals("Clean Code", retrievedByIsbn?.title)
    }

    @Test
    fun queryBooksByAvailabilityStatus() = runBlocking {
        val availableBook = Book(
            bookId = "book_avail",
            title = "Kotlin in Action",
            author = "Dmitry Jemerov",
            isbn = "978-1617293290",
            totalCopies = 2,
            availableCopies = 2,
            availabilityStatus = "Available"
        )

        val borrowedBook = Book(
            bookId = "book_borrowed",
            title = "Refactoring",
            author = "Martin Fowler",
            isbn = "978-0134757599",
            totalCopies = 1,
            availableCopies = 0,
            availabilityStatus = "Borrowed"
        )

        bookDao.insertBooks(listOf(availableBook, borrowedBook))

        val availableList = bookDao.getAvailableBooks().first()
        assertEquals(1, availableList.size)
        assertEquals("book_avail", availableList[0].bookId)
        assertTrue(availableList[0].isAvailable)

        val borrowedList = bookDao.getBorrowedBooks().first()
        assertEquals(1, borrowedList.size)
        assertEquals("book_borrowed", borrowedList[0].bookId)
        assertFalse(borrowedList[0].isAvailable)
    }

    @Test
    fun updateBookAvailabilityAndInventory() = runBlocking {
        val book = Book(
            bookId = "book_inv_1",
            title = "Effective Java",
            author = "Joshua Bloch",
            isbn = "978-0134685991",
            totalCopies = 5,
            availableCopies = 5,
            availabilityStatus = "Available"
        )

        bookDao.insertBook(book)

        // Update availability status when checked out
        bookDao.updateAvailabilityStatus("book_inv_1", "Borrowed")
        val updatedStatusBook = bookDao.getBookById("book_inv_1")
        assertEquals("Borrowed", updatedStatusBook?.availabilityStatus)

        // Update inventory copies & status
        bookDao.updateInventoryStock("book_inv_1", availableCopies = 2, totalCopies = 5, status = "Available")
        val updatedStockBook = bookDao.getBookById("book_inv_1")
        assertEquals(2, updatedStockBook?.availableCopies)
        assertEquals(5, updatedStockBook?.totalCopies)
        assertEquals("Available", updatedStockBook?.availabilityStatus)
    }

    @Test
    fun deleteBookRemovesItemFromInventory() = runBlocking {
        val book = Book(
            bookId = "book_del_1",
            title = "The Pragmatic Programmer",
            author = "Andrew Hunt",
            isbn = "978-0201616224",
            availabilityStatus = "Available"
        )

        bookDao.insertBook(book)
        assertNotNull(bookDao.getBookById("book_del_1"))

        bookDao.deleteBookById("book_del_1")
        assertNull(bookDao.getBookById("book_del_1"))
    }
}
