package com.mailsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mailsystem.ui.screen.*
import com.mailsystem.ui.theme.MailSystemTheme
import com.mailsystem.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MailSystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MailSystemApp()
                }
            }
        }
    }
}

@Composable
fun MailSystemApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { _ ->
                    navController.navigate("inbox") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onNavigateToAppeal = {
                    navController.navigate("appeal")
                },
                viewModel = authViewModel
            )
        }
        
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = authViewModel
            )
        }
        
        composable("appeal") {
            AppealScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = authViewModel
            )
        }
        
        composable("inbox") {
            InboxScreen(
                onMailClick = { filename ->
                    navController.navigate("mail_detail/$filename")
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToAdmin = {
                    navController.navigate("admin")
                },
                onCompose = { draftFilename ->
                    if (draftFilename != null) {
                        navController.navigate("compose?draft=$draftFilename")
                    } else {
                        navController.navigate("compose")
                    }
                },
                authViewModel = authViewModel
            )
        }
        
        composable(
            route = "compose?draft={draft}",
            arguments = listOf(navArgument("draft") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val draftFilename = backStackEntry.arguments?.getString("draft")
            val mailViewModel: com.mailsystem.ui.viewmodel.MailViewModel = viewModel()
            
            // State for draft loading
            var initialTo by remember { mutableStateOf("") }
            var initialSubject by remember { mutableStateOf("") }
            var initialContent by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(draftFilename != null) }

            LaunchedEffect(draftFilename) {
                if (draftFilename != null) {
                    mailViewModel.readDraft(draftFilename) { to, subject, body ->
                        initialTo = to
                        initialSubject = subject
                        initialContent = body
                        isLoading = false
                    }
                }
            }

            if (isLoading) {
                // Show loading indicator or just empty screen while loading
                Surface(modifier = Modifier.fillMaxSize()) {
                    // You might want a loading spinner here
                }
            } else {
                ComposeScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onSent = {
                        navController.popBackStack()
                    },
                    initialTo = initialTo,
                    initialSubject = initialSubject,
                    initialContent = initialContent,
                    draftFilename = draftFilename,
                    mailViewModel = mailViewModel
                )
            }
        }

        composable("profile") {
            ProfileScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = "mail_detail/{filename}",
            arguments = listOf(navArgument("filename") { type = NavType.StringType })
        ) { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: ""
            MailDetailScreen(
                filename = filename,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("admin") {
            AdminScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
