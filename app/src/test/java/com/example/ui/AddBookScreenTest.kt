package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LibEaseDatabase
import com.example.data.repository.LibraryRepository
import com.example.ui.screens.AddBookScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddBookScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: LibEaseDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(app, LibEaseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(
            bookDao = database.bookDao(),
            userDao = database.userDao(),
            borrowRecordDao = database.borrowRecordDao(),
            reservationDao = database.reservationDao(),
            fineDao = database.fineDao(),
            notificationDao = database.notificationDao()
        )
        viewModel = LibraryViewModel(app, repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun enteringBookDetailsAndClickingSave_persistsBookToRoomDatabase() = runBlocking {
        var navigatedBack = false

        composeTestRule.setContent {
            MyApplicationTheme {
                AddBookScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigatedBack = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("book_title_input")
            .performTextInput("Designing Data-Intensive Applications")
        composeTestRule.onNodeWithTag("book_author_input")
            .performTextInput("Martin Kleppmann")
        composeTestRule.onNodeWithTag("book_isbn_input")
            .performTextInput("978-1449373320")

        composeTestRule.onNodeWithTag("save_book_button")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()

        assertTrue("Expected navigation callback after saving book", navigatedBack)

        val savedBook = database.bookDao().getBookByIsbn("978-1449373320")
        assertNotNull("Expected book to be saved in Room database", savedBook)
        assertEquals("Designing Data-Intensive Applications", savedBook?.title)
        assertEquals("Martin Kleppmann", savedBook?.author)
        assertEquals("978-1449373320", savedBook?.isbn)
        assertEquals("Available", savedBook?.availabilityStatus)
    }

    @Test
    fun clickingSaveWithBlankFields_showsValidationErrorsAndDoesNotInsert() = runBlocking {
        composeTestRule.setContent {
            MyApplicationTheme {
                AddBookScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_book_button")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("book_title_input").performScrollTo()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Please enter a book title", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Please enter the author name", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("book_isbn_input").performScrollTo()
        composeTestRule.onNodeWithText("Please enter a valid ISBN", useUnmergedTree = true)
            .assertIsDisplayed()

        val allBooks = database.bookDao().getAllBooks().first()
        assertTrue("Database should remain empty when validation fails", allBooks.isEmpty())
    }
}
