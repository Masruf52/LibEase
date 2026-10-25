package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE bookId = :id LIMIT 1")
    suspend fun getBookById(id: String): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun getBookByIsbn(isbn: String): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn")
    fun getBooksByIsbn(isbn: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE availabilityStatus = :status OR status = :status ORDER BY title ASC")
    fun getBooksByAvailabilityStatus(status: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE availableCopies > 0 AND (availabilityStatus = 'Available' OR status = 'Available') ORDER BY title ASC")
    fun getAvailableBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE availableCopies == 0 OR availabilityStatus = 'Borrowed' OR status = 'Borrowed' ORDER BY title ASC")
    fun getBorrowedBooks(): Flow<List<Book>>

    @Query("""
        SELECT * FROM books 
        WHERE title LIKE '%' || :query || '%' 
           OR author LIKE '%' || :query || '%' 
           OR isbn LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchBooks(query: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE author LIKE '%' || :author || '%' ORDER BY title ASC")
    fun getBooksByAuthor(author: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE category = :category ORDER BY title ASC")
    fun getBooksByCategory(category: String): Flow<List<Book>>

    @Query("SELECT COUNT(*) FROM books")
    fun getTotalBookCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE availableCopies > 0 AND (availabilityStatus = 'Available' OR status = 'Available')")
    fun getAvailableBookCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalCopies), 0) FROM books")
    fun getTotalInventoryCopies(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<Book>)

    @Update
    suspend fun updateBook(book: Book)

    @Query("UPDATE books SET availabilityStatus = :status, status = :status WHERE bookId = :bookId")
    suspend fun updateAvailabilityStatus(bookId: String, status: String)

    @Query("UPDATE books SET availableCopies = :availableCopies, totalCopies = :totalCopies, availabilityStatus = :status, status = :status WHERE bookId = :bookId")
    suspend fun updateInventoryStock(bookId: String, availableCopies: Int, totalCopies: Int, status: String)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("DELETE FROM books WHERE bookId = :id")
    suspend fun deleteBookById(id: String)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()
}
