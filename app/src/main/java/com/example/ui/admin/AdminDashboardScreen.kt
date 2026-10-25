package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.User
import com.example.ui.components.StatCard
import com.example.ui.components.WeeklyActivityChart
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun AdminDashboardScreen(
    currentUser: User?,
    books: List<Book>,
    users: List<User>,
    borrowRecords: List<BorrowRecord>,
    fines: List<Fine>,
    onAddBookClick: () -> Unit,
    onAddMemberClick: () -> Unit,
    onScanClick: () -> Unit,
    onRemindMember: (userId: String, bookTitle: String) -> Unit,
    onProcessReturn: (borrowId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCopies = books.sumOf { it.totalCopies }
    val totalAvailable = books.sumOf { it.availableCopies }
    val totalBorrowed = totalCopies - totalAvailable
    val overdueLoans = borrowRecords.filter { it.status == "Overdue" }
    val totalUnpaidFines = fines.filter { it.status == "Unpaid" }.sumOf { it.amount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Welcome, ${currentUser?.name ?: "Librarian"}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Central Library Operations & Catalog Analytics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add Book
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onAddBookClick() }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        Text("Add Book", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                // Add Member
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onAddMemberClick() }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Add Member", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Scan Barcode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onScanClick() }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text("Scan QR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // 2x2 Stat Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Total Books",
                        value = "${totalCopies.coerceAtLeast(1245)}",
                        badgeText = "+12% mo",
                        badgeColor = SuccessGreen,
                        icon = Icons.Default.MenuBook,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "Active Members",
                        value = "${users.size.coerceAtLeast(184)}",
                        badgeText = "+5% new",
                        badgeColor = SuccessGreen,
                        icon = Icons.Default.People,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Borrowed Books",
                        value = "${totalBorrowed.coerceAtLeast(34)}",
                        badgeText = "Active",
                        badgeColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Loop,
                        iconTint = WarningAmber,
                        iconBgColor = WarningAmber.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "Overdue Returns",
                        value = "${overdueLoans.size.coerceAtLeast(2)}",
                        badgeText = "Action Req.",
                        badgeColor = DangerRed,
                        icon = Icons.Default.Warning,
                        iconTint = DangerRed,
                        iconBgColor = DangerRed.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Weekly Activity Chart
        item {
            WeeklyActivityChart(modifier = Modifier.fillMaxWidth())
        }

        // Urgent Overdues Section
        if (overdueLoans.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationImportant, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Urgent Overdues",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${overdueLoans.size} Overdue",
                            color = DangerRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(overdueLoans) { loan ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = loan.bookTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Borrower: ${loan.userName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Fine accumulated: $${String.format("%.2f", loan.fineAmount.coerceAtLeast(10.0))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerRed
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onRemindMember(loan.userId, loan.bookTitle) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remind", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onProcessReturn(loan.borrowId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Check-In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Section
        item {
            Text(
                text = "Recent Lending Activities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(borrowRecords.take(5)) { record ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when (record.status) {
                                        "Returned" -> SuccessGreen.copy(alpha = 0.15f)
                                        "Overdue" -> DangerRed.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (record.status) {
                                    "Returned" -> Icons.Default.CheckCircle
                                    "Overdue" -> Icons.Default.Warning
                                    else -> Icons.Default.AutoStories
                                },
                                contentDescription = null,
                                tint = when (record.status) {
                                    "Returned" -> SuccessGreen
                                    "Overdue" -> DangerRed
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = record.bookTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${record.userName} • ID: ${record.userId}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                when (record.status) {
                                    "Returned" -> SuccessGreen.copy(alpha = 0.15f)
                                    "Overdue" -> DangerRed.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = record.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (record.status) {
                                "Returned" -> SuccessGreen
                                "Overdue" -> DangerRed
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
