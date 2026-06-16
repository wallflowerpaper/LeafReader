package com.leaf.reader.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leaf.reader.library.LibraryScreen
import com.leaf.reader.reader.ReaderScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                onBookOpen = { bookId ->
                    navController.navigate("reader/$bookId")
                }
            )
        }

        composable(
            route     = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            ReaderScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
