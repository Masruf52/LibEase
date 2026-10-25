package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.Book
import com.example.ui.viewmodel.LibraryViewModel

/**
 * Stateful Compose screen for capturing Book details (title, author, ISBN, and optional
 * catalog metadata) and persisting entries to the local Room database via [LibraryViewModel].
 */
@Composable
fun AddBookScreen(
    viewModel: LibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val editingBook by viewModel.editingBook.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val handleBack = {
        viewModel.closeAddBookDialog()
        onNavigateBack()
    }

    BackHandler(onBack = handleBack)

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    AddBookScreenContent(
        editingBook = editingBook,
        snackbarHostState = snackbarHostState,
        onNavigateBack = handleBack,
        onSaveBook = { title, author, isbn, availabilityStatus, category, publisher, year, copies, shelf, description ->
            viewModel.saveBook(
                title = title,
                author = author,
                isbn = isbn,
                category = category,
                publisher = publisher,
                year = year,
                totalCopies = copies,
                shelf = shelf,
                description = description,
                availabilityStatus = availabilityStatus
            )
            onNavigateBack()
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBookScreenContent(
    editingBook: Book?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onSaveBook: (
        title: String,
        author: String,
        isbn: String,
        availabilityStatus: String,
        category: String,
        publisher: String,
        year: Int,
        copies: Int,
        shelf: String,
        description: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable(editingBook?.bookId) { mutableStateOf(editingBook?.title ?: "") }
    var author by rememberSaveable(editingBook?.bookId) { mutableStateOf(editingBook?.author ?: "") }
    var isbn by rememberSaveable(editingBook?.bookId) { mutableStateOf(editingBook?.isbn ?: "") }
    var availabilityStatus by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.availabilityStatus ?: "Available")
    }

    val categories = remember {
        listOf("General", "Programming", "Fiction", "Science", "History", "Business", "AI & Data")
    }
    var selectedCategory by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.category ?: "General")
    }
    var publisher by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.publisher ?: "")
    }
    var yearText by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.publicationYear?.toString() ?: "2026")
    }
    var copiesText by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.totalCopies?.toString() ?: "1")
    }
    var shelf by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.shelfNumber ?: "Shelf A-1")
    }
    var description by rememberSaveable(editingBook?.bookId) {
        mutableStateOf(editingBook?.description ?: "")
    }

    var showOptionalDetails by rememberSaveable { mutableStateOf(editingBook != null) }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }

    val isTitleInvalid = attemptedSubmit && title.isBlank()
    val isAuthorInvalid = attemptedSubmit && author.isBlank()
    val isIsbnInvalid = attemptedSubmit && isbn.isBlank()

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val submitForm = {
        attemptedSubmit = true
        if (title.isNotBlank() && author.isNotBlank() && isbn.isNotBlank()) {
            focusManager.clearFocus()
            val parsedYear = yearText.toIntOrNull() ?: 2026
            val parsedCopies = (copiesText.toIntOrNull() ?: 1).coerceAtLeast(1)
            onSaveBook(
                title.trim(),
                author.trim(),
                isbn.trim(),
                availabilityStatus,
                selectedCategory,
                publisher.trim(),
                parsedYear,
                parsedCopies,
                shelf.trim().ifEmpty { "Shelf A-1" },
                description.trim()
            )
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_book_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                if (editingBook != null) R.string.edit_book_screen_title
                                else R.string.add_book_screen_title
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("navigate_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back_desc)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_book_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Required Bibliographic Details Card (Title, Author, ISBN)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.book_core_details_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.book_title_label)) },
                            placeholder = { Text(stringResource(R.string.book_title_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null
                                )
                            },
                            isError = isTitleInvalid,
                            supportingText = if (isTitleInvalid) {
                                { Text(stringResource(R.string.book_title_error)) }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_title_input")
                        )

                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text(stringResource(R.string.book_author_label)) },
                            placeholder = { Text(stringResource(R.string.book_author_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            },
                            isError = isAuthorInvalid,
                            supportingText = if (isAuthorInvalid) {
                                { Text(stringResource(R.string.book_author_error)) }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_author_input")
                        )

                        OutlinedTextField(
                            value = isbn,
                            onValueChange = { isbn = it },
                            label = { Text(stringResource(R.string.book_isbn_label)) },
                            placeholder = { Text(stringResource(R.string.book_isbn_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null
                                )
                            },
                            isError = isIsbnInvalid,
                            supportingText = if (isIsbnInvalid) {
                                { Text(stringResource(R.string.book_isbn_error)) }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitForm() }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_isbn_input")
                        )
                    }
                }

                // Availability Status Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.book_availability_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isAvailableSelected = availabilityStatus.equals("Available", ignoreCase = true)
                            FilterChip(
                                selected = isAvailableSelected,
                                onClick = { availabilityStatus = "Available" },
                                label = { Text(stringResource(R.string.book_status_available)) },
                                leadingIcon = if (isAvailableSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("availability_chip_available")
                            )

                            val isBorrowedSelected = availabilityStatus.equals("Borrowed", ignoreCase = true)
                            FilterChip(
                                selected = isBorrowedSelected,
                                onClick = { availabilityStatus = "Borrowed" },
                                label = { Text(stringResource(R.string.book_status_borrowed)) },
                                leadingIcon = if (isBorrowedSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("availability_chip_borrowed")
                            )
                        }
                    }
                }

                // Expandable Optional Catalog Metadata Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clickable { showOptionalDetails = !showOptionalDetails }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .testTag("toggle_optional_details_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.book_optional_details_toggle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (showOptionalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showOptionalDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.book_category_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    categories.forEach { cat ->
                                        val isSelected = selectedCategory == cat
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat) },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = copiesText,
                                        onValueChange = { copiesText = it },
                                        label = { Text(stringResource(R.string.book_copies_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("book_copies_input")
                                    )

                                    OutlinedTextField(
                                        value = shelf,
                                        onValueChange = { shelf = it },
                                        label = { Text(stringResource(R.string.book_shelf_label)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("book_shelf_input")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = publisher,
                                        onValueChange = { publisher = it },
                                        label = { Text(stringResource(R.string.book_publisher_label)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("book_publisher_input")
                                    )

                                    OutlinedTextField(
                                        value = yearText,
                                        onValueChange = { yearText = it },
                                        label = { Text(stringResource(R.string.book_year_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .testTag("book_year_input")
                                    )
                                }

                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = { Text(stringResource(R.string.book_description_label)) },
                                    minLines = 3,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("book_description_input")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Save Action Button
                Button(
                    onClick = submitForm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("save_book_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (editingBook != null) R.string.update_book_button
                            else R.string.save_book_button
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
