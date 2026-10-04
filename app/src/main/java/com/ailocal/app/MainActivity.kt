package com.ailocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.ailocal.app.ui.NavDestination
import com.ailocal.app.ui.actions.ActionsScreen
import com.ailocal.app.ui.chat.ChatScreen
import com.ailocal.app.ui.chat.ChatViewModel
import com.ailocal.app.ui.conversations.ConversationsScreen
import com.ailocal.app.ui.models.ModelsScreen
import com.ailocal.app.ui.settings.SettingsScreen
import com.ailocal.app.ui.theme.AiLocalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The whole app is Hebrew-first; force RTL layout direction
            // regardless of system locale so the UI always matches the
            // product spec, while individual strings still localize.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AiLocalTheme {
                    AiLocalApp()
                }
            }
        }
    }
}

private data class BottomNavItem(
    val destination: NavDestination,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun AiLocalApp() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val items = listOf(
        BottomNavItem(NavDestination.Chat, R.string.nav_chat, Icons.Filled.Chat),
        BottomNavItem(NavDestination.Conversations, R.string.nav_conversations, Icons.Filled.Forum),
        BottomNavItem(NavDestination.Actions, R.string.nav_actions, Icons.Filled.Build),
        BottomNavItem(NavDestination.Models, R.string.nav_models, Icons.Filled.Memory),
        BottomNavItem(NavDestination.Settings, R.string.nav_settings, Icons.Filled.Settings)
    )

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = stringResourceCompat(item.labelRes)) },
                            label = { Text(stringResourceCompat(item.labelRes)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = NavDestination.Chat.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(NavDestination.Chat.route) {
                    ChatScreen(viewModel = chatViewModel)
                }
                composable(NavDestination.Conversations.route) {
                    ConversationsScreen(
                        chatManager = chatViewModel.chatManager,
                        onOpenConversation = { id ->
                            chatViewModel.selectConversation(id)
                            navController.navigate(NavDestination.Chat.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(NavDestination.Actions.route) { ActionsScreen() }
                composable(NavDestination.Models.route) {
                    ModelsScreen(modelManager = chatViewModel.modelManager)
                }
                composable(NavDestination.Settings.route) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun stringResourceCompat(resId: Int): String = androidx.compose.ui.res.stringResource(id = resId)
