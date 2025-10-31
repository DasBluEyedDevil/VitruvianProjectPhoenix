package com.example.vitruvianredux.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.presentation.screen.*
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.ThemeMode

/**
 * Main navigation graph for the app.
 * Defines all routes and their composable destinations.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    exerciseRepository: ExerciseRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavigationRoutes.Home.route,
        modifier = modifier
    ) {
        // Home screen - workout type selection
        composable(NavigationRoutes.Home.route) {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }

        // Just Lift screen - quick workout configuration
        composable(NavigationRoutes.JustLift.route) {
            JustLiftScreen(
                navController = navController,
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Single Exercise screen - choose one exercise
        composable(NavigationRoutes.SingleExercise.route) {
            SingleExerciseScreen(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository
            )
        }

        // Daily Routines screen - pre-built routines
        composable(NavigationRoutes.DailyRoutines.route) {
            DailyRoutinesScreen(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository,
                themeMode = themeMode
            )
        }

        // Weekly Programs screen - view and manage programs
        composable(NavigationRoutes.WeeklyPrograms.route) {
            WeeklyProgramsScreen(
                navController = navController,
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Program Builder screen - create/edit weekly program
        composable(
            route = NavigationRoutes.ProgramBuilder.route,
            arguments = listOf(navArgument("programId") { type = NavType.StringType })
        ) { backStackEntry ->
            val programId = backStackEntry.arguments?.getString("programId") ?: "new"
            ProgramBuilderScreen(
                navController = navController,
                viewModel = viewModel,
                programId = programId,
                exerciseRepository = exerciseRepository
            )
        }

        // Analytics screen - history, PRs, trends
        composable(NavigationRoutes.Analytics.route) {
            AnalyticsScreen(
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Settings screen
        composable(NavigationRoutes.Settings.route) {
            SettingsTab(
                weightUnit = viewModel.weightUnit.value,
                autoplayEnabled = viewModel.userPreferences.value.autoplayEnabled,
                onWeightUnitChange = { viewModel.setWeightUnit(it) },
                onAutoplayChange = { viewModel.setAutoplayEnabled(it) },
                onColorSchemeChange = { viewModel.setColorScheme(it) },
                onDeleteAllWorkouts = { viewModel.deleteAllWorkouts() }
            )
        }
    }
}
