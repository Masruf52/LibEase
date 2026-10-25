package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.LibEaseApp
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.AuthenticationViewModel
import com.example.ui.viewmodel.LibraryViewModel

/**
 * Root navigation graph managing top-level application destinations:
 * - Splash: Animated branding & authentication status check
 * - Login: Firebase email/password authentication & member registration
 * - Main: The core LMS experience with role-based portals (Admin & Student)
 */
@Composable
fun RootNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    libraryViewModel: LibraryViewModel = viewModel(),
    authViewModel: AuthenticationViewModel = viewModel(),
    startDestination: String = NavRoutes.SPLASH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        // Splash Destination
        composable(
            route = NavRoutes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(NavRoutes.MAIN) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Login / Register Destination
        composable(
            route = NavRoutes.LOGIN,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { authUser ->
                    libraryViewModel.handleAuthenticatedUser(authUser, authViewModel.uiState.value.selectedRole)
                    navController.navigate(NavRoutes.MAIN) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                },
                onDismissOrContinueGuest = {
                    navController.navigate(NavRoutes.MAIN) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Main LMS Experience (Admin Dashboard & Student Portal)
        composable(
            route = NavRoutes.MAIN,
            enterTransition = { fadeIn(animationSpec = tween(350)) },
            exitTransition = { fadeOut(animationSpec = tween(350)) }
        ) {
            LibEaseApp(
                viewModel = libraryViewModel,
                authViewModel = authViewModel,
                onNavigateToAuth = {
                    navController.navigate(NavRoutes.LOGIN)
                }
            )
        }
    }
}
