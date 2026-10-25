package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.LibEaseApp
import com.example.ui.screens.AddBookScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthenticationViewModel
import com.example.ui.viewmodel.LibraryViewModel

/**
 * Root navigation graph managing top-level application destinations with strict
 * Google Sign-In authentication gating before composing Firestore-backed feature screens.
 */
@Composable
fun RootNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    libraryViewModel: LibraryViewModel = viewModel(),
    authViewModel: AuthenticationViewModel = viewModel(),
    startDestination: String = NavRoutes.SPLASH
) {
    val currentAuthUser by authViewModel.currentAuthUser.collectAsStateWithLifecycle()
    val settings by libraryViewModel.settings.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        // Splash Destination: Silent Credential Manager check -> Login or Main
        composable(
            route = NavRoutes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            MyApplicationTheme(darkTheme = settings.isDarkMode) {
                SplashScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToMain = { authUser ->
                        libraryViewModel.bindAuthenticatedUser(
                            authUser,
                            authViewModel.uiState.value.selectedRole
                        )
                        navController.navigate(NavRoutes.MAIN) {
                            popUpTo(NavRoutes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Google Sign-In & Role Selection Destination
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
            MyApplicationTheme(darkTheme = settings.isDarkMode) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = { authUser ->
                        libraryViewModel.bindAuthenticatedUser(
                            authUser,
                            authViewModel.uiState.value.selectedRole
                        )
                        navController.navigate(NavRoutes.MAIN) {
                            popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Main LMS Experience (Strictly Auth-Gated)
        composable(
            route = NavRoutes.MAIN,
            enterTransition = { fadeIn(animationSpec = tween(350)) },
            exitTransition = { fadeOut(animationSpec = tween(350)) }
        ) {
            if (currentAuthUser == null) {
                MyApplicationTheme(darkTheme = settings.isDarkMode) {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = { authUser ->
                            libraryViewModel.bindAuthenticatedUser(
                                authUser,
                                authViewModel.uiState.value.selectedRole
                            )
                        }
                    )
                }
            } else {
                LibEaseApp(
                    viewModel = libraryViewModel,
                    authViewModel = authViewModel,
                    onSignOutComplete = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.MAIN) { inclusive = true }
                        }
                    },
                    onNavigateToAddBook = {
                        navController.navigate(NavRoutes.ADD_BOOK)
                    }
                )
            }
        }

        // Add / Edit Book Screen Destination (Strictly Auth-Gated)
        composable(
            route = NavRoutes.ADD_BOOK,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            MyApplicationTheme(darkTheme = settings.isDarkMode) {
                if (currentAuthUser == null) {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = { authUser ->
                            libraryViewModel.bindAuthenticatedUser(
                                authUser,
                                authViewModel.uiState.value.selectedRole
                            )
                        }
                    )
                } else {
                    AddBookScreen(
                        viewModel = libraryViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
