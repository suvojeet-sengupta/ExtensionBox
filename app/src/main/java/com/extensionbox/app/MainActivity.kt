package com.extensionbox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.extensionbox.app.ui.screens.AboutScreen
import com.extensionbox.app.ui.screens.DashboardScreen
import com.extensionbox.app.ui.screens.ExtensionsScreen
import com.extensionbox.app.ui.screens.SettingsScreen
import com.extensionbox.app.ui.theme.ExtensionBoxTheme

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestPerms()

        setContent {
            ExtensionBoxTheme {
                MainApp()
            }
        }
    }

    private fun requestPerms() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object Extensions : Screen("extensions", "Extensions", Icons.Filled.Extension)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object About : Screen("about", "About", Icons.Filled.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Dashboard, Screen.Extensions, Screen.Settings, Screen.About)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = screens.find { it.route == currentRoute } ?: Screen.Dashboard

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = currentScreen.title,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 2 }) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it / 2 }) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it / 2 }) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it / 2 }) }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Extensions.route) { ExtensionsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.About.route) { AboutScreen() }
        }
    }
}
