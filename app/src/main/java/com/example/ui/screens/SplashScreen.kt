package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.AuthUserInfo
import com.example.ui.viewmodel.AuthenticationViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthenticationViewModel = viewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: (AuthUserInfo) -> Unit
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "emblemPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val triggerAuthCheck: () -> Unit = {
        if (!hasNavigated) {
            authViewModel.attemptAutoSignIn(
                context = context,
                onAuthSuccess = { user ->
                    if (!hasNavigated) {
                        hasNavigated = true
                        onNavigateToMain(user)
                    }
                },
                onUnauthenticated = {
                    if (!hasNavigated) {
                        hasNavigated = true
                        onNavigateToLogin()
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(900)
        triggerAuthCheck()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(scale)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "LibEase Emblem",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "LibEase",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Campus Library & Cloud Resource System",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SplashFeaturePill(icon = Icons.Default.AutoStories, label = "Catalog")
                    SplashFeaturePill(icon = Icons.Default.CloudDone, label = "Cloud Sync")
                    SplashFeaturePill(icon = Icons.Default.Shield, label = "Role Portals")
                }

                Spacer(modifier = Modifier.height(48.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Checking Google Sign-In session…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = triggerAuthCheck,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("splash_get_started_button")
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashFeaturePill(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
