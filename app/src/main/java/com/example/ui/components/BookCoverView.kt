package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book

private val gradientPalettes = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)), // Deep Teal
    listOf(Color(0xFF141E30), Color(0xFF243B55)),                   // Royal Indigo
    listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),                   // Electric Blue
    listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)), // Sunset Magic
    listOf(Color(0xFF000428), Color(0xFF004E92)),                   // Cosmic Space
    listOf(Color(0xFF1D2671), Color(0xFFC33764)),                   // Tech Nebula
    listOf(Color(0xFF0B486B), Color(0xFFF56217)),                   // Contrast Glow
    listOf(Color(0xFF16222A), Color(0xFF3A6073))                    // Dark Slate
)

@Composable
fun BookCoverView(
    book: Book,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true
) {
    val palette = gradientPalettes[book.coverGradientIndex.coerceIn(0, gradientPalettes.lastIndex)]
    val brush = Brush.verticalGradient(palette)

    val categoryIcon = when (book.category.lowercase()) {
        "programming" -> Icons.Default.Code
        "science" -> Icons.Default.Science
        "ai & data" -> Icons.Default.Psychology
        "business" -> Icons.Default.TrendingUp
        "history" -> Icons.Default.Public
        else -> Icons.Default.MenuBook
    }

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
    ) {
        // Spine left highlight line
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxSize()
                .padding(start = 5.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category Icon & Tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Title & Author snippet
            if (showDetails) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = book.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Publisher & ISBN badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.category.uppercase(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
